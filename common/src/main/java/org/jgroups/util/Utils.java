package org.jgroups.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Misc utility methods, e.g. for marshalling and unmarshalling
 * @author Bela Ban
 * @since x.y
 */
public class Utils {
    public static final byte      TYPE_NULL      =  0;
    public static final byte      TYPE_INT       =  1;
    public static final byte      TYPE_INTEGER   =  2;

    public static final byte      TYPE_EXCEPTION = 10;

    public static final ByteArray NULL_BUFFER=new ByteArray(new byte[]{TYPE_NULL});


    public static ByteArray intToBuffer(int n) {
       return _intToBuffer(n, TYPE_INT);
    }

    public static ByteArray intToBuffer(Integer n) {
        return _intToBuffer(n, TYPE_INTEGER);
    }



    public static void writeInt(int num, byte[] buf, int offset) {
        buf[offset+3]=(byte)num;
        buf[offset+2]=(byte)(num >>>  8);
        buf[offset+1]=(byte)(num >>> 16);
        buf[offset]=(byte)(num >>> 24);
    }


    protected static ByteArray _intToBuffer(int n, byte type) {
        byte[] ret=new byte[Byte.BYTES + Integer.BYTES];
        ret[0]=type;
        writeInt(n, ret, 1);
        return new ByteArray(ret);
    }

    public static int readInt(byte[] buf, int offset) {
        return ((buf[offset+3] & 0xFF)) +
          ((buf[offset+2] & 0xFF) <<  8) +
          ((buf[offset+1] & 0xFF) << 16) +
          ((buf[offset]) << 24);
    }

    /** A JGroups version-independent marshaller for 3.6 and 4.x. Currently only supports a limited number of types,
     * will be replaced by protobuf soon */
    public static class Marshaller {

        // 3.6 Marshaller
        public static ByteArray objectToBuffer(Object obj) throws Exception {
            if(obj == null)
                return NULL_BUFFER;
            if(obj instanceof Integer)
                return intToBuffer((Integer)obj);

            throw new UnsupportedOperationException(String.format("handling of type %s is not supported", obj.getClass()));
        }

        // 3.6 Marshaller
        public static Object objectFromBuffer(byte[] buf, int offset, int length) throws Exception {
            byte type;
            if(length == 0 || (type=buf[offset]) == TYPE_NULL)
                return null;
            switch(type) {
                case TYPE_INT:
                case TYPE_INTEGER:
                    return readInt(buf, offset+1);
                default:
                    throw new UnsupportedOperationException(String.format("type %d no supported", type));
            }
        }

        // 4.x Marshaller
        public static void objectToStream(Object obj, DataOutput out) throws IOException {
            if(obj == null) {
                out.writeByte(TYPE_NULL);
                return;
            }
            if(obj instanceof Integer) {
                out.writeByte(TYPE_INTEGER);
                out.writeInt((Integer)obj);
                return;
            }
            throw new UnsupportedOperationException(String.format("handling of type %s is not supported", obj.getClass()));
        }

        // 4.x Marshaller
        public static Object objectFromStream(DataInput in) throws IOException, ClassNotFoundException {
            byte type=in.readByte();
            switch(type) {
                case TYPE_NULL:
                    return null;
                case TYPE_INT:
                case TYPE_INTEGER:
                    return in.readInt();
            }

            throw new UnsupportedOperationException(String.format("type %d not supported", type));
        }
    }
}
