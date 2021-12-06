package org.jgroups.demos.relay;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.protocols.relay.RELAY2;
import org.jgroups.protocols.relay.RouteStatusListener;
import org.jgroups.util.Util;

/** Demos RELAY. Create 2 *separate* clusters with RELAY as top protocol. Each RELAY has bridge_props="tcp.xml" (tcp.xml
 * needs to be present). Then start 2 instances in the first cluster and 2 instances in the second cluster. They should
 * find each other, and typing in a window should send the text to everyone, plus we should get 4 responses.
 * @author Bela Ban
 */
public abstract class RelayDemoBase {
    protected static final String SITE_MASTERS="site-masters";

    protected JChannel ch;
    protected RELAY2   relay;


    public void receive(Message msg) {
        Address sender=msg.getSrc();
        System.out.println("<< " + msg.getObject() + " from " + sender);
        Address dst=msg.getDest();
        if(dst == null) {
            Message rsp=createMessage(msg.getSrc(), "response");
            try {
                ch.send(rsp);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public abstract Message createMessage(Address dest, String payload);

    public void viewAccepted(View new_view) {
        System.out.println(print(new_view));
    }

    protected abstract void init(JChannel ch);

    protected void start(String props, String name, boolean print_route_status, boolean nohup) throws Exception {
        ch=new JChannel(props);
        init(ch);
        if(name != null)
            ch.setName(name);
        relay=ch.getProtocolStack().findProtocol(RELAY2.class);
        if(relay == null)
            throw new IllegalStateException(String.format("Protocol %s not found", RELAY2.class.getSimpleName()));
        if(print_route_status) {
            relay.setRouteStatusListener(new RouteStatusListener() {
                public void sitesUp(String... sites) {
                    System.out.printf("-- %s: site(s) %s came up\n",
                                      ch.getAddress(), String.join(", ", sites));
                }

                public void sitesDown(String... sites) {
                    System.out.printf("-- %s: site(s) %s went down\n",
                                      ch.getAddress(), String.join(", ", sites));
                }
            });
        }
        ch.connect("RelayDemo");
        if(!nohup) {
            eventLoop(ch);
            Util.close(ch);
        }
    }

    protected void eventLoop(JChannel ch) {
        for(;;) {
            try {
                String line=Util.readStringFromStdin(": ");
                if(process(line)) // see if we have a command, otherwise pass down
                    continue;
                ch.send(null, line);
            }
            catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }

    protected boolean process(String line) {
        if(line == null || line.isEmpty())
            return true;
        if(line.equalsIgnoreCase(SITE_MASTERS) || line.equalsIgnoreCase("sm")) {
            System.out.printf("site masters in %s: %s\n", relay.site(), relay.siteMasters());
            return true;
        }
        if(line.equalsIgnoreCase("help")) {
            help();
            return true;
        }
        if(line.equalsIgnoreCase("mbrs")) {
            System.out.printf("%s: local members: %s\n", relay.getLocalAddress(), relay.members());
            return true;
        }
        if(line.equalsIgnoreCase("sites")) {
            System.out.printf("configured sites: %s\n", relay.getSites());
            return true;
        }
        if(line.equalsIgnoreCase("topo")) {
            System.out.printf("\n%s\n", printTopology());
            return true;
        }

        return false;
    }

    protected static void help() {
        System.out.println("\ncommands:" +
                             "\nhelp" +
                             "\nmbrs: prints the local members" +
                             "\nsite-masters (sm): prints the site masters of this site" +
                             "\nsites: prints the configured sites" +
                             "\ntopo: prints the topology (site masters and local members of all sites)\n");
    }

    protected String printTopology() {
        return relay.printTopology(true);
    }

    protected static String print(View view) {
        StringBuilder sb=new StringBuilder();
        boolean first=true;
        sb.append(view.getClass().getSimpleName() + ": ").append(view.getViewId()).append(": ");
        for(Address mbr: view.getMembers()) {
            if(first)
                first=false;
            else
                sb.append(", ");
            sb.append(mbr);
        }
        return sb.toString();
    }
}
