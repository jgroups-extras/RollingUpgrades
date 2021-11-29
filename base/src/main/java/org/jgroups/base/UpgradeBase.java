package org.jgroups.base;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.annotations.Property;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.common.GrpcClient;
import org.jgroups.common.Marshaller;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.stack.Protocol;
import org.jgroups.upgrade_server.RpcHeader;
import org.jgroups.util.NameCache;
import org.jgroups.util.UUID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Relays application messages to the UpgradeServer (when active). Should be the top protocol in a stack.
 * @author Bela Ban
 * @since  1.0
 * @todo: implement support for addresses other than UUIDs
 * @todo: implement reconnection to server (server went down and then up again)
 */
@MBean(description="Protocol that redirects all messages to/from an UpgradeServer")
public abstract class UpgradeBase extends Protocol {
    @Property(description="Whether or not to perform relaying via the UpgradeServer",writable=false)
    protected volatile boolean                          active;

    @Property(description="The IP address (or symbolic name) of the UpgradeServer")
    protected String                                    server_address="localhost";

    @Property(description="The port on which the UpgradeServer is listening")
    protected int                                       server_port=50051;

    @Property(description="The filename of the UpgradeServer's certificate (with the server's public key). " +
      "If non-null and non-empty, the client will use an encrypted connection to the server")
    protected String                                    server_cert;

    @Property(description="Time in ms between trying to reconnect to UpgradeServer (while disconnected)")
    protected long                                      reconnect_interval=3000;

    @ManagedAttribute(description="The local address")
    protected Address                                   local_addr;

    @ManagedAttribute(description="Shows the local view")
    protected org.jgroups.View                          local_view;

    @ManagedAttribute(description="The global view (provided by the UpgradeServer)")
    protected org.jgroups.View                          global_view;

    @Property(description="If RPCs are sent over UPGRADE, then we must serialize every request, not just the responses")
    protected boolean                                   rpcs;

    @ManagedAttribute(description="The cluster this member is a part of")
    protected String                                    cluster;

    protected GrpcClient                                client=new GrpcClient();

    protected org.jgroups.common.Marshaller             marshaller;

    protected static final short                        REQ_ID=ClassConfigurator.getProtocolId(RequestCorrelator.class);

    @ManagedAttribute public String getMarshaller() {
        return marshaller != null? marshaller.getClass().getSimpleName() : "n/a";
    }

    public Marshaller                  marshaller()             {return marshaller;}
    public <T extends UpgradeBase> T   marshaller(Marshaller m) {this.marshaller=m; return (T)this;}
    public boolean                     getRpcs()                {return rpcs;}
    public <T extends UpgradeBase> T   setRpcs(boolean r)       {rpcs=r; return (T)this;}


    @ManagedAttribute(description="True if the connected to the gRPC server")
    public boolean isConnected() {
        return client.isConnected();
    }

    @ManagedAttribute(description="True if the reconnector is running")
    public boolean isReconnecting() {return client.reconnectorRunning();}


    public void init() throws Exception {
        super.init();
        client.setServerAddress(server_address).setServerPort(server_port).setServerCert(server_cert)
          .addViewHandler(this::handleView).addMessageHandler(this::handleMessage)
          .setReconnectionFunction(this::connect)
          .setReconnectInterval(reconnect_interval)
          .start();
    }



    public void stop() {
        client.stop();
    }

    @ManagedOperation(description="Enable forwarding and receiving of messages to/from the UpgradeServer")
    public synchronized void activate() {
        if(!active) {
            connect();
            active=true;
        }
    }

    @ManagedOperation(description="Disable forwarding and receiving of messages to/from the UpgradeServer")
    public synchronized void deactivate() {
        if(active) {
            disconnect();
            active=false;
        }
    }


    public Object down(Event evt) {
        switch(evt.type()) {
            case Event.SET_LOCAL_ADDRESS:
                local_addr=evt.arg();
                break;
            case Event.CONNECT:
            case Event.CONNECT_USE_FLUSH:
            case Event.CONNECT_WITH_STATE_TRANSFER:
            case Event.CONNECT_WITH_STATE_TRANSFER_USE_FLUSH:
                cluster=evt.arg();
                Object ret=down_prot.down(evt);
                if(active)
                    connect();
                return ret;
            case Event.DISCONNECT:
                ret=down_prot.down(evt);
                disconnect();
                return ret;
        }
        return down_prot.down(evt);
    }


    public Object up(Event evt) {
        if(evt.type() == Event.VIEW_CHANGE) {
            local_view=evt.arg();
            if(active)
                return null;
        }
        return up_prot.up(evt);
    }



    protected void connect() {
        org.jgroups.upgrade_server.Address addr=jgroupsAddressToProtobufAddress(local_addr);
        client.connect(cluster, addr);
    }

