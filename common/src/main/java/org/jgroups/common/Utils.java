package org.jgroups.common;

import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import org.jgroups.demos.DemoRequest;
import org.jgroups.demos.DemoResponse;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.AccessControlException;

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


    public static void sleep(long time_ms) {
        try {
            Thread.sleep(time_ms);
        }
        catch(InterruptedException ignored) {
        }
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

    public static byte[] pbToByteArray(Object obj) {
        Any any=objToAny(obj);
        return any != null? any.toByteArray() : null;
    }

    public static <T extends Object> T pbFromByteArray(byte[] ba, int offset, int length) throws Exception {
        Any any=Any.parseFrom(ByteBuffer.wrap(ba, offset, length));
        return anyToObject(any);
    }

    public static void pbToStream(Object obj, OutputStream out) throws IOException {
        Any any=objToAny(obj);
        if(any != null)
            any.writeTo(out);
        else
            throw new IllegalArgumentException(String.format("serialization of %s not supported", obj));
    }

    public static <T extends Object> T pbFromStream(InputStream in) throws Exception {
        Any any=Any.parseFrom(in);
        return anyToObject(any);
    }


    public static <T extends Object> T objToAny(Object obj) {
        if(obj instanceof DemoRequest)
            return (T)Any.pack((DemoRequest)obj);
        if(obj instanceof DemoResponse)
            return (T)Any.pack((DemoResponse)obj);
        if(obj instanceof Integer) {
            Int32Value val=Int32Value.newBuilder().setValue((Integer)obj).build();
            return (T)Any.pack(val);
        }
        if(obj instanceof String) {
            StringValue val=StringValue.newBuilder().setValue((String)obj).build();
            return (T)Any.pack(val);
        }
        return null;
    }

    public static <T extends Object> T anyToObject(Any any) throws Exception {
        if(any.is(DemoRequest.class))
            return (T)any.unpack(DemoRequest.class);
        if(any.is(DemoResponse.class))
            return (T)any.unpack(DemoResponse.class);
        if(any.is(Int32Value.class))
            return (T)Integer.valueOf(any.unpack(Int32Value.class).getValue());
        if(any.is(StringValue.class))
            return (T)any.unpack(StringValue.class).getValue();
        return null;
    }

    public static InputStream getFile(String filename) throws IOException {
        InputStream configStream = null;

        try { // check to see if the properties string is the name of a file.
            configStream=new FileInputStream(filename);
        }
        catch(FileNotFoundException | AccessControlException fnfe) {
            // the properties string is not a file
        }

        if(configStream == null) { // check to see if the properties string is a URL.
            try {
                configStream=new URL(filename).openStream();
            }
            catch (MalformedURLException mre) {
                // the properties string is not a URL
            }
        }

        // Check to see if the properties string is the name of a resource, e.g. udp.xml.
        if(configStream == null)
            return getResourceAsStream(filename, (Class<?>)null);
        return configStream;
    }

    public static InputStream getResourceAsStream(String name,Class<?> clazz) {
        return getResourceAsStream(name, clazz != null ? clazz.getClassLoader() : null);
    }

    public static InputStream getResourceAsStream(String name,ClassLoader loader) {
        InputStream retval;
        if (loader != null) {
            retval = loader.getResourceAsStream(name);
            if (retval != null)
                return retval;
        }
        try {
            loader=Thread.currentThread().getContextClassLoader();
            if(loader != null) {
                retval=loader.getResourceAsStream(name);
                if(retval != null)
                    return retval;
            }
        }
        catch(Throwable ignored) {
        }
        try {
            loader=ClassLoader.getSystemClassLoader();
            if(loader != null) {
                retval=loader.getResourceAsStream(name);
                if(retval != null)
                    return retval;
            }
        }
        catch(Throwable ignored) {
        }
        try {
            return new FileInputStream(name);
        }
        catch(FileNotFoundException e) {
        }
        return null;
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
