package org.jgroups.demos;

import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import org.jgroups.blocks.MethodCall;
import org.jgroups.common.ByteArray;
import org.jgroups.common.Marshaller;

import java.util.Arrays;

/**
 * @author Bela Ban
 * @since  1.1.1
 */
public class DemoMarshaller implements Marshaller {

    @Override
    public ByteArray objectToBuffer(Object obj) throws Exception {
        if(obj instanceof ByteArray)
            return (ByteArray)obj;
        if(obj instanceof byte[])
            return new ByteArray((byte[])obj);
        if(obj instanceof MethodCall) { // it is a request
            MethodCall call=(MethodCall)obj;
            if(!call.useIds())
                throw new IllegalArgumentException(String.format("method call %s must use IDs", call));
            org.jgroups.upgrade_server.MethodCall.Builder builder=org.jgroups.upgrade_server.MethodCall.newBuilder()
              .setId(call.getMethodId());
            Object[] args=call.getArgs();
            // we only have a single method here, otherwise we'd switch on the method ID
            String name=(String)args[0];
            Integer num=(Integer)args[1];

            StringValue str=StringValue.newBuilder().setValue(name).build();
            builder.addArguments(Any.pack(str));

            Int32Value i=Int32Value.newBuilder().setValue(num).build();
            builder.addArguments(Any.pack(i));

            // now pack the whole MethodCall into an Any
            org.jgroups.upgrade_server.MethodCall mc=builder.build();
            Any a=Any.pack(mc);
            return new ByteArray(a.toByteArray());
        }
        if(obj instanceof DemoRequest)
            return new ByteArray(Any.pack((DemoRequest)obj).toByteArray());
        if(obj instanceof DemoResponse)
            return new ByteArray(Any.pack((DemoResponse)obj).toByteArray());
        if(obj instanceof Integer) {
            Int32Value val=Int32Value.newBuilder().setValue((Integer)obj).build();
            return new ByteArray(Any.pack(val).toByteArray());
        }
        if(obj instanceof String) {
            StringValue val=StringValue.newBuilder().setValue((String)obj).build();
            return new ByteArray(Any.pack(val).toByteArray());
        }
        throw new IllegalArgumentException(String.format("serialization of %s is not handled by this marshaller", obj));
    }

    @Override
    public Object objectFromBuffer(byte[] buf, int offset, int length) throws Exception {
        byte[] ba=offset == 0 && length == buf.length? buf : Arrays.copyOfRange(buf, offset, offset+length);
        Any any=Any.parseFrom(ba);
        if(any.is(DemoRequest.class))
            return any.unpack(DemoRequest.class);
        if(any.is(DemoResponse.class))
            return any.unpack(DemoResponse.class);
        if(any.is(org.jgroups.upgrade_server.MethodCall.class)) {
            org.jgroups.upgrade_server.MethodCall mc=any.unpack(org.jgroups.upgrade_server.MethodCall.class);

            String name=mc.getArguments(0).unpack(StringValue.class).getValue();
            int num=mc.getArguments(1).unpack(Int32Value.class).getValue();
            return new MethodCall((short)mc.getId(), name, num);
        }
        if(any.is(Int32Value.class))
            return any.unpack(Int32Value.class).getValue();
        if(any.is(StringValue.class))
            return any.unpack(StringValue.class).getValue();
        throw new IllegalArgumentException(String.format("de-serialization of any %s is not handled by this marshaller", any));
    }
}