package org.jgroups.relay_server;

import io.grpc.stub.StreamObserver;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class RelayService extends RelayServiceGrpc.RelayServiceImplBase {
    protected final Collection<StreamObserver<Message>>                               observers=new ConcurrentLinkedQueue<>();
    protected final ConcurrentMap<String,ConcurrentMap<Address,StreamObserver<View>>> views=new ConcurrentHashMap<>();


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

    public void register(Registration request, StreamObserver<View> responseObserver) {
        String  cluster=request.getClusterName();
        Address addr=request.getLocalAddr();
        View    view=request.getView();
        boolean added=false;

        ConcurrentMap<Address,StreamObserver<View>> map=views.computeIfAbsent(cluster, k -> new ConcurrentHashMap<>());

        if(map.putIfAbsent(addr, responseObserver) == null)
            added=true;
        if(view != null) {
            for(Address mbr: view.getMemberList()) {
                if(map.putIfAbsent(mbr, responseObserver) == null)
                    added=true;
            }
        }
        if(added) {
            View.Builder view_builder=View.newBuilder();
            for(Address mbr: map.keySet())
                view_builder.addMember(mbr);
            View new_view=view_builder.build();
            postView(new_view, map);
        }
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

    protected static void postView(View v, ConcurrentMap<Address,StreamObserver<View>> map) {
        for(Map.Entry<Address,StreamObserver<View>> entry: map.entrySet()) {
            Address              key=entry.getKey();
            StreamObserver<View> val=entry.getValue();
            try {
                val.onNext(v);
            }
            catch(Throwable t) {
                map.remove(key);
            }
        }
    }

}
