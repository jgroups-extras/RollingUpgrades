package org.jgroups.protocols.upgrade;

import com.google.protobuf.ByteString;
import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.annotations.Property;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.common.GrpcClient;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.stack.Protocol;
import org.jgroups.upgrade_server.Request;
import org.jgroups.upgrade_server.RpcHeader;
import org.jgroups.upgrade_server.View;
import org.jgroups.upgrade_server.ViewId;
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
public class UPGRADE extends Protocol {

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

    @ManagedAttribute(description="The cluster this member is a part of")
    protected String                                    cluster;

    protected GrpcClient                                client=new GrpcClient();
    protected static final short                        REQ_ID=ClassConfigurator.getProtocolId(RequestCorrelator.class);


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

    public void init() throws Exception {
        super.init();
        client.setServerAddress(server_address).setServerPort(server_port).setServerCert(server_cert)
          .addViewHandler(this::handleView).addMessageHandler(this::handleMessage)
          .setReconnectionFunction(this::connect)
          .setReconnectInterval(reconnect_interval)
          .start();
    }

    public void start() throws Exception {
        super.start();
    }

    public void stop() {
        super.stop();
        client.stop();
    }

    public Object down(Event evt) {
        switch(evt.type()) {
            case Event.MSG:
                if(!active)
                    return down_prot.down(evt);
                // else send to UpgradeServer
                Message msg=(Message)evt.getArg();
                if(msg.getSrc() == null)
                    msg.setSrc(local_addr);
                try {
                    org.jgroups.upgrade_server.Message m=jgroupsMessageToProtobufMessage(cluster, msg);
                    Request req=Request.newBuilder().setMessage(m).build();
                    client.send(req);
                }
                catch(Exception e) {
                    log.error("%s: failed sending message: %s", local_addr, e);
                }
                return null;
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
        switch(evt.type()) {
            case Event.VIEW_CHANGE:
                local_view=evt.arg();
                if(active)
                    return null;
                break;
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


    protected void handleView(View view) {
        org.jgroups.View jg_view=protobufViewToJGroupsView(view);
        global_view=jg_view;
        up_prot.up(new Event(Event.VIEW_CHANGE, jg_view));

    }

    protected void handleMessage(org.jgroups.upgrade_server.Message m) {
        Message msg=protobufMessageToJGroupsMessage(m);
        up_prot.up(new Event(Event.MSG, msg));
    }

    protected static org.jgroups.upgrade_server.Address jgroupsAddressToProtobufAddress(Address jgroups_addr) {
        if(jgroups_addr == null)
            return org.jgroups.upgrade_server.Address.newBuilder().build();
        if(!(jgroups_addr instanceof org.jgroups.util.UUID))
            throw new IllegalArgumentException(String.format("JGroups address has to be of type UUID but is %s",
                                                             jgroups_addr.getClass().getSimpleName()));
        UUID uuid=(UUID)jgroups_addr;
        String name=UUID.get(jgroups_addr);

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
            UUID.add(uuid, logical_name);
        return uuid;
    }

    protected static org.jgroups.upgrade_server.Message jgroupsMessageToProtobufMessage(String cluster, Message jgroups_msg) {
        if(jgroups_msg == null)
            return null;
        Address destination=jgroups_msg.getDest(), sender=jgroups_msg.getSrc();
        byte[] payload=jgroups_msg.getBuffer();
        RequestCorrelator.Header hdr=(RequestCorrelator.Header)jgroups_msg.getHeader(REQ_ID);

        org.jgroups.upgrade_server.Message.Builder msg_builder=org.jgroups.upgrade_server.Message.newBuilder()
          .setClusterName(cluster);
        if(destination != null)
            msg_builder.setDestination(jgroupsAddressToProtobufAddress(destination));
        if(sender != null)
            msg_builder.setSender(jgroupsAddressToProtobufAddress(sender));
        if(payload != null)
            msg_builder.setPayload(ByteString.copyFrom(payload));
        if(hdr != null) {
            RpcHeader pbuf_hdr=jgroupsReqHeaderToProtobufRpcHeader(hdr);
            msg_builder.setRpcHeader(pbuf_hdr);
        }
        return msg_builder.build();
    }

    protected static Message protobufMessageToJGroupsMessage(org.jgroups.upgrade_server.Message msg) {
        Message jgroups_mgs=new Message();
        if(msg.hasDestination())
            jgroups_mgs.setDest(protobufAddressToJGroupsAddress(msg.getDestination()));
        if(msg.hasSender())
            jgroups_mgs.setSrc(protobufAddressToJGroupsAddress(msg.getSender()));
        ByteString payload=msg.getPayload();
        if(!payload.isEmpty())
            jgroups_mgs.setBuffer(payload.toByteArray());
        if(msg.hasRpcHeader()) {
            RequestCorrelator.Header hdr=protobufRpcHeaderToJGroupsReqHeader(msg.getRpcHeader());
            jgroups_mgs.putHeader(REQ_ID, hdr);
        }
        return jgroups_mgs;
    }

    protected static org.jgroups.View protobufViewToJGroupsView(View v) {
        ViewId pbuf_vid=v.getViewId();
        List<org.jgroups.upgrade_server.Address> pbuf_mbrs=v.getMemberList();
        org.jgroups.ViewId jg_vid=new org.jgroups.ViewId(protobufAddressToJGroupsAddress(pbuf_vid.getCreator()),
                                                         pbuf_vid.getId());
        List<Address> members=new ArrayList<>();
        pbuf_mbrs.stream().map(UPGRADE::protobufAddressToJGroupsAddress).forEach(members::add);
        return new org.jgroups.View(jg_vid, members);
    }

    protected static RpcHeader jgroupsReqHeaderToProtobufRpcHeader(RequestCorrelator.Header hdr) {
        RpcHeader.Builder rpcHeader = RpcHeader.newBuilder().setType(hdr.type).setRequestId(hdr.req_id).setCorrId(hdr.corrId);
        if (hdr instanceof RequestCorrelator.MultiDestinationHeader) {
            RequestCorrelator.MultiDestinationHeader mdhdr = (RequestCorrelator.MultiDestinationHeader) hdr;

            Address[] exclusions = mdhdr.exclusion_list;
            if (exclusions != null && exclusions.length > 0) {
                rpcHeader.addAllExclusionList(Arrays.stream(exclusions).map(UPGRADE::jgroupsAddressToProtobufAddress).collect(Collectors.toList()));
            }
        }

        return rpcHeader.build();
    }

    protected static RequestCorrelator.Header protobufRpcHeaderToJGroupsReqHeader(RpcHeader hdr) {
        byte type=(byte)hdr.getType();
        long request_id=hdr.getRequestId();
        short corr_id=(short)hdr.getCorrId();
        return (RequestCorrelator.Header)new RequestCorrelator.Header(type, request_id, corr_id).setProtId(REQ_ID);
    }


}
