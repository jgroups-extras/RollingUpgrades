package org.jgroups.relay_server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class RelayServer {
    protected Server server;

    public void start(int port) throws Exception {
        server = ServerBuilder.forPort(port)
          .addService(new RelayService())
          .build()
          .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            System.out.println("server was shut down");
        }));
        System.out.printf("-- RelayServer listening on %d\n", server.getPort());
        server.awaitTermination();
    }

    public void stop() throws InterruptedException {
        server.awaitTermination();
    }

    public static void main(String[] args) throws Exception {
        RelayServer srv=new RelayServer();
        srv.start(50051);
        srv.stop();
    }
}
