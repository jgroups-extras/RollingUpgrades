package org.jgroups.demos.relay;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;

/** Demos RELAY. Create 2 *separate* clusters with RELAY as top protocol. Each RELAY has bridge_props="tcp.xml" (tcp.xml
 * needs to be present). Then start 2 instances in the first cluster and 2 instances in the second cluster. They should
 * find each other, and typing in a window should send the text to everyone, plus we should get 4 responses.
 * @author Bela Ban
 */
public class RelayDemo extends RelayDemoBase implements Receiver {

    public Message createMessage(Address dest, String payload) {
        return new Message(dest, payload);
    }

    @Override
       protected void init(JChannel ch) {
           ch.setReceiver(this);
    }
}
