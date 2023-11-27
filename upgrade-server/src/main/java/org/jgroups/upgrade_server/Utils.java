package org.jgroups.upgrade_server;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * @author Bela Ban
 * @since x.y
 */
public class Utils {
    public static String print(View v) {
        return String.format("[%s]", v.getMemberList().stream().map(Address::getName).collect(Collectors.joining(", ")));
    }

    public static String print(Collection<Address> addresses) {
        return String.format("[%s]", addresses.stream().map(Address::getName).collect(Collectors.joining(", ")));
    }

    public static String printView(long view_id, Collection<Address> addrs) {
        if(addrs == null || addrs.isEmpty())
            return "null";
        Address coord=addrs.iterator().next();
        return String.format("[%s|%d] (%d) %s", coord.getName(), view_id, addrs.size(), print(addrs));
    }
}
