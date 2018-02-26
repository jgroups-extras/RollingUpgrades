package org.jgroups.relay_server;

import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bela Ban
 * @since x.y
 */
public class ChatService extends org.jgroups.relay_server.ChatGrpc.ChatImplBase {
    protected List<StreamObserver<org.jgroups.relay_server.ChatOuter.ChatMessage>> observers=new ArrayList<>();

    @Override
    public StreamObserver<org.jgroups.relay_server.ChatOuter.ChatMessage> post(StreamObserver<org.jgroups.relay_server.ChatOuter.ChatMessage> responseObserver) {
        observers.add(responseObserver);

        return new StreamObserver<org.jgroups.relay_server.ChatOuter.ChatMessage>() {
            public void onNext(org.jgroups.relay_server.ChatOuter.ChatMessage value) {
                postToAll(value);
            }

            public void onError(Throwable t) {

            }

            public void onCompleted() {
                for(StreamObserver<org.jgroups.relay_server.ChatOuter.ChatMessage> obs: observers)
                    obs.onCompleted();
                observers.remove(this);
            }
        };
    }

    protected void postToAll(org.jgroups.relay_server.ChatOuter.ChatMessage msg) {
        System.out.printf("posting message %s to %d observers\n", msg.getMsg(), observers.size());
        for(StreamObserver<org.jgroups.relay_server.ChatOuter.ChatMessage> obs: observers) {
            try {
                obs.onNext(msg);
            }
            catch(Throwable t) {
                System.out.printf("exception %s: removing observer\n", t);
                obs.onError(t);
                observers.remove(obs);
            }
        }
    }

}
