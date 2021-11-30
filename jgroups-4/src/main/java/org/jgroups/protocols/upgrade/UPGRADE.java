package org.jgroups.protocols.upgrade;

import com.google.protobuf.ByteString;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.annotations.MBean;
import org.jgroups.base.UpgradeBase;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.common.ByteArray;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.upgrade_server.Headers;
import org.jgroups.upgrade_server.RelayHeader;
import org.jgroups.upgrade_server.Request;
import org.jgroups.upgrade_server.RpcHeader;
import org.jgroups.util.Buffer;
import org.jgroups.util.ByteArrayDataInputStream;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.Streamable;

/**
 * Relays application messages to the UpgradeServer (when active). Should be the top protocol in a stack.
 * @author Bela Ban
 * @since  1.0
 * @todo: implement support for addresses other than UUIDs
 * @todo: implement reconnection to server (server went down and then up again)
 */
@MBean(description="Protocol that redirects all messages to/from an UpgradeServer")
public class UPGRADE extends UpgradeBase {


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


    protected org.jgroups.upgrade_server.Message jgroupsMessageToProtobufMessage(String cluster, Message jg_msg) throws Exception {
        if(jg_msg == null)
            return null;
        Address destination=jg_msg.getDest(), sender=jg_msg.getSrc();
        org.jgroups.upgrade_server.Message.Builder builder=org.jgroups.upgrade_server.Message.newBuilder()
          .setClusterName(cluster);
        if(destination != null)
            builder.setDestination(jgroupsAddressToProtobufAddress(destination));
        if(sender != null)
            builder.setSender(jgroupsAddressToProtobufAddress(sender));
        builder.setFlags(jg_msg.getFlags());

        boolean is_rsp=false;
        RequestCorrelator.Header hdr=jg_msg.getHeader(REQ_ID);
        RELAY2.Relay2Header relay_hdr=jg_msg.getHeader(RELAY2_ID);
        Headers.Builder b=Headers.newBuilder();
        if(hdr != null) {
            RpcHeader pbuf_hdr=jgroupsReqHeaderToProtobufRpcHeader(hdr);
            b.setRpcHdr(pbuf_hdr);
            is_rsp=hdr.type == RequestCorrelator.Header.RSP || hdr.type == RequestCorrelator.Header.EXC_RSP;
        }
        if(relay_hdr!= null) {
            RelayHeader h=jgroupsRelayHeaderToProtobuf(relay_hdr);
            b.setRelayHdr(h);
        }
        builder.setHeaders(b.build());

        org.jgroups.common.ByteArray payload;
        if (marshaller != null) {
            if(is_rsp) {
                Object obj=jg_msg.getObject();
                payload=marshaller.objectToBuffer(obj);
            } else if (rpcs) {
                Streamable mc=methodCallFromBuffer(jg_msg.buffer(), jg_msg.getOffset(), jg_msg.getLength(), null);
                payload=marshaller.objectToBuffer(mc);
            } else {
                payload = payloadFromJGroupsMessage(jg_msg);
            }
        } else {
            payload = payloadFromJGroupsMessage(jg_msg);
        }
        if(payload != null)
            builder.setPayload(ByteString.copyFrom(payload.getBytes(), payload.getOffset(), payload.getLength()));
        return builder.build();
    }


    protected Message protobufMessageToJGroupsMessage(org.jgroups.upgrade_server.Message msg) throws Exception {
        ByteString payload=msg.getPayload();
        Message jg_msg=new Message();
        if(msg.hasDestination())
            jg_msg.setDest(protobufAddressToJGroupsAddress(msg.getDestination()));
        if(msg.hasSender())
            jg_msg.setSrc(protobufAddressToJGroupsAddress(msg.getSender()));
        jg_msg.setFlag(jg_msg.getFlags());
        boolean is_rsp=false;
        if(msg.hasHeaders()) {
            Headers hdrs=msg.getHeaders();
            if(hdrs.hasRpcHdr()) {
                RpcHeader pb_hdr=hdrs.getRpcHdr();
                RequestCorrelator.Header hdr=protobufRpcHeaderToJGroupsReqHeader(pb_hdr);
                jg_msg.putHeader(REQ_ID, hdr);
                is_rsp=hdr.type == RequestCorrelator.Header.RSP || hdr.type == RequestCorrelator.Header.EXC_RSP;
            }
            if(hdrs.hasRelayHdr()) {
                RelayHeader pbuf_hdr=hdrs.getRelayHdr();
                RELAY2.Relay2Header relay_hdr=protobufRelayHeaderToJGroups(pbuf_hdr);
                jg_msg.putHeader(RELAY2_ID, relay_hdr);
            }
        }
        if  (payload.isEmpty()) {
            return jg_msg;
        }

        byte[] tmp = payload.toByteArray();
        if (marshaller != null) {
            if (is_rsp) {
                Object obj = marshaller.objectFromBuffer(tmp, 0, tmp.length);
                jg_msg.setObject(obj);
            } else if (rpcs) {
                org.jgroups.blocks.MethodCall obj = (org.jgroups.blocks.MethodCall) marshaller.objectFromBuffer(tmp, 0, tmp.length);
                Buffer buf = methodCallToBuffer(obj, null);
                jg_msg.setBuffer(buf);
            } else {
                jg_msg.setBuffer(tmp);
            }
        } else {
            jg_msg.setBuffer(tmp);
        }

        return jg_msg;
    }

    protected static Buffer methodCallToBuffer(final org.jgroups.blocks.MethodCall call, org.jgroups.blocks.Marshaller marshaller) throws Exception {
        Object[] args=call.args();

        int estimated_size=64;
        if(args != null)
            for(Object arg: args)
                estimated_size+=marshaller != null? marshaller.estimatedSize(arg) : (arg == null? 2 : 50);

        ByteArrayDataOutputStream out=new ByteArrayDataOutputStream(estimated_size, true);
        call.writeTo(out, marshaller);
        return out.getBuffer();
    }

    protected static org.jgroups.blocks.MethodCall methodCallFromBuffer(final byte[] buf, int offset, int length, org.jgroups.blocks.Marshaller marshaller) throws Exception {
        ByteArrayDataInputStream in=new ByteArrayDataInputStream(buf, offset, length);
        org.jgroups.blocks.MethodCall call=new org.jgroups.blocks.MethodCall();
        call.readFrom(in, marshaller);
        return call;
    }

    private static org.jgroups.common.ByteArray payloadFromJGroupsMessage(Message jg_msg) {
        byte[] raw_buf = jg_msg.getRawBuffer();
        return raw_buf == null ? null : new ByteArray(jg_msg.getRawBuffer(), jg_msg.getOffset(), jg_msg.getLength());
    }

}
