package org.jgroups.common;

import org.jgroups.util.ByteArray;

/**
 * @author Bela Ban
 * @since  1.1.1
 */
public interface Marshaller {
    /**
     * Marshals the object into a byte[] buffer and returns a Buffer with a ref to the underlying byte[] buffer,
     * offset and length.<br/>
     * <em>
     * Note that the underlying byte[] buffer must not be changed as this would change the buffer of a message which
     * potentially can get retransmitted, and such a retransmission would then carry a ref to a changed byte[] buffer !
     * </em>
     */
    ByteArray objectToBuffer(Object obj) throws Exception;

    Object objectFromBuffer(byte[] buf, int offset, int length) throws Exception;
}