    protected void disconnect() {
        org.jgroups.upgrade_server.Address addr=jgroupsAddressToProtobufAddress(local_addr);
        client.disconnect(cluster, addr);
    }

    protected void handleView(org.jgroups.upgrade_server.View view) {
        View jg_view=protobufViewToJGroupsView(view);
        global_view=jg_view;
        up_prot.up(new Event(Event.VIEW_CHANGE, jg_view));
    }

    protected void handleMessage(org.jgroups.upgrade_server.Message m) {
        try {
            Message msg=protobufMessageToJGroupsMessage(m);
            up_prot.up(msg);
        }
        catch(Exception e) {
            log.error("%s: failed reading message: %s", local_addr, e);
        }
    }

    protected abstract org.jgroups.upgrade_server.Message jgroupsMessageToProtobufMessage(String cluster, Message jg_msg)
      throws Exception;

    protected abstract Message protobufMessageToJGroupsMessage(org.jgroups.upgrade_server.Message msg) throws Exception;


    protected static RpcHeader jgroupsReqHeaderToProtobufRpcHeader(RequestCorrelator.Header hdr) {
        RpcHeader.Builder builder = RpcHeader.newBuilder().setType(hdr.type).setRequestId(hdr.req_id).setCorrId(hdr.corrId);
        if (hdr instanceof RequestCorrelator.MultiDestinationHeader) {
            RequestCorrelator.MultiDestinationHeader mdhdr = (RequestCorrelator.MultiDestinationHeader) hdr;
            Address[] exclusions = mdhdr.exclusion_list;
            if (exclusions != null && exclusions.length > 0) {
                builder.addAllExclusionList(Arrays.stream(exclusions).map(UpgradeBase::jgroupsAddressToProtobufAddress)
                                              .collect(Collectors.toList()));
            }
        }
        return builder.build();
    }

    protected static RequestCorrelator.Header protobufRpcHeaderToJGroupsReqHeader(RpcHeader hdr) {
        byte type=(byte)hdr.getType();
        long request_id=hdr.getRequestId();
        short corr_id=(short)hdr.getCorrId();
        return (RequestCorrelator.Header)new RequestCorrelator.Header(type, request_id, corr_id).setProtId(REQ_ID);
    }


    protected static org.jgroups.upgrade_server.Address jgroupsAddressToProtobufAddress(Address jgroups_addr) {
        if(jgroups_addr == null)
            return org.jgroups.upgrade_server.Address.newBuilder().build();
        if(!(jgroups_addr instanceof org.jgroups.util.UUID))
            throw new IllegalArgumentException(String.format("JGroups address has to be of type UUID but is %s",
                                                             jgroups_addr.getClass().getSimpleName()));
        UUID uuid=(UUID)jgroups_addr;
        String name=NameCache.get(jgroups_addr);

        org.jgroups.upgrade_server.UUID pbuf_uuid=org.jgroups.upgrade_server.UUID.newBuilder()
          .setLeastSig(uuid.getLeastSignificantBits()).setMostSig(uuid.getMostSignificantBits()).build();
        org.jgroups.upgrade_server.Address.Builder builder=org.jgroups.upgrade_server.Address.newBuilder()
          .setUuid(pbuf_uuid);
        if(name != null)
            builder.setName(name);
        return builder.build();
    }

    protected static Address protobufAddressToJGroupsAddress(org.jgroups.upgrade_server.Address pbuf_addr) {
        if(pbuf_addr == null)
            return null;
        org.jgroups.upgrade_server.UUID pbuf_uuid=pbuf_addr.hasUuid()? pbuf_addr.getUuid() : null;

        UUID uuid=pbuf_uuid == null? null : new UUID(pbuf_uuid.getMostSig(), pbuf_uuid.getLeastSig());
        String logical_name=pbuf_addr.getName();
        if(uuid != null && logical_name != null && !logical_name.isEmpty())
            NameCache.add(uuid, logical_name);
        return uuid;
    }


    protected static org.jgroups.View protobufViewToJGroupsView(org.jgroups.upgrade_server.View v) {
        org.jgroups.upgrade_server.ViewId pbuf_vid=v.getViewId();
        List<org.jgroups.upgrade_server.Address> pbuf_mbrs=v.getMemberList();
        org.jgroups.ViewId jg_vid=new org.jgroups.ViewId(protobufAddressToJGroupsAddress(pbuf_vid.getCreator()),
                                                         pbuf_vid.getId());
        List<Address> members=new ArrayList<>();
        pbuf_mbrs.stream().map(UpgradeBase::protobufAddressToJGroupsAddress).forEach(members::add);
        return new org.jgroups.View(jg_vid, members);
    }


}
