package org.jgroups.protocols.relay3;

import org.jgroups.annotations.MBean;
import org.jgroups.stack.Protocol;

/**
 * @author Bela Ban
 * @since  1.0
 */
@MBean(description="Protocol that redirects all messages to/from a RelayServer")
public class RELAY3 extends Protocol {
}
