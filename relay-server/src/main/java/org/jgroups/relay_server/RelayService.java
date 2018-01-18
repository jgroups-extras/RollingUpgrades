package org.jgroups.relay_server;

import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class RelayService extends org.jgroups.relay_server.RelayServiceGrpc.RelayServiceImplBase {
    protected List<StreamObserver<org.jgroups.relay_server.Relay.Message>> observers=new ArrayList<>();


    @Override
    public StreamObserver<org.jgroups.relay_server.Relay.Message> relay(StreamObserver<org.jgroups.relay_server.Relay.Message> responseObserver) {
        observers.add(responseObserver);

        return new StreamObserver<org.jgroups.relay_server.Relay.Message>() {
            public void onNext(org.jgroups.relay_server.Relay.Message value) {
                postToAll(value);
            }

            public void onError(Throwable t) {

            }

            public void onCompleted() {
                for(StreamObserver<org.jgroups.relay_server.Relay.Message> obs: observers)
                    obs.onCompleted();
                observers.remove(this);
            }
        };
    }

    protected void postToAll(org.jgroups.relay_server.Relay.Message msg) {
        for(StreamObserver<org.jgroups.relay_server.Relay.Message> obs: observers)
            obs.onNext(msg);
    }

}
