package org.jgroups.demos;

import org.jgroups.*;
import org.jgroups.blocks.Marshaller;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.common.InputStreamAdapter;
import org.jgroups.common.OutputStreamAdapter;
import org.jgroups.util.Buffer;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
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
        byte[] buf=msg.getBuffer();
        DemoRequest req=DemoRequest.parseFrom(buf);
        int count=req.getCount()+1;
        System.out.printf("received %s, returning %d\n", req.getName(), count);
        return count;
    }


    protected void start(String props, String name) throws Exception {
        ch=new JChannel(props);
        ch.setName(name);
        disp=new MessageDispatcher(ch, this);
        disp.correlator().setMarshaller(new TestMarshaller());


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
            byte[] buf=req.toByteArray();
            Buffer b=new Buffer(buf, 0, buf.length);
            RspList<DemoResponse> rsps=disp.castMessage(null, b, RequestOptions.SYNC());
            System.out.printf("rsps:\n%s\n", rsps.entrySet().stream()
              .filter(e -> e.getKey() != null && e.getValue() != null)
              .map(e -> String.format("%s: %s", e.getKey(), e.getValue().getValue().getCount()))
              .collect(Collectors.joining("\n")));
        }
        Util.close(disp, ch);
    }

    protected static void help() {
        System.out.printf("%s [-help] [-props config] [-name name]\n",
                          MessageDispatcherTest.class.getSimpleName());
    }



    protected static class TestMarshaller implements Marshaller {

        @Override
        public void objectToStream(Object obj, DataOutput out) throws IOException {
            DemoResponse rsp=DemoResponse.newBuilder().setCount((Integer)obj).build();
            rsp.writeTo(new OutputStreamAdapter(out));
           //  Utils.Marshaller.objectToStream(obj, out);
        }

        @Override
        public Object objectFromStream(DataInput in) throws IOException, ClassNotFoundException {
            // return Utils.Marshaller.objectFromStream(in);
            return DemoResponse.parseFrom(new InputStreamAdapter(in));
        }
    }

}
