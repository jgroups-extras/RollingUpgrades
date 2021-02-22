package org.jgroups.demos;

import org.jgroups.*;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.common.Utils;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;

import java.util.stream.Collectors;

/**
 * Tests RPCs across different JGroups versions
 * @author Bela Ban
 * @since  2.0.0
 */
public class MessageDispatcherTest implements RequestHandler {
    protected JChannel          ch;
    protected MessageDispatcher disp;

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
        DemoRequest req=Utils.pbFromByteArray(msg.getRawBuffer(), msg.getOffset(), msg.getLength());
        int count=req.getCount()+1;
        System.out.printf("received %s, returning %d\n", req.getName(), count);
        return DemoResponse.newBuilder().setCount(count).build();
    }


    protected void start(String props, String name) throws Exception {
        ch=new JChannel(props);
        ch.setName(name);
        disp=new MessageDispatcher(ch, this);
        disp.correlator().setMarshaller(new DemoMarshaller());

        disp.setMembershipListener(new MembershipListener() {
            @Override public void viewAccepted(View new_view) {
                System.out.printf("-- new view: %s\n", new_view);
            }

            @Override public void suspect(Address suspected_mbr) {}
            @Override public void block() {}
            @Override public void unblock() {}
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
            byte[] buf=Utils.pbToByteArray(req);
            Message msg=new Message(null, buf);
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




}
