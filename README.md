# Leader Election System using gRPC

A distributed leader election system implemented in Java using gRPC for node communication in a ring topology.

## Authors
- Pranav Chand
- Pui Kit Chen
- Aryan Sharma

## Overview
This system implements a distributed leader election algorithm where nodes communicate through gRPC to elect a leader based on the highest node ID. The nodes are arranged in a ring topology, with each node knowing its successor.

## Features
- Ring topology-based node arrangement
- Automatic node registration system
- Leader election based on highest node ID
- Robust connection retry mechanism
- Interactive command interface
- Support for both command-line and interactive initialization

## Prerequisites
- Java 8 or higher
- Maven
- gRPC dependencies (handled by Maven)

## Building the Project
```bash
mvn clean install
```

## Running Nodes

### Starting the Register Node
The register node (Peer Register) must be started first as it coordinates the ring topology.

```bash
# Using command line arguments
java -jar leader-election-grpc-1.0-SNAPSHOT.jar 0 5000 true

# Or run without arguments and follow the prompts
java -jar leader-election-grpc-1.0-SNAPSHOT.jar
```

### Starting Regular Nodes
After the register node is running, you can start regular nodes:

```bash
# Using command line arguments
java -jar leader-election-grpc-1.0-SNAPSHOT.jar <nodeId> <port> false

# Example
java -jar leader-election-grpc-1.0-SNAPSHOT.jar 1 5001 false
```

Note: Port 5000 is reserved for the register node.

## Command Interface
Once a node is running, you can use the following commands:
1. Start Election: Initiates the leader election process
2. Exit: Shuts down the node

## System Architecture

### Node Types
1. Register Node (Peer Register)
    - Runs on port 5000
    - Manages node registration
    - Maintains ring topology

2. Regular Nodes
    - Can initiate elections
    - Participate in leader election
    - Forward messages to next node in ring

### Message Types
- ELECTION_MESSAGE_CODE (1): Used during election process
- LEADER_MESSAGE_CODE (2): Announces elected leader
- PING_MESSAGE_CODE (3): Updates node connections
- REGISTER_MESSAGE_CODE (4): Handles node registration

## Example Setup
1. Start Register Node:
```bash
java -jar leader-election-grpc-1.0-SNAPSHOT.jar 0 5000 true
```

2. Start Node 1:
```bash
java -jar leader-election-grpc-1.0-SNAPSHOT.jar 1 5001 false
```

3. Start Node 2:
```bash
java -jar leader-election-grpc-1.0-SNAPSHOT.jar 2 5002 false
```

4. From any node, enter command "1" to start election

## Error Handling
- The system includes automatic retry mechanisms for node connections
- Invalid ports (especially 5000 for regular nodes) are prevented
- Proper error messages for invalid commands and configurations

## Implementation Details
- Uses gRPC for inter-node communication
- Implements a ring topology where each node only knows its next node
- Leader election algorithm picks the highest node ID as leader
- Messages are passed around the ring until they complete a full circle

## Troubleshooting
1. If a node fails to connect, ensure the register node is running
2. Check if the port is available
3. Verify that no other node is using port 5000
4. Ensure all nodes are on the same network/localhost

## Contributing
Feel free to submit issues and enhancement requests.