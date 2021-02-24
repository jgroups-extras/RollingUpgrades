package org.jgroups.upgrade_server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.jgroups.common.Utils;

import java.io.InputStream;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class UpgradeServer {
    protected Server server;

    public void start(int port, String cert, String private_key) throws Exception {
        ServerBuilder<?> srv_builder=ServerBuilder.forPort(port).addService(new UpgradeService());
        String encryption="plaintext - no encryption";
        if(cert != null || private_key != null) {
            if(cert == null || private_key == null)
                throw new IllegalArgumentException(String.format("both cert (%s) and private key (%s) have to be given " +
                                                                   "to enable TLS", cert, private_key));
            InputStream cert_stream=Utils.getFile(cert), pkey_stream=Utils.getFile(private_key);
            srv_builder.useTransportSecurity(cert_stream, pkey_stream);
            encryption="encrypted";
        }
        server=srv_builder.build().start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            System.out.println("server was shut down");
        }));
        System.out.printf("-- UpgradeServer listening on %d (%s)\n", server.getPort(), encryption);
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
        String cert=null, private_key=null;
        UpgradeServer srv=new UpgradeServer();
        for(int i=0; i < args.length; i++) {
            if(args[i].equals("-p") || args[i].equals("-port")) {
                port=Integer.parseInt(args[++i]);
                continue;
            }
            if(args[i].equals("-cert")) {
                cert=args[++i];
                continue;
            }
            if(args[i].equals("-key")) {
                private_key=args[++i];
                continue;
            }
            help();
            return;
        }
        srv.start(port, cert, private_key);
        srv.blockUntilShutdown();
    }

    protected static void help() {
        System.out.println("UpgradeServer [-port <server port>] [-cert cert-file] [-key private-key-file]\n" +
                             "(the certificate and public/private key can be generated with bin/genkey.sh)");
    }
}
