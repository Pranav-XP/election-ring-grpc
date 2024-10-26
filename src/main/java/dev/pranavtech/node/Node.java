package dev.pranavtech.node;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.Scanner;

public class Node extends NodeServiceGrpc.NodeServiceImplBase {
    private final int port;
    private final int nextPort;
    private Server server;
    private final int id;
    private int leaderId;
    private final int REGISTER_PORT= 5000;
    private final int ELECTION_MESSAGE_CODE = 1;
    private final int LEADER_MESSAGE_CODE = 2;
    private final int PING_MESSAGE_CODE = 3;

    //Constructor for normal node
    public Node(int id, int nextPort, int port) throws IOException {
        this.id = id;
        this.nextPort = nextPort;
        this.port = port;
    }

    //Constructor for register node
    public Node(int nextPort, int id) {
        this.port = REGISTER_PORT;
        this.nextPort = nextPort;
        this.id = id;
    }

    public void startServer() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(this)
                .build()
                .start();
    }

    // Send election or leader message to the neighboring nodes
    public void sendMessage(int candidateId, int originId, int messageType){
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost",nextPort)
                .usePlaintext()
                .build();

        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        NodeProto.MessageRequest request = NodeProto.MessageRequest.newBuilder()
                .setMessage(candidateId)
                .setOrigin(originId)
                .build();

        // 1 is election message. 2 is leader message. Otherwise error.
        if(messageType == ELECTION_MESSAGE_CODE){
            NodeProto.MessageResponse response = stub.sendElection(request);
            System.out.println("NODE "+this.id+": Election message sent to "+response.getAck());
            channel.shutdown();
        }else if(messageType == LEADER_MESSAGE_CODE){
            NodeProto.MessageResponse response = stub.sendLeader(request);
            System.out.println("NODE "+this.id+": Leader message sent to "+response.getAck());
            channel.shutdown();
        }else if(messageType == PING_MESSAGE_CODE){
            NodeProto.MessageResponse response = stub.ping(request);
            System.out.println("NODE "+this.id+": Connected to "+response.getAck());
            channel.shutdown();

        }else{
            System.out.println("NODE "+this.id+": Unknown message type with code "+messageType);
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

         if(originId == this.id) {
             // Message has completed round. Begin informing leader id.
             //TODO: FIX THE LOOP HERE
             this.leaderId = candidateId;
             System.out.println("NODE "+this.id+": Round complete. Sending leader . . .");
             sendMessage(candidateId,this.id,LEADER_MESSAGE_CODE);
         }else sendMessage(Math.max(this.id, candidateId), originId, ELECTION_MESSAGE_CODE);

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
            System.out.println("NODE "+this.id+": Leader is NODE "+leaderId);
            System.out.println("ELECTION COMPLETE");
        }else{
            //Forward leader ID
            this.leaderId = leaderId;
            System.out.println("NODE "+this.id+": Leader is NODE "+leaderId);
            sendMessage(leaderId,originId,LEADER_MESSAGE_CODE);
        }

    }

    @Override
    public void ping(NodeProto.MessageRequest request, StreamObserver<NodeProto.MessageResponse> responseObserver) {
        int id = request.getMessage();
        int originId = request.getOrigin();

        System.out.println("Connected . . .");

        NodeProto.MessageResponse response = NodeProto.MessageResponse.newBuilder()
                .setAck(this.id)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private static void connectWithRetry(Node node, int maxRetries, int retryDelayMs) {
        int retryCount = 0;
        boolean connected = false;

        while (!connected && retryCount < maxRetries) {
            try {
                // Try to establish connection by sending a test message
                node.sendMessage(node.id, node.id, node.PING_MESSAGE_CODE);
                connected = true;
                System.out.println("Successfully connected to next node!");
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

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please enter Node ID:");
        int nodeId = scanner.nextInt();

        System.out.println("Please enter this node's port:");
        int port = scanner.nextInt();

        System.out.println("Please enter next node's port:");
        int nextPort = scanner.nextInt();

        System.out.println("Initializing Node " + nodeId + " ...");

        try {
            // Create and start the node
            Node node = new Node(nodeId, nextPort, port);
            node.startServer();
            System.out.println("Node " + nodeId + " started successfully on port " + port);

            // Try to connect to next node with retry logic
            System.out.println("Attempting to connect to next node on port " + nextPort + "...");
            connectWithRetry(node, 5, 5000); // 5 retries, 5 seconds between retries

            // Keep the node running and wait for commands
            while (true) {
                System.out.println("\nEnter command (1: Start Election, 2: Exit):");
                int command = scanner.nextInt();

                switch (command) {
                    case 1:
                        System.out.println("NODE "+node.id+": Starting election process...");
                        node.sendMessage(node.id, node.id, node.ELECTION_MESSAGE_CODE);
                        break;
                    case 2:
                        System.out.println("Shutting down node...");
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