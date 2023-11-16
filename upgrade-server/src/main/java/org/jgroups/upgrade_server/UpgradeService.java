package org.jgroups.upgrade_server;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class UpgradeService extends UpgradeServiceGrpc.UpgradeServiceImplBase {
    protected final Map<String,SynchronizedMap> members=new ConcurrentHashMap<>();
    protected long                              view_id; // global, for all clusters, but who cares
    protected final static Logger               log=Logger.getLogger(UpgradeService.class.getName());

    @Override
    public StreamObserver<Request> connect(StreamObserver<Response> responseObserver) {
        return new StreamObserver<Request>() {
            public void onNext(Request req) {
                if(req.hasMessage()) {
                    Message m=req.getMessage();
                    ByteString pl=m.getPayload();
                    int size=pl != null? pl.size() : 0;
                    log.fine(String.format("msg from %s: %s", m.getSender().getName(), String.format("%d bytes", size)));
                    handleMessage(req.getMessage());
                    return;
                }
                if(req.hasRegisterReq()) {
                    RegisterView rv=req.getRegisterReq();
                    log.fine(String.format("handleRegisterView(%s: %s)", rv.getClusterName(), rv.getView()));
                    handleRegisterView(rv, responseObserver);
                    return;
                }
                if(req.hasJoinReq()) {
                    log.fine(String.format("handleJoinRequest(%s)", req.getJoinReq().getAddress().getName()));
                    handleJoinRequest(req.getJoinReq(), responseObserver);
                    return;
                }
                if(req.hasLeaveReq()) {
                    log.fine(String.format("handleLeaveRequest(%s)", req.getLeaveReq().getLeaver().getName()));
                    handleLeaveRequest(req.getLeaveReq());
                    return;
                }
                log.warning(String.format("request not known: %s", req));
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

        SynchronizedMap m=members.get(cluster);
        if(m != null) {
            Map<Address,StreamObserver<Response>> map=m.getMap();
            Lock lock=m.getLock();
            lock.lock();
            try {
                StreamObserver<Response> observer=map.remove(leaver);
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


    protected void remove(StreamObserver<Response> observer) {
        if(observer == null)
            return;

        for(Map.Entry<String,SynchronizedMap> entry : members.entrySet()) {
            String cluster=entry.getKey();
            SynchronizedMap m=entry.getValue();
            Map<Address,StreamObserver<Response>> map=m.getMap();
            Lock lock=m.getLock();
            lock.lock();
            try {
                map.values().removeIf(val -> Objects.equals(val, observer));
                if(map.isEmpty())
                    members.remove(cluster);
                else
                    postView(map);
            }
            finally {
                lock.unlock();
            }
        }
    }

    protected void handleRegisterView(RegisterView rv, StreamObserver<Response> responseObserver) {
        final String        cluster=rv.getClusterName();
        final List<Address> mbrs=rv.getView().getMemberList();
        final Address       local_addr=rv.getLocalAddr();
        SynchronizedMap     m=members.computeIfAbsent(cluster, k -> new SynchronizedMap(new LinkedHashMap<>()));
        Map<Address,StreamObserver<Response>> map=m.getMap();
        Lock lock=m.getLock();
        lock.lock();
        try {
            for(Address addr: mbrs)
                map.putIfAbsent(addr, null);
            map.put(local_addr, responseObserver);
            // send response:
            RegisterViewOk ack=RegisterViewOk.newBuilder().build();
            Response rsp=Response.newBuilder().setRegViewOk(ack).build();
            responseObserver.onNext(rsp);
        }
        finally {
            lock.unlock();
        }
    }

    protected void handleJoinRequest(JoinRequest join_req, StreamObserver<Response> responseObserver) {
        final String  cluster=join_req.getClusterName();
        final Address joiner=join_req.getAddress();
        SynchronizedMap m=members.computeIfAbsent(cluster, k -> new SynchronizedMap(new LinkedHashMap<>()));
        Map<Address,StreamObserver<Response>> map=m.getMap();
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

    protected void handleLeaveRequest(LeaveRequest leave_req) {
        final String  cluster=leave_req.getClusterName();
        boolean       removed=false;
        Address       leaver=leave_req.getLeaver();

        if(leaver == null)
            return;

        SynchronizedMap m=members.get(cluster);
        if(m != null) {
            Map<Address,StreamObserver<Response>> map=m.getMap();
            Lock lock=m.getLock();
            lock.lock();
            try {
                StreamObserver<Response> observer=map.remove(leaver);
                if(observer != null) {
                    removed=true;
                    observer.onCompleted();
                }
                if(removed && !map.isEmpty())
                    postView(map);
            }
            finally {
                lock.unlock();
            }
        }
    }

    protected void handleMessage(Message msg) {
        String cluster=msg.getClusterName();
        Address dest=msg.hasDestination()? msg.getDestination() : null;

        SynchronizedMap mbrs=members.get(cluster);
        if(mbrs == null) {
            System.err.printf("no members found for cluster %s\n", cluster);
            return;
        }

        if(dest == null)
            relayToAll(msg, mbrs);
        else
            relayTo(msg, mbrs);
    }


    protected void relayToAll(Message msg, SynchronizedMap m) {
        Map<Address,StreamObserver<Response>> map=m.getMap();
        Lock lock=m.getLock();
        lock.lock();
        try {
            if(!map.isEmpty()) {
                // System.out.printf("-- relaying msg to %d members for cluster %s\n", map.size(), msg.getClusterName());
                Response response=Response.newBuilder().setMessage(msg).build();

                // need to honor the exclusion list in the header if present
                Headers hdrs=msg.getHeaders();
                RpcHeader rpcHeader = hdrs != null && hdrs.hasRpcHdr()? hdrs.getRpcHdr() : null;

                Set<Address> exclusions = new HashSet<>();
                if (rpcHeader != null && rpcHeader.getExclusionListList() != null && !rpcHeader.getExclusionListList().isEmpty()) {
                    exclusions.addAll(rpcHeader.getExclusionListList());
                }

                for(Map.Entry<Address, StreamObserver<Response>> node: map.entrySet()) {
                    StreamObserver<Response> obs = node.getValue();
                    try {
                        if (!exclusions.contains(node.getKey())) {
                            obs.onNext(response);
                        }
                    }
                    catch(Throwable t) {
                        System.out.printf("exception relaying message (removing observer): %s\n", t);
                        remove(obs);
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    protected void relayTo(Message msg, SynchronizedMap m) {
        Address dest=msg.getDestination();
        Map<Address,StreamObserver<Response>> map=m.getMap();
        Lock lock=m.getLock();
        lock.lock();
        try {
            StreamObserver<Response> obs=map.get(dest);
            if(obs == null) {
                System.err.printf("unicast destination %s (uuid: %s) not found; dropping message\n",
                                  dest.getName(), dest.getUuid());
                return;
            }

            //System.out.printf("-- relaying msg to member %s for cluster %s\n", dest.getName(), msg.getClusterName());
            Response response=Response.newBuilder().setMessage(msg).build();
            try {
                obs.onNext(response);
            }
            catch(Throwable t) {
                System.err.printf("exception relaying message to %s (removing observer): %s\n", dest.getName(), t);
                remove(obs);
            }
        }
        finally {
            lock.unlock();
        }
    }


    protected void postView(Map<Address,StreamObserver<Response>> map) {
        if(map == null || map.isEmpty())
            return;
        View.Builder view_builder=View.newBuilder();
        Address coord=null;
        for(Address mbr: map.keySet()) {
            view_builder.addMember(mbr);
            if(coord == null)
                coord=mbr;
        }
        view_builder.setViewId(ViewId.newBuilder().setCreator(coord).setId(getNewViewId()).build());

        View new_view=view_builder.build();
        Response response=Response.newBuilder().setView(new_view).build();

        for(Iterator<Map.Entry<Address,StreamObserver<Response>>> it=map.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Address,StreamObserver<Response>> entry=it.next();
            StreamObserver<Response>                    val=entry.getValue();
            try {
                val.onNext(response);
            }
            catch(Throwable t) {
                it.remove();
            }
        }
    }

    protected String dumpDiagnostics() {
        StringBuilder sb=new StringBuilder();
        sb.append("members:\n");
        dumpViews(sb);
        return sb.append("\n").toString();
    }

    protected void dumpViews(final StringBuilder sb) {
        for(Map.Entry<String,SynchronizedMap> entry: members.entrySet()) {
            String cluster=entry.getKey();
            SynchronizedMap m=entry.getValue();
            Map<Address,StreamObserver<Response>> map=m.getMap();
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

    protected synchronized long getNewViewId() {return view_id++;}


    protected static class SynchronizedMap {
        protected final Map<Address,StreamObserver<Response>> map;
        protected final Lock                                  lock=new ReentrantLock();

        public SynchronizedMap(Map<Address,StreamObserver<Response>> map) {
            this.map=map;
        }

        protected Map<Address,StreamObserver<Response>> getMap()       {return map;}
        protected Lock                                  getLock()      {return lock;}
    }

}
