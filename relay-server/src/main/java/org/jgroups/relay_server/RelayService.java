package org.jgroups.relay_server;

import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class RelayService extends RelayServiceGrpc.RelayServiceImplBase {
    protected final Collection<StreamObserver<Message>>    observers=new ConcurrentLinkedQueue<>();
    protected final ConcurrentMap<String,SynchronizedMap>  views=new ConcurrentHashMap<>();


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
                final Address joiner=req.getAddress();

                SynchronizedMap m=views.computeIfAbsent(cluster, k -> new SynchronizedMap(new LinkedHashMap()));
                Map<Address,StreamObserver<View>> map=m.getMap();
                Lock lock=m.getLock();
                lock.lock();
                try {
                    if(map.putIfAbsent(joiner, responseObserver) == null)
                        postView(map);
                }
                finally {
                    lock.unlock();
                }
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

        SynchronizedMap m=views.get(cluster);
        if(m != null) {
            Map<Address,StreamObserver<View>> map=m.getMap();
            Lock lock=m.getLock();
            lock.lock();
            try {
                StreamObserver<View> observer=map.remove(leaver);
                if(observer != null) {
                    removed=true;
                    observer.onCompleted();
                }
                if(removed)
                    postView(map);
            }
            finally {
                lock.unlock();
            }
        }
        responseObserver.onNext(Void.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void dump(Void request, StreamObserver<DumpResponse> responseObserver) {
        String result=dumpDiagnostics();
        responseObserver.onNext(DumpResponse.newBuilder().setDump(result).build());
        responseObserver.onCompleted();
    }


    protected void remove(StreamObserver<View> observer) {
        if(observer == null)
            return;

        for(Map.Entry<String,SynchronizedMap> entry : views.entrySet()) {
            String cluster=entry.getKey();
            SynchronizedMap m=entry.getValue();
            Map<Address,StreamObserver<View>> map=m.getMap();
            Lock lock=m.getLock();
            lock.lock();
            try {
                map.values().removeIf(val -> Objects.equals(val, observer));
                if(map.isEmpty())
                    views.remove(cluster);
                else
                    postView(map);
            }
            finally {
                lock.unlock();
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

        for(Iterator<Map.Entry<Address,StreamObserver<View>>> it=map.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Address,StreamObserver<View>> entry=it.next();
            StreamObserver<View>                    val=entry.getValue();
            try {
                val.onNext(new_view);
            }
            catch(Throwable t) {
                it.remove();
            }
        }
    }

    protected String dumpDiagnostics() {
        StringBuilder sb=new StringBuilder();
        sb.append(String.format("%d observers\nviews:\n", observers.size()));
        dumpViews(sb);
        return sb.append("\n").toString();
    }

    protected void dumpViews(final StringBuilder sb) {
        for(Map.Entry<String,SynchronizedMap> entry: views.entrySet()) {
            String cluster=entry.getKey();
            SynchronizedMap m=entry.getValue();
            Map<Address,StreamObserver<View>> map=m.getMap();
            Lock lock=m.getLock();
            lock.lock();
            try {
                sb.append(cluster).append(": ").append(Utils.print(map.keySet())).append("\n");
            }
            finally {
                lock.unlock();
            }
        }
    }


    protected static class SynchronizedMap {
        protected final Map<Address,StreamObserver<View>> map;
        protected final Lock                              lock=new ReentrantLock();

        public SynchronizedMap(Map<Address,StreamObserver<View>> map) {
            this.map=map;
        }

        protected Map<Address,StreamObserver<View>> getMap() {return map;}
        protected Lock                              getLock() {return lock;}
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
