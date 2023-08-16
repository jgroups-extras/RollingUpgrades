package org.jgroups.protocols.upgrade;

import com.google.protobuf.ByteString;
import org.jgroups.BytesMessage;
import org.jgroups.Message;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.common.ByteArray;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.upgrade_server.*;
import org.jgroups.util.Util;

/**
 * Relays application messages to the UpgradeServer (when active). Should be the top protocol in a stack.
 * @author Bela Ban
 * @since  1.0
 */
public class UPGRADE extends UpgradeBase5 {

    public Object down(Message msg) { // cannot be moved to parent due to IncompatibleClassChangeError (class->interface)
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

        Metadata md=Metadata.newBuilder().setMsgType(jg_msg.getType()).build();
        org.jgroups.upgrade_server.Message.Builder builder=msgBuilder(cluster, jg_msg.getSrc(), jg_msg.getDest(),
                                                                      jg_msg.getFlags(), md);
        RequestCorrelator.Header hdr=jg_msg.getHeader(REQ_ID);
        RELAY2.Relay2Header relay_hdr=jg_msg.getHeader(RELAY2_ID);
        boolean is_rsp=setHeaders(builder, hdr, relay_hdr);

        org.jgroups.common.ByteArray payload;
        if((is_rsp || rpcs) && marshaller != null) {
            Object obj=jg_msg.getPayload();
            payload=marshaller.objectToBuffer(obj);
        }
        else {
            if(jg_msg.hasArray())
                payload=new ByteArray(jg_msg.getArray(), jg_msg.getOffset(), jg_msg.getLength());
            else {
                org.jgroups.util.ByteArray pl=jg_msg.hasPayload()? Util.objectToBuffer(jg_msg.getObject()) : null;
                payload=pl != null? new ByteArray(pl.getArray(), pl.getOffset(), pl.getLength()) : null;
            }
        }
        if(payload != null)
            builder.setPayload(ByteString.copyFrom(payload.getBytes(), payload.getOffset(), payload.getLength()));
        return builder.build();
    }



    protected Message protobufMessageToJGroupsMessage(org.jgroups.upgrade_server.Message msg) throws Exception {
        ByteString payload=msg.getPayload();
        Message jg_msg=msg.hasMetaData()? getTransport().getMessageFactory().create((short)msg.getMetaData().getMsgType())
          : new BytesMessage();
        if(msg.hasDestination())
            jg_msg.setDest(protobufAddressToJGroupsAddress(msg.getDestination()));
        if(msg.hasSender())
            jg_msg.setSrc(protobufAddressToJGroupsAddress(msg.getSender()));
        jg_msg.setFlag((short)msg.getFlags(), false);
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

        if(!payload.isEmpty()) {
            byte[] tmp=payload.toByteArray();
            if((is_rsp || rpcs) && marshaller != null) {
                Object obj=marshaller.objectFromBuffer(tmp, 0, tmp.length);
                jg_msg.setPayload(obj);
            }
            else {
                if(jg_msg.hasArray())
                    jg_msg.setArray(tmp);
                else {
                    Object pl=Util.objectFromByteBuffer(tmp);
                    jg_msg.setObject(pl);
                }
            }
        }
        return jg_msg;
    }





}
