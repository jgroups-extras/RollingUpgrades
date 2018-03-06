package org.jgroups.relay_server;

import java.util.stream.Collectors;

/**
 * @author Bela Ban
 * @since x.y
 */
public class Utils {
    public static String print(View v) {
        return String.format("[%s]", v.getMemberList().stream().map(Address::getAddress).collect(Collectors.joining(", ")));
    }
}
