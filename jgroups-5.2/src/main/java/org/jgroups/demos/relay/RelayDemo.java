package org.jgroups.demos.relay;

import org.jgroups.*;

/** Demos RELAY. Create 2 *separate* clusters with RELAY as top protocol. Each RELAY has bridge_props="tcp.xml" (tcp.xml
 * needs to be present). Then start 2 instances in the first cluster and 2 instances in the second cluster. They should
 * find each other, and typing in a window should send the text to everyone, plus we should get 4 responses.
 * @author Bela Ban
 */
public class RelayDemo extends RelayDemoBase implements Receiver {

    public static void main(String[] args) throws Exception {
        String props="udp.xml";
        String name=null;
        boolean print_route_status=false, nohup=false;

        for(int i=0; i < args.length; i++) {
            if(args[i].equals("-props")) {
                props=args[++i];
                continue;
            }
            if(args[i].equals("-name")) {
                name=args[++i];
                continue;
            }
            if(args[i].equals("-print_route_status")) {
                print_route_status=Boolean.parseBoolean(args[++i]);
                continue;
            }
            if(args[i].equals("-nohup")) {
                nohup=true;
                continue;
            }
            System.out.println("RelayDemo [-props props] [-name name] [-print_route_status false|true] [-nohup]");
            return;
        }
        RelayDemo demo=new RelayDemo();
        demo.start(props, name, print_route_status, nohup);
    }


    public Message createMessage(Address dest, String payload) {
        return new BytesMessage(dest, payload);
    }

    @Override
    protected void init(JChannel ch) {
        ch.setReceiver(this);
    }

    @Override
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


}
