package org.jgroups.relay_server;

import io.grpc.stub.StreamObserver;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class RelayService extends RelayServiceGrpc.RelayServiceImplBase {
    protected Collection<StreamObserver<Message>> observers=new ConcurrentLinkedQueue<>();


    public StreamObserver<Message> relay(StreamObserver<Message> responseObserver) {
        observers.add(responseObserver);
        System.out.printf("-- relay(): %d observers\n", observers.size());

        return new StreamObserver<Message>() {
            public void onNext(Message msg) {
                postToAll(msg);
            }

            public void onError(Throwable t) {
                System.out.printf("exception: %s. Removing observer\n", t);
                observers.remove(responseObserver);
            }

            public void onCompleted() {
                System.out.println("server is done. Removing observer");
                observers.remove(responseObserver);
            }
        };

    }

    protected void postToAll(Message msg) {
        System.out.printf("-- relaying %s to %d observers\n", new String(msg.getPayload().toByteArray()), observers.size());
        for(StreamObserver<Message> obs: observers) {
            try {
                obs.onNext(msg);
            }
            catch(Throwable t) {
                System.out.printf("exception relaying message (removing observer): %s\n", t);
                observers.remove(obs);
            }
        }
    }

}
