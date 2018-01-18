package org.jgroups.relay_server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class ChatServer {
    protected Server server;

    public void start(int port) throws IOException {
        server = ServerBuilder.forPort(port)
          .addService(new ChatService())
          .build()
          .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            server.shutdown();
            System.err.println("*** server shut down");
        }));
    }

    public void stop() throws InterruptedException {
        server.awaitTermination();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ChatServer srv=new ChatServer();
        srv.start(50051);
        srv.stop();
    }
}
