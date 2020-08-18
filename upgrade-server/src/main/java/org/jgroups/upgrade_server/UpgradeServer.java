package org.jgroups.upgrade_server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class UpgradeServer {
    protected Server server;

    public void start(int port) throws Exception {
        server = ServerBuilder.forPort(port)
          .addService(new UpgradeService())
          .build()
          .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            System.out.println("server was shut down");
        }));
        System.out.printf("-- UpgradeServer listening on %d\n", server.getPort());
        server.awaitTermination();
    }

    public void stop() throws InterruptedException {
        server.awaitTermination();
    }

    protected void blockUntilShutdown() throws InterruptedException {
        server.awaitTermination();
    }

    public static void main(String[] args) throws Exception {
        int port=50051;
        UpgradeServer srv=new UpgradeServer();
        for(int i=0; i < args.length; i++) {
            if(args[i].equals("-p") || args[i].equals("-port")) {
                port=Integer.parseInt(args[++i]);
                continue;
            }
            help();
            return;
        }
        srv.start(port);
        srv.blockUntilShutdown();
    }

    protected static void help() {
        System.out.println("UpgradeServer [-port <server port>]");
    }
}
