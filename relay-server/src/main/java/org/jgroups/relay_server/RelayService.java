package org.jgroups.relay_server;

import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class RelayService extends RelayServiceGrpc.RelayServiceImplBase {
    protected final Collection<StreamObserver<Message>>                     observers=new ConcurrentLinkedQueue<>();
    protected final ConcurrentMap<String,Map<Address,StreamObserver<View>>> views=new ConcurrentHashMap<>();


    @Override
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
                System.out.printf("Removing observer %s\n", responseObserver);
                observers.remove(responseObserver);
            }
        };
    }


    @Override
    public StreamObserver<JoinRequest> join(StreamObserver<View> responseObserver) {
        return new StreamObserver<JoinRequest>() {
            public void onNext(JoinRequest req) {
                final String  cluster=req.getClusterName();
                final Address addr=req.getLocalAddr();
                final View    view=req.getView();
                boolean       added=false;


                Map<Address,StreamObserver<View>> map;
                synchronized(views) {
                    map=views.computeIfAbsent(cluster, k -> new LinkedHashMap());
                    if(map.putIfAbsent(addr, responseObserver) == null)
                        added=true;
                    if(view != null) {
                        for(Address mbr : view.getMemberList()) {
                            if(map.putIfAbsent(mbr, responseObserver) == null)
                                added=true;
                        }
                    }
                }
                if(added)
                    postView(map);
            }

            public void onError(Throwable t) {
                remove(responseObserver);
            }

            public void onCompleted() {
                remove(responseObserver);
            }
        };
    }

    @Override
    public void leave(LeaveRequest req, StreamObserver<Void> responseObserver) {
        final String  cluster=req.getClusterName();
        boolean       removed=false;
        Address       leaver=req.getLeaver();

        if(leaver == null)
            return;

        Map<Address,StreamObserver<View>> map;
        synchronized(views) {
            map=views.get(cluster);
            if(map != null) {
                StreamObserver<View> observer=map.remove(leaver);
                if(observer != null) {
                    removed=true;
                    observer.onCompleted();
                }
                if(removed)
                    postView(map);
            }
        }
        responseObserver.onNext(Void.newBuilder().build());
        responseObserver.onCompleted();
    }

    protected void remove(StreamObserver<View> observer) {
        if(observer == null)
            return;

        synchronized(views) {
            for(Map.Entry<String,Map<Address,StreamObserver<View>>> entry : views.entrySet()) {
                String cluster=entry.getKey();
                Map<Address,StreamObserver<View>> map=entry.getValue();
                map.values().removeIf(val -> Objects.equals(val, observer));
                if(map.isEmpty())
                    views.remove(cluster);
                else {
                    // post new view
                    postView(map);
                }
            }
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

    protected static void postView(Map<Address,StreamObserver<View>> map) {
        View.Builder view_builder=View.newBuilder();
        for(Address mbr: map.keySet())
            view_builder.addMember(mbr);
        View new_view=view_builder.build();

        for(Map.Entry<Address,StreamObserver<View>> entry: map.entrySet()) {
            Address              key=entry.getKey();
            StreamObserver<View> val=entry.getValue();
            try {
                val.onNext(new_view);
            }
            catch(Throwable t) {
                map.remove(key);
            }
        }
    }



   /* protected static AddressList diff(View first, View second) {
        if(first == null || second == null)
            return null;
        List<Address> first_list=first.getMemberList(), second_list=second.getMemberList();
        first_list.removeAll(second_list);
        if(first_list.isEmpty())
            return null;
        AddressList.Builder builder=AddressList.newBuilder();
        for(Address address: first_list)
            builder.addMember(address);
        return builder.build();
    }*/
}
