package org.jgroups.protocols.upgrade;

import com.google.protobuf.ProtocolStringList;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.*;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.ManagedOperation;
import org.jgroups.annotations.Property;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.common.ConnectionStatus;
import org.jgroups.common.GrpcClient;
import org.jgroups.common.Marshaller;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.SiteMaster;
import org.jgroups.protocols.relay.SiteUUID;
import org.jgroups.stack.Protocol;
import org.jgroups.upgrade_server.ViewId;
import org.jgroups.upgrade_server.*;
import org.jgroups.util.NameCache;

import java.util.*;
import java.util.stream.Collectors;

import static org.jgroups.common.ConnectionStatus.State.connected;
import static org.jgroups.common.ConnectionStatus.State.disconnecting;
import static org.jgroups.protocols.relay.RelayHeader.*;


/**
 * Relays application messages to the UpgradeServer (when active). Should be the top protocol in a stack.
 * @author Bela Ban
 * @since  1.1.1
 * @todo: implement support for addresses other than UUIDs
 * @todo: implement reconnection to server (server went down and then up again)
 */
@MBean(description="Protocol that redirects all messages to/from an UpgradeServer")
public abstract class UpgradeBase5_2 extends Protocol {
    @Property(description="Whether or not to perform relaying via the UpgradeServer", writable=false)
    protected volatile boolean   active;

    @Property(description="The IP address (or symbolic name) of the UpgradeServer")
    protected String             server_address="localhost";

    @Property(description="The port on which the UpgradeServer is listening")
    protected int                server_port=50051;

    @Property(description="The filename of the UpgradeServer's certificate (with the server's public key). " +
      "If non-null and non-empty, the client will use an encrypted connection to the server")
    protected String             server_cert;

    @Property(description="Time in ms between trying to reconnect to UpgradeServer (while disconnected)")
    protected long               reconnect_interval=3000;

    @ManagedAttribute(description="Shows the local view")
    protected View               local_view;

    @ManagedAttribute(description="The global view (provided by the UpgradeServer)")
    protected View               global_view;

    @Property(description="If RPCs are sent over UPGRADE, then we must serialize every request, not just the responses")
    protected boolean            rpcs;

    @ManagedAttribute(description="The cluster this member is a part of")
    protected String             cluster;

    protected GrpcClient         client=new GrpcClient();

    protected Marshaller         marshaller;

    protected static final short REQ_ID=ClassConfigurator.getProtocolId(RequestCorrelator.class);
    protected static final short RELAY2_ID=ClassConfigurator.getProtocolId(RELAY2.class);

    @ManagedAttribute
    public String getMarshaller() {
        return marshaller != null? marshaller.getClass().getSimpleName() : "n/a";
    }

    public Marshaller marshaller() {
        return marshaller;
    }

    public <T extends UpgradeBase5_2> T marshaller(Marshaller m) {
        this.marshaller=m;
        return (T)this;
    }

    public boolean getRpcs() {
        return rpcs;
    }

    public <T extends UpgradeBase5_2> T setRpcs(boolean r) {
        rpcs=r;
        return (T)this;
    }

    @ManagedAttribute(description="True if the reconnector is running")
    public boolean isReconnecting() {
        return client.reconnectorRunning();
    }

    @ManagedAttribute(description="state of the connection")
    public ConnectionStatus state() {return client.state();}

    @ManagedAttribute(description="Whether or not this member is the coordinator")
    public boolean isCoordinator() {
        return Objects.equals(local_addr, local_view != null? local_view.getCreator() : null);
    }

    public void init() throws Exception {
        super.init();
        client.setServerAddress(server_address).setServerPort(server_port).setServerCert(server_cert)
          .addViewHandler(this::handleView).addMessageHandler(this::handleMessage)
          .setViewResponseHandler(this::handleViewResponse)
          .setReconnectionFunction(this::connect).setReconnectInterval(reconnect_interval)
          .start();
    }

    public void stop() {
        client.stop();
    }

    @ManagedOperation(description="Enable forwarding and receiving of messages to/from the UpgradeServer")
    public synchronized void activate() {
        if(!active) {
            registerView();
            active=true;
        }
    }

