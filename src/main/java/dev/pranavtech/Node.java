package dev.pranavtech;

import io.grpc.Server;

public class Node {
    private final int port;
    private final int nextPort;
    private Server server;
    private final int id;
    private final int REGISTER_PORT= 5000;

    //Constructor for normal node
    public Node(int id, int nextPort, int port) {
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

    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}