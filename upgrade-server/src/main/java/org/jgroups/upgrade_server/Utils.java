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
}
