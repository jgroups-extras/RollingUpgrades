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
    protected RelayServiceGrpc.RelayServiceBlockingStub blocking_stub;
    protected final Address                     local_addr;
    protected View                              view; // the current view
    protected StreamObserver<JoinRequest>       join_req;
    protected static final String               CLUSTER="grpc";


    public RelayClient(String addr) {
        local_addr=Address.newBuilder().setAddress(addr).build();
    }


    protected void start(int port) throws InterruptedException {
        channel=ManagedChannelBuilder.forAddress("localhost", port).usePlaintext(true).build();
        asyncStub=RelayServiceGrpc.newStub(channel);
        blocking_stub=RelayServiceGrpc.newBlockingStub(channel);

        join_req=asyncStub.join(new StreamObserver<View>() {
            public void onNext(View v) {
                System.out.printf("-- received view %s\n", Utils.print(v));
                RelayClient.this.view=v;
            }

            public void onError(Throwable t) {

            }

            public void onCompleted() {

            }
        });
        JoinRequest req=JoinRequest.newBuilder().setAddress(local_addr).setClusterName(CLUSTER).build();
        join_req.onNext(req);


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
                if(line.startsWith("quit") || line.startsWith("exit")) {
                    blocking_stub.leave(LeaveRequest.newBuilder().setClusterName(CLUSTER).setLeaver(local_addr).build());
                    System.out.println("Client left gracefully");
                    break;
                }

                Message msg=Message.newBuilder()
                  .setClusterName(CLUSTER)
                  .setSender(local_addr)
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
