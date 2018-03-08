package org.jgroups.protocols.relay3;

import org.jgroups.annotations.MBean;
import org.jgroups.annotations.Property;
import org.jgroups.stack.Protocol;

/**
 * @author Bela Ban
 * @since  1.0
 */
@MBean(description="Protocol that redirects all messages to/from a RelayServer")
public class RELAY3 extends Protocol {

    @Property(description="Whether or not to perform relaying via the relay server")
    protected boolean active;

    @Property(description="The IP address (or symbolic name) of the relay server")
    protected String  server_address="localhost";

    @Property(description="The port on which the relay server is listening")
    protected int     server_port=50051;


}
