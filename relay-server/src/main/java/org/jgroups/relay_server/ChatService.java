package org.jgroups.relay_server;

import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bela Ban
 * @since x.y
 */
public class ChatService extends org.jgroups.relay_server.ChatGrpc.ChatImplBase {
    protected List<StreamObserver<org.jgroups.relay_server.ChatOuter.Message>> observers=new ArrayList<>();

    @Override
    public StreamObserver<org.jgroups.relay_server.ChatOuter.Message> post(StreamObserver<org.jgroups.relay_server.ChatOuter.Message> responseObserver) {
        observers.add(responseObserver);

        return new StreamObserver<org.jgroups.relay_server.ChatOuter.Message>() {
            public void onNext(org.jgroups.relay_server.ChatOuter.Message value) {
                postToAll(value);
            }

            public void onError(Throwable t) {

            }

            public void onCompleted() {
                for(StreamObserver<org.jgroups.relay_server.ChatOuter.Message> obs: observers)
                    obs.onCompleted();
                observers.remove(this);
            }
        };
    }

    protected void postToAll(org.jgroups.relay_server.ChatOuter.Message msg) {
        for(StreamObserver<org.jgroups.relay_server.ChatOuter.Message> obs: observers)
            obs.onNext(msg);
    }

}
