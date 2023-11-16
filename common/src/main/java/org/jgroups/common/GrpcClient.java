package org.jgroups.common;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import org.jgroups.upgrade_server.*;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.jgroups.common.ConnectionStatus.State.*;

/**
 * Class which interacts with a gRPC server, e.g. sending and receiving messages, retry logic etc. The state transitions
 * are start - connect - disconnect (possibly multiple times) - stop
 * @author Bela Ban
 * @since  1.1.1
 */
public class GrpcClient implements StreamObserver<Response> {
    protected String                                server_address="localhost";
    protected int                                   server_port=50051;
    protected String                                server_cert;
    protected ManagedChannel                        channel;
    protected UpgradeServiceGrpc.UpgradeServiceStub asyncStub;
    protected StreamObserver<Request>               send_stream;
    protected final Lock                            send_stream_lock=new ReentrantLock();
    protected final Set<Consumer<View>>             view_handlers=new HashSet<>();
    protected final Set<Consumer<Message>>          message_handlers=new HashSet<>();
    protected final ConnectionStatus                state=new ConnectionStatus();
    protected long                                  reconnect_interval=3000; // in ms
    protected Runner                                reconnector;
    protected Runnable                              reconnect_function;
    protected static final Logger                   log=Logger.getLogger(GrpcClient.class.getSimpleName());

    public String     getServerAddress()                        {return server_address;}
    public GrpcClient setServerAddress(String a)                {server_address=a; return this;}
    public int        getServerPort()                           {return server_port;}
    public GrpcClient setServerPort(int p)                      {server_port=p; return this;}
    public String     getServerCert()                           {return server_cert;}
    public GrpcClient setServerCert(String c)                   {server_cert=c; return this;}
    public boolean    isConnected()                             {return state.isState(ConnectionStatus.State.connected);}
    public ConnectionStatus state()                             {return state;}
    public GrpcClient setReconnectionFunction(Runnable f)       {reconnect_function=f; return this;}
    public long       getReconnectInterval()                    {return reconnect_interval;}
    public GrpcClient setReconnectInterval(long i)              {reconnect_interval=i; return this;}
    public GrpcClient addViewHandler(Consumer<View> h)          {view_handlers.add(h); return this;}
    public GrpcClient removeViewHandler(Consumer<View> h)       {view_handlers.remove(h); return this;}
    public GrpcClient addMessageHandler(Consumer<Message> h)    {message_handlers.add(h); return this;}
    public GrpcClient removeMessageHandler(Consumer<Message> h) {message_handlers.remove(h); return this;}
    public boolean    reconnectorRunning()                      {return reconnector.isRunning();}



    public GrpcClient start() throws Exception {
        InputStream server_cert_stream=null;
        SslContext  ctx=null;

        if(server_cert != null && !server_cert.trim().isEmpty()) {
            if((server_cert_stream=Utils.getFile(server_cert)) == null)
                throw new FileNotFoundException(String.format("server certificate (%s) not found", server_cert));
            ctx=GrpcSslContexts.forClient().trustManager(server_cert_stream).build();
        }
        NettyChannelBuilder cb=NettyChannelBuilder.forAddress(server_address, server_port);
        if(server_cert_stream == null)
            channel=cb.usePlaintext().build();
        else
            channel=cb.sslContext(ctx).build();
        asyncStub=UpgradeServiceGrpc.newStub(channel);
        if(reconnect_function != null)
            reconnector=createReconnector();
        return this;
    }

