package org.jgroups.demos;

import org.jgroups.*;
import org.jgroups.blocks.Marshaller;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.common.Utils;
import org.jgroups.util.ByteArrayDataInputStream;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

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
        ByteArrayDataInputStream in=new ByteArrayDataInputStream(msg.getRawBuffer(), msg.getOffset(), msg.getLength());
        String message=in.readUTF();
        int count=in.readInt();
        System.out.printf("received %s, returning %d\n", message, count+1);
        return count+1;
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
            if(str == null || str.isEmpty() || "exit".equals(str))
                break;

            ByteArrayDataOutputStream out=new ByteArrayDataOutputStream();
            out.writeUTF(String.format("%s from %s", str, ch.getAddress()));
            out.writeInt(count++);
            RspList<Integer> rsps=disp.castMessage(null, out.getBuffer(), RequestOptions.SYNC());
            System.out.printf("rsps: %s\n", rsps);
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
            Utils.Marshaller.objectToStream(obj, out);
        }

        @Override
        public Object objectFromStream(DataInput in) throws IOException, ClassNotFoundException {
            return Utils.Marshaller.objectFromStream(in);
        }
    }

}
