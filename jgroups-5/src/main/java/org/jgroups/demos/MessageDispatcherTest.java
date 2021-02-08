package org.jgroups.demos;

import org.jgroups.*;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.common.Marshaller;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.upgrade.UPGRADE;
import org.jgroups.util.ByteArray;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Tests RPCs across different JGroups versions
 * @author Bela Ban
 * @since  2.0.0
 */
public class MessageDispatcherTest implements RequestHandler {
    protected JChannel          ch;
    protected MessageDispatcher disp;
    protected static final Log  log=LogFactory.getLog(MessageDispatcherTest.class);

    public static void main(String[] args) throws Exception {
        String  props="config.xml", name=null;

        for(int i=0; i < args.length; i++) {
            if("-props".equals(args[i])) {
                props=args[++i];
                continue;
            }
            if("-name".equals(args[i])) {
                name=args[++i];
                continue;
            }
            help();
            return;
        }
        MessageDispatcherTest test=new MessageDispatcherTest();
        test.start(props, name);
    }

    @Override
    public Object handle(Message msg) throws Exception {
        byte[] buf=msg.getArray();
        DemoRequest req=DemoRequest.parseFrom(buf);
        int count=req.getCount()+1;
        System.out.printf("received %s, returning %d\n", req.getName(), count);
        return count;
    }


    protected void start(String props, String name) throws Exception {
        ch=new JChannel(props).setName(name);
        setMarshaller(ch);
        disp=new MessageDispatcher(ch, this);

        disp.setReceiver(new Receiver() {
            @Override public void viewAccepted(View new_view) {
                System.out.printf("-- new view: %s\n", new_view);
            }
        });

        ch.connect("rpcs");
        int count=1;
        while(true) {
            String str=Util.readStringFromStdin(": ");
            if(str == null || str.isEmpty())
                str="";
            if("exit".equals(str))
                break;
            DemoRequest req=DemoRequest.newBuilder()
              .setName(String.format("%s from %s", str, ch.getAddress()))
              .setCount(count++)
              .build();
            byte[] buf=req.toByteArray();
            ByteArray b=new ByteArray(buf, 0, buf.length);
            Message msg=new BytesMessage(null, b);
            RspList<DemoResponse> rsps=disp.castMessage(null, msg, RequestOptions.SYNC());
            System.out.printf("rsps:\n%s\n", rsps.entrySet().stream()
              .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue().getValue() != null)
              .map(e -> String.format("%s: %s", e.getKey(), e.getValue().getValue().getCount()))
              .collect(Collectors.joining("\n")));
        }
        Util.close(disp, ch);
    }

    protected static void help() {
        System.out.printf("%s [-help] [-props config] [-name name]\n",
                          MessageDispatcherTest.class.getSimpleName());
    }

    protected static void setMarshaller(JChannel ch) {
        UPGRADE upgrade=ch.getProtocolStack().findProtocol(UPGRADE.class);
        if(upgrade != null)
            upgrade.marshaller(new TestMarshaller());
        else
            log.warn("%s not found", UPGRADE.class.getSimpleName());
    }


    protected static class TestMarshaller implements Marshaller {

        @Override
        public ByteArray objectToBuffer(Object obj) throws Exception {
            DemoResponse rsp=DemoResponse.newBuilder().setCount((Integer)obj).build();
            return new ByteArray(rsp.toByteArray());
        }

        @Override
        public Object objectFromBuffer(byte[] buf, int offset, int length) throws Exception {
            byte[] ba=offset == 0 && length == buf.length? buf : Arrays.copyOfRange(buf, offset, offset+length);
            return DemoResponse.parseFrom(ba);
        }
    }


}
