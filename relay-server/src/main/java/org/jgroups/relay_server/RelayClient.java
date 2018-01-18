package org.jgroups.relay_server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * @author Bela Ban
 * @since x.y
 */
public class RelayClient {
    private ManagedChannel channel;
    private org.jgroups.relay_server.ChatGrpc.ChatStub asyncStub;

    protected void start(int port, String address) throws InterruptedException {
        channel=ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(true).build();
        asyncStub=org.jgroups.relay_server.ChatGrpc.newStub(channel);

        StreamObserver<org.jgroups.relay_server.ChatOuter.Message> reqs=asyncStub.post(new StreamObserver<org.jgroups.relay_server.ChatOuter.Message>() {
            public void onNext(org.jgroups.relay_server.ChatOuter.Message value) {
                System.out.printf("%s: %s\n", value.getSender(), value.getMsg());
            }

            public void onError(Throwable t) {

            }

            public void onCompleted() {
                System.out.printf("client is done\n");
            }
        });

        BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            try {
                System.out.print("> "); System.out.flush();
                String line=in.readLine().toLowerCase();
                if(line.startsWith("quit") || line.startsWith("exit")) {
                    break;
                }
                org.jgroups.relay_server.ChatOuter.Message msg=org.jgroups.relay_server.ChatOuter.Message.newBuilder()
                  .setMsg(line).setSender(address).build();
                reqs.onNext(msg);
            }
            catch(Exception e) {
            }
        }

        reqs.onCompleted();
    }

    protected void stop() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws InterruptedException {
        RelayClient client=new RelayClient();
        client.start(50051, args[0]);
        client.stop();
    }
}
