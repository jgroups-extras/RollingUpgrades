package org.jgroups.demos;

import org.jgroups.*;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.util.*;

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
        ByteArrayDataInputStream in=new ByteArrayDataInputStream(msg.getArray(), msg.getOffset(), msg.getLength());
        String message=in.readUTF();
        int count=in.readInt();
        System.out.printf("received %s, returning %d\n", message, count+1);
        return count+1;
    }


    protected void start(String props, String name) throws Exception {
        ch=new JChannel(props);
        ch.setName(name);
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
            if(str == null || str.isEmpty() || "exit".equals(str))
                break;

            ByteArrayDataOutputStream out=new ByteArrayDataOutputStream();
            out.writeUTF(String.format("%s from %s", str, ch.getAddress()));
            out.writeInt(count++);
            Message msg=new BytesMessage(null, out.getBuffer());
            RspList<Integer> rsps=disp.castMessage(null, msg, RequestOptions.SYNC());
            System.out.printf("rsps: %s\n", rsps);

            /*for(Rsp<Integer> rsp: rsps) {
                Object obj=rsp.getValue();
                if(obj instanceof byte[]) {
                    byte[] buf=(byte[])obj;
                    buf[0]=11;
                    Object object=Util.objectFromByteBuffer(buf);
                    System.out.printf("value: %s\n", object);
                }
                // Integer val=rsp.getValue();
            }
*/
        }
        Util.close(disp, ch);
    }

    protected static void help() {
        System.out.printf("%s [-help] [-props config] [-name name]\n",
                          MessageDispatcherTest.class.getSimpleName());
    }


}
