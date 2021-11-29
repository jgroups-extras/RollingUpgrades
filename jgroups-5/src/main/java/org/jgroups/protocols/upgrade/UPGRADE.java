package org.jgroups.protocols.upgrade;

import com.google.protobuf.ByteString;
import org.jgroups.Address;
import org.jgroups.BytesMessage;
import org.jgroups.Message;
import org.jgroups.base.UpgradeBase;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.common.ByteArray;
import org.jgroups.upgrade_server.Metadata;
import org.jgroups.upgrade_server.Request;
import org.jgroups.upgrade_server.RpcHeader;

/**
 * Relays application messages to the UpgradeServer (when active). Should be the top protocol in a stack.
 * @author Bela Ban
 * @since  1.0
 * @todo: implement support for addresses other than UUIDs
 * @todo: implement reconnection to server (server went down and then up again)
 */
public class UPGRADE extends UpgradeBase {

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
        Address destination=jg_msg.getDest(), sender=jg_msg.getSrc();
        RequestCorrelator.Header hdr=jg_msg.getHeader(REQ_ID);
        org.jgroups.upgrade_server.Message.Builder builder=org.jgroups.upgrade_server.Message.newBuilder()
          .setClusterName(cluster);
        if(destination != null)
            builder.setDestination(jgroupsAddressToProtobufAddress(destination));
        if(sender != null)
            builder.setSender(jgroupsAddressToProtobufAddress(sender));
        builder.setFlags(jg_msg.getFlags());
        Metadata md=Metadata.newBuilder().setMsgType(jg_msg.getType()).build();
        builder.setMetaData(md);

        boolean is_rsp=false;
        if(hdr != null) {
            RpcHeader pbuf_hdr=jgroupsReqHeaderToProtobufRpcHeader(hdr);
            builder.setRpcHeader(pbuf_hdr);
            is_rsp=hdr.type == RequestCorrelator.Header.RSP || hdr.type == RequestCorrelator.Header.EXC_RSP;
        }
        org.jgroups.common.ByteArray payload;
        if((is_rsp || rpcs) && marshaller != null) {
            Object obj=jg_msg.getPayload();
            payload=marshaller.objectToBuffer(obj);
        }
        else
            payload=new ByteArray(jg_msg.getArray(), jg_msg.getOffset(), jg_msg.getLength());
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
        if(msg.hasRpcHeader()) {
            RpcHeader pb_hdr=msg.getRpcHeader();
            RequestCorrelator.Header hdr=protobufRpcHeaderToJGroupsReqHeader(pb_hdr);
            jg_msg.putHeader(REQ_ID, hdr);
            is_rsp=hdr.type == RequestCorrelator.Header.RSP || hdr.type == RequestCorrelator.Header.EXC_RSP;
        }
        if(!payload.isEmpty()) {
            byte[] tmp=payload.toByteArray();
            if((is_rsp || rpcs) && marshaller != null) {
                Object obj=marshaller.objectFromBuffer(tmp, 0, tmp.length);
                jg_msg.setPayload(obj);
            }
            else
                jg_msg.setArray(tmp);
        }
        return jg_msg;
    }





}