    @ManagedOperation(description="Disable forwarding and receiving of messages to/from the UpgradeServer")
    public synchronized void deactivate() {
        if(active) {
            state().setState(connected, disconnecting);
            active=false;
            getViewFromServer();
        }
    }


    public Object down(Event evt) {
        switch(evt.type()) {
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
                if(active)
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

    public Object down(Message msg) {
        if(!active)
            return down_prot.down(msg);

        // else send to UpgradeServer
        if(msg.getSrc() == null)
            msg.setSrc(local_addr);
        try {
            org.jgroups.upgrade_server.Message m=jgroupsMessageToProtobufMessage(cluster, msg);
            Request req=Request.newBuilder().setMessage(m).build();
            client.send(req);
        }
        catch(Exception e) {
            throw new RuntimeException(String.format("%s: failed sending message: %s", local_addr, e));
        }
        return null;
    }

    protected void registerView() {
        org.jgroups.upgrade_server.View v=jgroupsViewToProtobufView(local_view);
        org.jgroups.upgrade_server.Address local=jgroupsAddressToProtobufAddress(local_addr);
        log.debug("%s: registering view %s", local_addr, local_view);
        client.registerView(cluster, v, local);
    }

    protected void getViewFromServer() {
        log.debug("%s: getting view for cluster %s", local_addr, cluster);
        client.getViewFromServer(cluster);
    }

    protected void connect() {
        org.jgroups.upgrade_server.Address addr=jgroupsAddressToProtobufAddress(local_addr);
        log.debug("%s: joining cluster %s", local_addr, cluster);
        client.connect(cluster, addr);
    }

    protected void disconnect() {
        org.jgroups.upgrade_server.Address addr=jgroupsAddressToProtobufAddress(local_addr);
        log.debug("%s: leaving cluster %s", local_addr, cluster);
        client.disconnect(cluster, addr);
    }

    protected void handleView(org.jgroups.upgrade_server.View view) {
        View jg_view=protobufViewToJGroupsView(view);
        if(!active) {
            log.warn("%s: global view %s from server is discarded as active == false", local_addr, jg_view);
            return;
        }
        global_view=jg_view;
        log.debug("%s: received new global view %s", local_addr, global_view);
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

    protected void handleViewResponse(org.jgroups.upgrade_server.GetViewResponse rsp) {
        org.jgroups.upgrade_server.View v=rsp.getView();
        View view=protobufViewToJGroupsView(v);

        // Install a MergeView *if* I'm the coordinator of the global view
        if(Objects.equals(local_addr, view.getCreator())) {
            long view_id=Math.max(view.getViewId().getId(), local_view == null? 1 : local_view.getViewId().getId()) +1;
            MergeView mv=new MergeView(view.getCreator(), view_id, view.getMembers(), List.of(view));
            log.debug("%s: I'm the coordinator, installing new local view %s", local_addr, mv);
            GMS gms=stack.findProtocol(GMS.class);
            gms.castViewChangeAndSendJoinRsps(mv, null, mv.getMembers(), null, null);
        }
        else
            log.debug("%s: I'm not coordinator, waiting for new MergeView from global view %s", local_addr, view);
        // disconnect();
        active=false;
    }

    protected static org.jgroups.upgrade_server.Message.Builder msgBuilder(String cluster, Address src, Address dest,
                                                                           short flags, Metadata md) {
        org.jgroups.upgrade_server.Message.Builder builder=org.jgroups.upgrade_server.Message.newBuilder()
          .setClusterName(cluster);
        if(dest !=null)
            builder.setDestination(jgroupsAddressToProtobufAddress(dest));
        if(src != null)
            builder.setSender(jgroupsAddressToProtobufAddress(src));
        if(md != null)
            builder.setMetaData(md);
        return builder.setFlags(flags);
    }

    protected static boolean setHeaders(org.jgroups.upgrade_server.Message.Builder builder,
                                        RequestCorrelator.Header req_hdr,
                                        org.jgroups.protocols.relay.RelayHeader relay_hdr) {
        boolean is_rsp=false;
        Headers.Builder hdr_builder=Headers.newBuilder();
        if(req_hdr != null) {
            RpcHeader pbuf_hdr=jgroupsReqHeaderToProtobufRpcHeader(req_hdr);
            hdr_builder.setRpcHdr(pbuf_hdr);
            is_rsp=req_hdr.type == RequestCorrelator.Header.RSP || req_hdr.type == RequestCorrelator.Header.EXC_RSP;
        }
        if(relay_hdr!= null) {
            RelayHeader h=jgroupsRelayHeaderToProtobuf(relay_hdr);
            hdr_builder.setRelayHdr(h);
        }
        builder.setHeaders(hdr_builder.build());
        return is_rsp;
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
                builder.addAllExclusionList(Arrays.stream(exclusions).map(UpgradeBase5_2::jgroupsAddressToProtobufAddress)
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

    protected static RelayHeader jgroupsRelayHeaderToProtobuf(org.jgroups.protocols.relay.RelayHeader jg_hdr) {
        RelayHeader.Builder rb=RelayHeader.newBuilder();
        switch(jg_hdr.getType()) {
            case DATA: rb.setType(RelayHeader.Type.DATA); break;
            case SITE_UNREACHABLE: rb.setType(RelayHeader.Type.SITE_UNREACHABLE); break;
            case SITES_UP: rb.setType(RelayHeader.Type.SITES_UP); break;
            case SITES_DOWN: rb.setType(RelayHeader.Type.SITES_DOWN); break;
            case TOPO_REQ: rb.setType(RelayHeader.Type.TOPO_REQ); break;
            case TOPO_RSP: rb.setType(RelayHeader.Type.TOPO_RSP); break;
        }

        if(jg_hdr.getFinalDest() != null) {
            org.jgroups.upgrade_server.Address addr=jgroupsAddressToProtobufAddress(jg_hdr.getFinalDest());
            rb.setFinalDest(addr);
        }

        if(jg_hdr.getOriginalSender() != null) {
            org.jgroups.upgrade_server.Address addr=jgroupsAddressToProtobufAddress(jg_hdr.getOriginalSender());
            rb.setOriginalSender(addr);
        }

        Set<String> sites=jg_hdr.getSites();
        if(sites != null && !sites.isEmpty())
            rb.addAllSites(sites);
        return rb.build();
    }

    protected static org.jgroups.protocols.relay.RelayHeader protobufRelayHeaderToJGroups(RelayHeader pbuf_hdr) {
        byte        type=-1;
        Address     final_dest=null, original_sender=null;
        Set<String> sites=null;

        RelayHeader.Type pbuf_type=pbuf_hdr.getType();
        switch(pbuf_type) {
            case DATA: type=DATA;
                break;
            case SITE_UNREACHABLE:
                type=SITE_UNREACHABLE;
                break;
            case HOST_UNREACHABLE:
                // was removed in 5.2.18
                break;
            case SITES_UP:
                type=SITES_UP;
                break;
            case SITES_DOWN:
                type=SITES_DOWN;
                break;
            case TOPO_REQ:
                type=TOPO_REQ;
                break;
            case TOPO_RSP:
                type=TOPO_RSP;
                break;
            case UNRECOGNIZED:
                throw new IllegalArgumentException("type is UNRECOGNIZED");
        }
        if(pbuf_hdr.hasFinalDest())
            final_dest=protobufAddressToJGroupsAddress(pbuf_hdr.getFinalDest());

        if(pbuf_hdr.hasOriginalSender())
            original_sender=protobufAddressToJGroupsAddress(pbuf_hdr.getOriginalSender());
        ProtocolStringList pbuf_sites=pbuf_hdr.getSitesList();
        if(pbuf_sites != null)
            sites=new HashSet<>(pbuf_sites);
        org.jgroups.protocols.relay.RelayHeader hdr=new org.jgroups.protocols.relay.RelayHeader(type, final_dest, original_sender);
        if(sites != null)
            hdr.addToSites(sites);
        return hdr;
    }


    protected static org.jgroups.upgrade_server.Address jgroupsAddressToProtobufAddress(Address jgroups_addr) {
        if(jgroups_addr == null)
            return org.jgroups.upgrade_server.Address.newBuilder().build();
        if(!(jgroups_addr instanceof org.jgroups.util.UUID))
            throw new IllegalArgumentException(String.format("JGroups address has to be of type UUID but is %s",
                                                             jgroups_addr.getClass().getSimpleName()));
        org.jgroups.util.UUID uuid=(org.jgroups.util.UUID)jgroups_addr;
        String name=jgroups_addr instanceof SiteUUID? ((SiteUUID)jgroups_addr).getName() : NameCache.get(jgroups_addr);

        org.jgroups.upgrade_server.Address.Builder addr_builder=org.jgroups.upgrade_server.Address.newBuilder();
        org.jgroups.upgrade_server.UUID pbuf_uuid=org.jgroups.upgrade_server.UUID.newBuilder()
          .setLeastSig(uuid.getLeastSignificantBits()).setMostSig(uuid.getMostSignificantBits()).build();

        if(jgroups_addr instanceof SiteUUID || jgroups_addr instanceof SiteMaster) {
            String site_name=((SiteUUID)jgroups_addr).getSite();
            org.jgroups.upgrade_server.SiteUUID.Builder b=org.jgroups.upgrade_server.SiteUUID.newBuilder().
              setUuid(pbuf_uuid);
            if(site_name != null)
                b.setSiteName(site_name);
            if(jgroups_addr instanceof SiteMaster)
                b.setIsSiteMaster(true);
            addr_builder.setSiteUuid(b.build());
        }
        else {
            addr_builder.setUuid(pbuf_uuid);
        }
        if(name != null)
            addr_builder.setName(name);
        return addr_builder.build();
    }

    protected static Address protobufAddressToJGroupsAddress(org.jgroups.upgrade_server.Address pbuf_addr) {
        if(pbuf_addr == null)
            return null;

        String logical_name=pbuf_addr.getName();
        Address retval=null;
        if(pbuf_addr.hasSiteUuid()) {
            org.jgroups.upgrade_server.SiteUUID pbuf_site_uuid=pbuf_addr.getSiteUuid();
            String site_name=pbuf_site_uuid.getSiteName();
            if(pbuf_site_uuid.getIsSiteMaster())
                retval=new SiteMaster(site_name);
            else {
                long least=pbuf_site_uuid.getUuid().getLeastSig(), most=pbuf_site_uuid.getUuid().getMostSig();
                retval=new SiteUUID(most, least, logical_name, site_name);
            }
        }
        else if(pbuf_addr.hasUuid()) {
            org.jgroups.upgrade_server.UUID pbuf_uuid=pbuf_addr.getUuid();
            retval=new org.jgroups.util.UUID(pbuf_uuid.getMostSig(), pbuf_uuid.getLeastSig());
        }

        if(retval != null && logical_name != null && !logical_name.isEmpty())
            NameCache.add(retval, logical_name);
        return retval;
    }

    protected static org.jgroups.upgrade_server.View jgroupsViewToProtobufView(View v) {
        ViewId view_id=jgroupsViewIdToProtobufViewId(v.getViewId());
        List<org.jgroups.upgrade_server.Address> mbrs=new ArrayList<>(v.size());
        for(Address a: v)
            mbrs.add(jgroupsAddressToProtobufAddress(a));
        return org.jgroups.upgrade_server.View.newBuilder().addAllMember(mbrs).setViewId(view_id).build();
    }

    protected static ViewId jgroupsViewIdToProtobufViewId(org.jgroups.ViewId view_id) {
        org.jgroups.upgrade_server.Address coord=jgroupsAddressToProtobufAddress(view_id.getCreator());
        return ViewId.newBuilder().setCreator(coord).setId(view_id.getId()).build();
    }

    protected static org.jgroups.View protobufViewToJGroupsView(org.jgroups.upgrade_server.View v) {
        org.jgroups.upgrade_server.ViewId pbuf_vid=v.getViewId();
        List<org.jgroups.upgrade_server.Address> pbuf_mbrs=v.getMemberList();
        org.jgroups.ViewId jg_vid=new org.jgroups.ViewId(protobufAddressToJGroupsAddress(pbuf_vid.getCreator()),
                                                         pbuf_vid.getId());
        List<Address> members=new ArrayList<>();
        pbuf_mbrs.stream().map(UpgradeBase5_2::protobufAddressToJGroupsAddress).forEach(members::add);
        return new org.jgroups.View(jg_vid, members);
    }


}
