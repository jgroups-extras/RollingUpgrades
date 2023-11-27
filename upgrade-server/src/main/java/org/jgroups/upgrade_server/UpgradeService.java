package org.jgroups.upgrade_server;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * @author Bela Ban
 * @since  1.0.0
 */
public class UpgradeService extends UpgradeServiceGrpc.UpgradeServiceImplBase {
    protected final Map<String,SynchronizedMap> members=new ConcurrentHashMap<>();
    protected final Logger                      log=LogManager.getFormatterLogger(UpgradeService.class);
    protected boolean                           verbose;

    public boolean        verbose()          {return verbose;}
    public UpgradeService verbose(boolean v) {verbose=v; return this;}

    @Override
    public StreamObserver<Request> connect(final StreamObserver<Response> responseObserver) {
        return new StreamObserver<Request>() {
            public void onNext(Request req) {
                if(req.hasMessage()) {
                    Message m=req.getMessage();
                    ByteString pl=m.getPayload();
                    int size=pl != null? pl.size() : 0;
                    Address dest=m.hasDestination()? m.getDestination() : null;
                    log.trace("msg from %s to %s: %s", m.getSender().getName(),
                              dest != null && dest.getName() != null? dest.getName() : "<all>",
                              String.format("%d bytes", size));

                    //Context ctx=Context.current().fork();
                    //Context prev=ctx.attach();
                    //try {
                        handleMessage(req.getMessage());
                   // }
                    //finally {
                     //   ctx.detach(prev);
                    //}
                    return;
                }
                if(req.hasRegisterReq()) {
                    RegisterView rv=req.getRegisterReq();
                    log.debug("handleRegisterView(%s: %s)", rv.getClusterName(), Utils.print(rv.getView()));
                    handleRegisterView(rv, responseObserver);
                    return;
                }
                if(req.hasJoinReq()) {
                    log.debug("handleJoinRequest(%s)", req.getJoinReq().getAddress().getName());

                    //Context ctx=Context.current().fork();
                    //Context prev=ctx.attach();
                    //try {
                        handleJoinRequest(req.getJoinReq(), responseObserver);
                    //}
                    //finally {
                      //  ctx.detach(prev);
                    //}
                    return;
                }
                if(req.hasLeaveReq()) {
                    log.debug("handleLeaveRequest(%s)", req.getLeaveReq().getLeaver().getName());
                    handleLeaveRequest(req.getLeaveReq(), responseObserver);
                    return;
                }
                if(req.hasGetViewReq()) {
                    String cluster=req.getGetViewReq().getClusterName();
                    log.debug("handleGetViewRequest(%s)", cluster);
                    handleGetViewRequest(cluster, responseObserver);
                    return;
                }
                log.warn("request not known: %s", req);
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
    public void dump(Void request, StreamObserver<DumpResponse> responseObserver) {
        String result=dumpDiagnostics();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized(responseObserver) { // responseObserver always points to the same object
            responseObserver.onNext(DumpResponse.newBuilder().setDump(result).build());
            responseObserver.onCompleted();
        }
    }

    protected void handleRegisterView(RegisterView rv, final StreamObserver<Response> responseObserver) {
        final String        cluster=rv.getClusterName();
        final List<Address> mbrs=rv.getView().getMemberList();
        final long          view_id=rv.getView().getViewId().getId();
        final Address       local_addr=rv.getLocalAddr();
        SynchronizedMap     m=members.computeIfAbsent(cluster, k -> new SynchronizedMap(cluster));
        for(Address addr: mbrs)
            m.put(addr, null, false);
        m.put(local_addr, responseObserver, true);
        m.setViewId(view_id);
        // send response:
        RegisterViewOk ack=RegisterViewOk.newBuilder().build();
        Response rsp=Response.newBuilder().setRegViewOk(ack).build();

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized(responseObserver) { // responseObserver always points to the same object
            responseObserver.onNext(rsp);
        }
    }

    protected void handleJoinRequest(JoinRequest join_req, StreamObserver<Response> responseObserver) {
        final String    cluster=join_req.getClusterName();
        final Address   joiner=join_req.getAddress();
        SynchronizedMap m=members.computeIfAbsent(cluster, k -> new SynchronizedMap(cluster));
        if(m.put(joiner, responseObserver, true)) {
            if(verbose)
                System.out.printf("-- %s joined: %s\n", joiner.getName(), m);
            m.postView();
        }
    }

    protected void handleLeaveRequest(LeaveRequest leave_req, StreamObserver<Response> responseObserver) {
        final String  cluster=leave_req.getClusterName();
        Address       leaver=leave_req.getLeaver();
        if(leaver == null)
            return;

        // responseObserver.onCompleted();
        StreamObserver<Response> obs=null;
        SynchronizedMap m=members.get(cluster);
        if(m != null && (obs=m.remove(leaver)) != null) {
            obs.onCompleted(); // obs == responseObserver
            if(m.isEmpty()) {
                if(verbose)
                    System.out.printf("-- %s left: []\n", leaver.getName());
                members.remove(cluster);
            }
            else {
                if(verbose)
                    System.out.printf("-- %s left: %s\n", leaver.getName(), m);
                m.postView();
            }
        }
    }

    protected void handleGetViewRequest(String cluster, StreamObserver<Response> responseObserver) {
        SynchronizedMap map=members.get(Objects.requireNonNull(cluster));
        View view=map != null? map.getView() : null;
        if(view == null) {
            log.warn("no view was found for cluster %s", cluster);
            return;
        }
        // send response
        GetViewResponse rsp=GetViewResponse.newBuilder().setView(view).build();
        Response r=Response.newBuilder().setGetViewRsp(rsp).build();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized(responseObserver) { // // responseObserver always points to the same object
            responseObserver.onNext(r);
        }
    }

    protected void handleMessage(Message msg) {
        String cluster=msg.getClusterName();
        Address dest=msg.hasDestination()? msg.getDestination() : null;

        SynchronizedMap map=members.get(cluster);
        if(map == null) {
            System.err.printf("no members found for cluster %s\n", cluster);
            return;
        }

        if(dest == null)
            relayToAll(msg, map);
        else
            relayTo(dest, msg, map);
    }


    protected void relayToAll(Message msg, SynchronizedMap m) {
        if(!m.isEmpty()) {
            Response response=Response.newBuilder().setMessage(msg).build();

            // need to honor the exclusion list in the header if present
            Headers hdrs=msg.getHeaders();
            RpcHeader rpcHeader = hdrs != null && hdrs.hasRpcHdr()? hdrs.getRpcHdr() : null;
            Set<Address> exclusions=new HashSet<>();
            if(rpcHeader != null && rpcHeader.getExclusionListList() != null && !rpcHeader.getExclusionListList().isEmpty())
                exclusions.addAll(rpcHeader.getExclusionListList());
            m.forAll(response, exclusions, (__,v) -> remove(v));
        }
    }

    protected void relayTo(Address dest, Message msg, SynchronizedMap m) {
        StreamObserver<Response> obs=m.get(dest);
        if(obs == null) {
            System.err.printf("unicast destination %s (uuid: %s) not found; dropping message\n",
                              dest.getName(), dest.getUuid());
            return;
        }
        Response response=Response.newBuilder().setMessage(msg).build();
        try {
            synchronized(obs) {
                obs.onNext(response);
            }
        }
        catch(Throwable t) {
            System.err.printf("exception relaying message to %s (removing observer): %s\n", dest.getName(), t);
            remove(obs);
        }
    }


    protected void remove(StreamObserver<Response> observer) {
        if(observer == null)
            return;

        for(Map.Entry<String,SynchronizedMap> entry: members.entrySet()) {
            String cluster=entry.getKey();
            SynchronizedMap m=entry.getValue();
            boolean removed=m.removeIf(val -> Objects.equals(val, observer));
            if(removed) {
                if(m.isEmpty())
                    members.remove(cluster);
                else
                    m.postView();
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
        for(SynchronizedMap m: members.values())
            sb.append(m.toString()).append("\n");
    }

    protected class SynchronizedMap {
        protected final String                                cluster;
        protected final Map<Address,StreamObserver<Response>> map=new LinkedHashMap<>();
        protected long                                        view_id;

        public SynchronizedMap(String cluster) {
            this.cluster=cluster;
        }

        protected String                                cluster()         {return cluster;}
        protected synchronized StreamObserver<Response> get(Address mbr)  {return map.get(mbr);}
        protected synchronized boolean                  isEmpty()         {return map.isEmpty();}
        protected synchronized long                     getViewId()       {return view_id;}
        protected synchronized long                     getNewViewId()    {return ++view_id;}
        protected synchronized long                     setViewId(long v) {return view_id=Math.max(view_id, v);}

        @Override
        public synchronized String toString() {
            return String.format("%s: %s", cluster, Utils.printView(view_id, map.keySet()));
        }

        protected synchronized boolean put(Address joiner, StreamObserver<Response> o, boolean override) {
            StreamObserver<Response> prev=override? map.put(joiner, o) : map.putIfAbsent(joiner, o);
            return prev == null;
        }

        protected synchronized StreamObserver<Response> remove(Address leaver) {
            return map.remove(leaver);
        }

        protected synchronized boolean removeIf(Predicate<? super StreamObserver<Response>> predicate) {
            return map.values().removeIf(predicate);
        }

        protected void postView() {
            View new_view=getView();
            if(new_view == null)
                return;

            log.debug("new view: %s", Utils.printView(view_id, new_view.getMemberList()));
            Response response=Response.newBuilder().setView(new_view).build();
            forAll(response, null, null);
        }

        protected View getView() {
            View.Builder view_builder=View.newBuilder();
            Address coord=null;
            synchronized(this) {
                if(map == null || map.isEmpty())
                    return null;
                for(Address mbr: map.keySet()) {
                    view_builder.addMember(mbr);
                    if(coord == null)
                        coord=mbr;
                }
            }
            view_builder.setViewId(ViewId.newBuilder().setCreator(coord).setId(getNewViewId()).build());
            return view_builder.build();
        }

        protected void forAll(Response response, Set<Address> exclusions,
                              BiConsumer<Address,StreamObserver<Response>> exception_handler) {
            List<Record> observers=new ArrayList<>();
            synchronized(this) {
                for(Map.Entry<Address,StreamObserver<Response>> entry: map.entrySet()) {
                    StreamObserver<Response> obs=entry.getValue();
                    if(exclusions == null || !exclusions.contains(entry.getKey()))
                        observers.add(new Record(entry.getKey(), obs));
                }
            }
            // iterate through all members outside the lock scope
            for(Record r: observers) {
                synchronized(r.obs) {
                    try {
                        r.obs().onNext(response);
                    }
                    catch(Throwable t) {
                        log.warn("failed relaying message to %s: %s", r.addr().getName(), t);
                        if(exception_handler != null)
                            exception_handler.accept(r.addr(), r.obs());
                    }
                }
            }
        }


        protected final class Record {
            final Address address;
            final StreamObserver<Response> obs;

            private Record(Address address, StreamObserver<Response> obs) {
                this.address=Objects.requireNonNull(address);
                this.obs=Objects.requireNonNull(obs);
            }

            private Address                  addr() {return address;}
            private StreamObserver<Response> obs()  {return obs;}
        }

    }

}
