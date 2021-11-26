package org.jgroups.base;

import org.jgroups.Address;
import org.jgroups.annotations.MBean;
import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.Property;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.common.GrpcClient;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.stack.Protocol;

/**
 * Relays application messages to the UpgradeServer (when active). Should be the top protocol in a stack.
 * @author Bela Ban
 * @since  1.0
 * @todo: implement support for addresses other than UUIDs
 * @todo: implement reconnection to server (server went down and then up again)
 */
@MBean(description="Protocol that redirects all messages to/from an UpgradeServer")
public class UpgradeBase extends Protocol {
    @Property(description="Whether or not to perform relaying via the UpgradeServer",writable=false)
    protected volatile boolean                          active;

    @Property(description="The IP address (or symbolic name) of the UpgradeServer")
    protected String                                    server_address="localhost";

    @Property(description="The port on which the UpgradeServer is listening")
    protected int                                       server_port=50051;

    @Property(description="The filename of the UpgradeServer's certificate (with the server's public key). " +
      "If non-null and non-empty, the client will use an encrypted connection to the server")
    protected String                                    server_cert;

    @Property(description="Time in ms between trying to reconnect to UpgradeServer (while disconnected)")
    protected long                                      reconnect_interval=3000;

    @ManagedAttribute(description="The local address")
    protected Address local_addr;

    @ManagedAttribute(description="Shows the local view")
    protected org.jgroups.View                          local_view;

    @ManagedAttribute(description="The global view (provided by the UpgradeServer)")
    protected org.jgroups.View                          global_view;

    @ManagedAttribute(description="The cluster this member is a part of")
    protected String                                    cluster;

    protected GrpcClient                                client=new GrpcClient();
    protected static final short                        REQ_ID=ClassConfigurator.getProtocolId(RequestCorrelator.class);
}
