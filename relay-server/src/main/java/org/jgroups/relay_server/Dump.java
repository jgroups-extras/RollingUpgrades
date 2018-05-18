package org.jgroups.relay_server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

/** Dumps the contents of the RelayServer's registered clusters and members
 * @author Bela Ban
 * @since 1.0.0
 */
public class Dump {
    protected ManagedChannel                            channel;
    protected RelayServiceGrpc.RelayServiceBlockingStub blocking_stub;


    protected void start(String host, int port) throws InterruptedException {
        channel=ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
        blocking_stub=RelayServiceGrpc.newBlockingStub(channel);
        DumpResponse response=blocking_stub.dump(Void.newBuilder().build());
        System.out.printf("%s\n", response.getDump());
    }


    protected void stop() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws InterruptedException {
        String host="localhost";
        int port=50051;
        Dump client=new Dump();
        for(int i=0; i < args.length; i++) {
            if(args[i].equals("-host")) {
                host=args[++i];
                continue;
            }
            if(args[i].equals("-port")) {
                port=Integer.valueOf(args[++i]);
                continue;
            }
            System.out.println("Dump [-host host] -port port] [-h]");
            return;
        }
        client.start(host, port);
        client.stop();
    }
}