    public GrpcClient stop() {
        if(channel != null) {
            channel.shutdown();
            try {
                channel.awaitTermination(30, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
            }
        }
        return this;
    }

    public synchronized GrpcClient registerView(String cluster, View local_view, Address local_addr) {
        if(state.setState(disconnected, connecting)) {
            send_stream=asyncStub.connect(this);
            RegisterView register_req=RegisterView.newBuilder().setClusterName(cluster).setView(local_view)
              .setLocalAddr(local_addr).build();
            Request req=Request.newBuilder().setRegisterReq(register_req).build();
            send_stream.onNext(req);
        }
        return this;
    }

    public synchronized GrpcClient connect(String cluster, Address local_addr) {
        if(state.setState(disconnected, connecting)) {
            send_stream=asyncStub.connect(this);
            JoinRequest join_req=JoinRequest.newBuilder().setAddress(local_addr).setClusterName(cluster).build();
            Request req=Request.newBuilder().setJoinReq(join_req).build();
            send_stream.onNext(req);
        }
        return this;
    }

    public synchronized GrpcClient disconnect(String cluster, Address local_addr) {
        if(send_stream != null) {
            if(local_addr != null && cluster != null) {
                LeaveRequest leave_req=LeaveRequest.newBuilder().setClusterName(cluster).setLeaver(local_addr).build();
                Request request=Request.newBuilder().setLeaveReq(leave_req).build();
                send_stream.onNext(request);
                state.setState(disconnected);
            }
            send_stream.onCompleted();
        }
        return this;
    }

    public GrpcClient send(Request req) {
        if(state.isStateOneOf(disconnected, disconnecting))
            throw new IllegalStateException(String.format("not connected to %s:%d", server_address, server_port));

        send_stream_lock.lock();
        try {
            // Per javadoc, StreamObserver is not thread-safe and calls onNext() must be handled by the application
            send_stream.onNext(req);
            return this;
        }
        finally {
            send_stream_lock.unlock();
        }
    }

    public void onNext(Response rsp) {
        if(rsp.hasMessage()) {
            handleMessage(rsp.getMessage());
            return;
        }
        if(rsp.hasView()) {
            handleView(rsp.getView());
            return;
        }
        if(rsp.hasRegViewOk()) {
            state.setState(connected);
            return;
        }
        throw new IllegalStateException(String.format("response is illegal: %s", rsp));
    }

    public void onError(Throwable t) {
        if(state.isState(connected))
            log.warning(String.format("exception from server: %s (%s)", t, t.getCause()));
        state.setState(disconnected);
        startReconnector();
    }

    public void onCompleted() {
    }


    protected void handleMessage(Message msg) {
        for(Consumer<Message> c: message_handlers)
            c.accept(msg);
    }

    protected void handleView(View view) {
        state.setState(connected);
        stopReconnector();
        for(Consumer<View> c: view_handlers)
            c.accept(view);
    }

    protected synchronized Runner createReconnector() {
        return new Runner("client-reconnector",
                          () -> {
                              reconnect_function.run();
                              Utils.sleep(reconnect_interval);
                          },null);
    }

    protected synchronized GrpcClient startReconnector() {
        if(reconnector != null && !reconnector.isRunning()) {
            log.fine("starting reconnector");
            reconnector.start();
        }
        return this;
    }

    protected synchronized GrpcClient stopReconnector() {
        if(reconnector != null && reconnector.isRunning()) {
            log.fine("stopping reconnector");
            reconnector.stop();
        }
        return this;
    }


    public static void main(String[] args) throws Exception {
        GrpcClient client=new GrpcClient()
          .addMessageHandler(m -> System.out.printf("-- msg from %s: %s\n",
                                                    m.getSender().getName(), new String(m.getPayload().toByteArray())))
          .addViewHandler(v -> System.out.printf("-- view: %s\n", v))
          .start();


        UUID uuid=UUID.newBuilder().setLeastSig(1).setMostSig(2).build();
        Address a=Address.newBuilder().setUuid(uuid).setName("A").build();
        client.connect("rpcs", a);

        byte[] buf="hello world".getBytes();
        Message msg=Message.newBuilder()
          .setClusterName("rpcs")
          .setSender(Address.newBuilder().setName("A").setUuid(uuid).build())
          .setPayload(ByteString.copyFrom(buf)).build();
        client.send(Request.newBuilder().setMessage(msg).build());
        client.disconnect("rpcs", a);

    }
}
