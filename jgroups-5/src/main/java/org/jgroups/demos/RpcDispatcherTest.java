package org.jgroups.demos;

import org.jgroups.JChannel;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.upgrade.UPGRADE;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;

import java.lang.reflect.Method;
import java.util.stream.Collectors;

/**
 * Tests RPCs across different JGroups versions
 * @author Bela Ban
 * @since  2.0.0
 */
public class RpcDispatcherTest {
    protected JChannel            ch;
    protected RpcDispatcher       disp;
    protected static final Log    log=LogFactory.getLog(RpcDispatcherTest.class);
    protected static final Method HANDLE;
    protected static final short  HANDLE_ID=1;

    static {
        try {
            HANDLE=RpcDispatcherTest.class.getMethod("handle", String.class, int.class);
        }
        catch(NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

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
        RpcDispatcherTest test=new RpcDispatcherTest();
        test.start(props, name);
    }

    /** Called by invoker */
    public static int handle(String name, int cnt) {
        int count=cnt++;
        System.out.printf("received %s, returning %d\n", name, count);
        return count;
    }


    protected void start(String props, String name) throws Exception {
        ch=new JChannel(props).setName(name);
        setMarshaller(ch);
        disp=new RpcDispatcher(ch, this).setMethodLookup(id -> HANDLE); // we only have 1 method

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
            String s=String.format("%s from %s", str, ch.getAddress());
            int cnt=count++;
            MethodCall call=new MethodCall(HANDLE_ID, s, cnt);
            RspList<Integer> rsps=disp.callRemoteMethods(null, call, RequestOptions.SYNC());

            System.out.printf("rsps:\n%s\n", rsps.entrySet().stream()
              .filter(e -> e.getKey() != null && e.getValue() != null)
              .map(e -> String.format("%s: %s", e.getKey(), e.getValue()))
              .collect(Collectors.joining("\n")));
        }
        Util.close(disp, ch);
    }

    protected static void help() {
        System.out.printf("%s [-help] [-props config] [-name name]\n",
                          RpcDispatcherTest.class.getSimpleName());
    }

    protected static void setMarshaller(JChannel ch) {
        UPGRADE upgrade=ch.getProtocolStack().findProtocol(UPGRADE.class);
        if(upgrade != null)
            upgrade.marshaller(new DemoMarshaller());
        else
            log.warn("%s not found", UPGRADE.class.getSimpleName());
    }



}
