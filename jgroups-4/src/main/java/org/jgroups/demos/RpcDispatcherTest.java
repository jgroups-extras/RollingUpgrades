package org.jgroups.demos;

import org.jgroups.JChannel;
import org.jgroups.MembershipListener;
import org.jgroups.View;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.upgrade.UPGRADE;
import org.jgroups.util.Rsp;
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
    protected JChannel              ch;
    protected RpcDispatcher         disp;
    protected static final Method[] METHODS=new Method[1];
    protected static final short    HELLO=0;
    protected static final Log      log=LogFactory.getLog(RpcDispatcherTest.class);

    static {
        try {
            METHODS[HELLO]=RpcDispatcherTest.class.getMethod("hello", String.class, int.class);
        }
        catch(Throwable t) {
            throw new RuntimeException(t);
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

    public static int hello(String message, int count) {
        System.out.printf("received %s, returning %d\n", message, count+1);
        return count+1;
    }

    protected void start(String props, String name) throws Exception {
        ch=new JChannel(props).setName(name);
        disp=new RpcDispatcher(ch, this)
          .setMethodLookup(id -> METHODS[0]);
        setMarshaller(ch);
        useRpcs(ch, true);

        disp.setMembershipListener(new MembershipListener() {
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
            MethodCall call=new MethodCall(HELLO, str + " from " + ch.getAddress(), count++);
            RspList<Integer> rsps=disp.callRemoteMethods(null, call, RequestOptions.SYNC());
            System.out.printf("\nrsps:\n%s\n", rsps.entrySet().stream()
              .map(e -> {
                  Rsp<Integer> rsp=e.getValue();
                  String received=rsp.wasReceived()? "" : "(not received)", suspected=rsp.wasSuspected()? "(suspected)" : "" ;
                  return String.format("  %s: %d %s %s", e.getKey(), rsp.getValue(), received, suspected);
              }).collect(Collectors.joining("\n")));
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


    protected static void useRpcs(JChannel ch, boolean v) {
        UPGRADE upgrade=ch.getProtocolStack().findProtocol(UPGRADE.class);
        if(upgrade != null)
            upgrade.setRpcs(v);
        else
            log.warn("%s not found", UPGRADE.class.getSimpleName());
    }
}
