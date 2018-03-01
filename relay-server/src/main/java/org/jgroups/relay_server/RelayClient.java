package org.jgroups.relay_server;

import com.google.protobuf.ByteString;
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
    protected ManagedChannel                    channel;
    protected RelayServiceGrpc.RelayServiceStub asyncStub;
    protected final String                      local_addr;
    protected static final String               CLUSTER="grpc";


    public RelayClient(String addr) {
        local_addr=addr;
    }


    protected void start(int port) throws InterruptedException {
        channel=ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(true).build();
        asyncStub=org.jgroups.relay_server.RelayServiceGrpc.newStub(channel);

        StreamObserver<Message> send_stream=asyncStub.relay(new StreamObserver<Message>() {
            public void onNext(Message msg) {
                System.out.printf("received message from %s: %s\n", msg.getSender().getAddress(), new String(msg.getPayload().toByteArray()));
            }

            public void onError(Throwable t) {
                System.out.printf("exception from server: %s\n", t);
            }

            public void onCompleted() {
                System.out.println("server is done");
            }
        });

        BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            try {
                System.out.print("> "); System.out.flush();
                String line=in.readLine().toLowerCase();
                if(line.startsWith("quit") || line.startsWith("exit"))
                    break;

                Message msg=Message.newBuilder()
                  .setClusterName(CLUSTER)
                  .setDestination(Address.newBuilder())
                  .setSender(Address.newBuilder().setAddress(local_addr))
                  .setPayload(ByteString.copyFrom(line.getBytes()))
                  .build();
                send_stream.onNext(msg);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }

        send_stream.onCompleted();
    }

    protected void stop() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws InterruptedException {
        RelayClient client=new RelayClient(args[0]);
        client.start(50051);
        client.stop();
    }
}
