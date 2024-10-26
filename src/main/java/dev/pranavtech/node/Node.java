package dev.pranavtech.node;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Node extends NodeServiceGrpc.NodeServiceImplBase {
    private final int port;
    private int nextPort;
    private Server server;
    private final int id;
    private int leaderId;
    private final boolean isRegister;
    private List<NodeInfo> registeredNodes = new ArrayList<>();

    // Inner class to store node information
    private static class NodeInfo {
        int nodeId;
        int port;
        int nextPort;

        NodeInfo(int nodeId, int port) {
            this.nodeId = nodeId;
            this.port = port;
            this.nextPort = -1; // Default until set by the register node
        }
    }

    //Message codes
    private final int REGISTER_PORT= 5000;
    private final int ELECTION_MESSAGE_CODE = 1;
    private final int LEADER_MESSAGE_CODE = 2;
    private final int PING_MESSAGE_CODE = 3;
    private final int REGISTER_MESSAGE_CODE = 4;

    //Constructor for normal node
    public Node(int id, int port,boolean isRegister) throws IOException {
        this.id = id;
        this.port = port;
        this.isRegister = isRegister;
        this.leaderId = 0;

        if (isRegister) {
            System.out.println("Peer register running . . .");
        }
    }

    public void startServer() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(this)
                .build()
                .start();
    }

    public void stopServer() {
        if (server != null) {
            server.shutdown();
        }
    }

    // Send election or leader message to the neighboring nodes
    public void sendMessage(int message, int originId,int to,int messageType){
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",to)
                .usePlaintext()
                .build();

        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        NodeProto.MessageRequest request = NodeProto.MessageRequest.newBuilder()
                .setMessage(message)
                .setOrigin(originId)
                .build();
        // 1 is election message. 2 is leader message. Otherwise error.
        if(messageType == ELECTION_MESSAGE_CODE){
            NodeProto.MessageResponse response = stub.sendElection(request);
            System.out.println(this + ": Election message sent to "+response.getAck());
            channel.shutdown();
        }else if(messageType == LEADER_MESSAGE_CODE){
            NodeProto.MessageResponse response = stub.sendLeader(request);
            System.out.println(this+": Leader message sent to "+response.getAck());
            channel.shutdown();
        }else if(messageType == PING_MESSAGE_CODE){
            NodeProto.MessageResponse response = stub.setNext(request);
            System.out.println(this+": Adjusted Node "+response.getAck());
            channel.shutdown();
        } else if (messageType == REGISTER_MESSAGE_CODE) {
            NodeProto.MessageResponse response = stub.register(request);
            this.nextPort = response.getAck();
            System.out.println(this+": Registered successfully.");
            channel.shutdown();
        } else{
            System.out.println(this+": Unknown message type with code "+messageType);
        }
    }

    //RPC to register node into ring topology
    @Override
    public void register(NodeProto.MessageRequest request, StreamObserver<NodeProto.MessageResponse> responseObserver) {
        if(!isRegister){
            System.err.println(this+": ERROR Not a Peer Register");
        }else{
            int newId = request.getOrigin();
            int newPort = request.getMessage();
            NodeInfo newNode = new NodeInfo(newId, newPort);

            if(registeredNodes.isEmpty()){
                NodeInfo registerNode = new NodeInfo(this.id, REGISTER_PORT);
                registerNode.nextPort = newPort;
                this.nextPort = newPort;

                newNode.nextPort = REGISTER_PORT;
                registeredNodes.add(registerNode);
                registeredNodes.add(newNode);
                NodeProto.MessageResponse response = NodeProto.MessageResponse.newBuilder()
                        .setAck(newNode.nextPort)
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }else{
                NodeInfo lastNode = registeredNodes.getLast();
                NodeInfo firstNode = registeredNodes.getFirst();
                lastNode.nextPort = newNode.port;
                newNode.nextPort = firstNode.port;
                registeredNodes.add(newNode);
                sendMessage(newPort,REGISTER_PORT, lastNode.port, PING_MESSAGE_CODE);
                NodeProto.MessageResponse response = NodeProto.MessageResponse.newBuilder()
                        .setAck(newNode.nextPort)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        }
        //Show updated nodes
        System.out.print(this+": Registered Nodes [");
        for (NodeInfo node : registeredNodes) {
            System.out.print(node.nodeId+" ");
        }
        System.out.println("]");
    }

    // Adjust the nextPort of a node
    @Override
    public void setNext(NodeProto.MessageRequest request, StreamObserver<NodeProto.MessageResponse> responseObserver) {
        if(request.getOrigin() != REGISTER_PORT){
            System.err.println(this+": ERROR Not received from Peer Register");
            NodeProto.MessageResponse response = NodeProto.MessageResponse.newBuilder()
                    .setAck(-1)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }else{
            this.nextPort = request.getMessage();
            System.out.println(this+": Next port assigned as "+this.nextPort);
            NodeProto.MessageResponse response = NodeProto.MessageResponse.newBuilder()
                    .setAck(this.id)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    //Handle the election message by comparing ID and sending the larger.
    @Override
    public void sendElection(NodeProto.MessageRequest request, StreamObserver<NodeProto.MessageResponse> responseObserver) {
        int candidateId = request.getMessage();
        int originId = request.getOrigin();

        NodeProto.MessageResponse response = NodeProto.MessageResponse.newBuilder()
                .setAck(this.id)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        //If Peer register then do not participate
        if(isRegister){
            System.out.println(this+": Passing message");
            sendMessage(candidateId,originId,this.nextPort,ELECTION_MESSAGE_CODE);
            return;
        }

         if(originId == this.id) {
             // Message has completed round. Begin informing leader id.
             this.leaderId = candidateId;
             System.out.println(this+": Round complete. Sending leader . . .");
             sendMessage(candidateId,this.id,this.nextPort,LEADER_MESSAGE_CODE);
         }else sendMessage(Math.max(this.id, candidateId), originId,this.nextPort,ELECTION_MESSAGE_CODE);

    }

    // Handle the leader message by assigning leader ID
    @Override
    public void sendLeader(NodeProto.MessageRequest request, StreamObserver<NodeProto.MessageResponse> responseObserver) {
        int leaderId = request.getMessage();
        int originId = request.getOrigin();

        NodeProto.MessageResponse response = NodeProto.MessageResponse.newBuilder()
                .setAck(this.id)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();

        if(this.id == originId){
            // Round complete
            System.out.println(this+": Leader is NODE "+leaderId);
            System.out.println("ELECTION COMPLETE");
        }else{
            //Forward leader ID
            this.leaderId = leaderId;
            System.out.println("NODE "+this.id+": Leader is NODE "+leaderId);
            sendMessage(leaderId,originId,this.nextPort,LEADER_MESSAGE_CODE);
        }

    }

    private static void connectWithRetry(Node node, int maxRetries, int retryDelayMs) {
        int retryCount = 0;
        boolean connected = false;

        while (!connected && retryCount < maxRetries) {
            try {
                // Try to   establish connection by sending a test message
                node.sendMessage(node.port, node.id,node.REGISTER_PORT, node.REGISTER_MESSAGE_CODE);
                connected = true;
            } catch (Exception e) {
                retryCount++;
                System.out.println("Attempt " + retryCount + " of " + maxRetries +
                        " failed to connect to next node. Retrying in " +
                        (retryDelayMs/1000) + " seconds...");
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!connected) {
            System.err.println("Failed to connect to next node after " + maxRetries + " attempts.");
            System.err.println("Please ensure the next node is running and try again.");
            System.exit(1);
        }
    }

    @Override
    public String toString() {
        return "NODE " + this.id;
    }

    public static void main(String[] args) {
        int nodeId, port;
        boolean isRegisterNode = false;

        if (args.length == 3) {
            // Use args directly if provided
            try {
                nodeId = Integer.parseInt(args[0]);
                port = Integer.parseInt(args[1]);
                isRegisterNode = Boolean.parseBoolean(args[2]);

                if (!isRegisterNode && port == 5000) {
                    System.err.println("Error: Port 5000 is reserved. Please choose a different port.");
                    return;
                }

                System.out.println("Using provided arguments: Node ID = " + nodeId + ", Port = " + port +
                        ", Register Node = " + isRegisterNode);
            } catch (NumberFormatException e) {
                System.err.println("Error: Node ID and Port must be integers. 'Register Node' should be true or false.");
                return;
            }
        } else if (args.length > 0) {
            System.err.println("Error: Please provide exactly 3 arguments (Node ID, Port, Register Node), or none to use " +
                    "prompts.");
            return;
        } else {
            // Prompt the user if args are not provided
            Scanner scanner = new Scanner(System.in);

            System.out.println("Is this node the register node? (true/false):");
            isRegisterNode = scanner.nextBoolean();

            if (isRegisterNode) {
                // Automatically set values for register node
                nodeId = 0; // ID for register node
                port = 5000; // Reserved port for register node
                System.out.println("Register Node created with Node ID = " + nodeId + ", Port = " + port);
            }else{
                System.out.println("Please enter Node ID:");
                nodeId = scanner.nextInt();
                System.out.println("Please enter this node's port:");
                port = scanner.nextInt();

                //Port 5000 is reserved for Peer Register
                if (port == 5000) {
                    System.err.println("Error: Port 5000 is reserved. Please choose a different port.");
                    return;
                }
            }
        }

        System.out.println("Initializing Node " + nodeId + " ...");

        try {
            Node node = new Node(nodeId,port, isRegisterNode);
            node.startServer();
            System.out.println("Node " + nodeId + " started successfully on port " + port);

            // If not the register node, attempt to connect to the register node
            if (!isRegisterNode) {
                System.out.println("Attempting to register with the register node...");
                connectWithRetry(node, 5, 5000); // 5 retries, 5 seconds between retries
            }

            // Keep the node running and wait for commands
            Scanner commandScanner = new Scanner(System.in);
            while (true) {
                System.out.println("\nEnter command (1: Start Election, 2: Exit):");
                int command = commandScanner.nextInt();
                switch (command) {
                    case 1:
                        System.out.println("NODE " + nodeId + ": Starting election process...");
                        node.sendMessage(nodeId, nodeId,node.nextPort,node.ELECTION_MESSAGE_CODE);
                        break;
                    case 2:
                        System.out.println("Shutting down node...");
                        node.stopServer();
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Invalid command!");
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start node: " + e.getMessage());
        }
    }
}