package org.jgroups.demos;

import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.common.Utils;
import org.jgroups.util.Buffer;

import java.nio.ByteBuffer;

/**
 * @author Bela Ban
 * @since  1.1.1
 */
public class DemoMarshaller implements RpcDispatcher.Marshaller {

       @Override
       public Buffer objectToBuffer(Object obj) throws Exception {
           byte[] b=obj instanceof MethodCall? methodCallToByteArray((MethodCall)obj) : Utils.pbToByteArray(obj);
           if(b != null)
               return new Buffer(b);
           throw new IllegalArgumentException(String.format("response %s not handled", obj));
       }

       @Override
       public Object objectFromBuffer(byte[] buf, int offset, int length) throws Exception {
           Any any=Any.parseFrom(ByteBuffer.wrap(buf, offset, length));
           return any.is(org.jgroups.upgrade_server.MethodCall.class)? methodCallFromAny(any) : Utils.anyToObject(any);
       }


    protected static byte[] methodCallToByteArray(MethodCall call) {
        if(call.getMode() != 3) // IDs, change to MethodCall.ID
            throw new IllegalArgumentException(String.format("method call %s must use IDs", call));
        org.jgroups.upgrade_server.MethodCall.Builder builder=org.jgroups.upgrade_server.MethodCall.newBuilder()
          .setId(call.getId());
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
        return a.toByteArray();
    }

    protected static MethodCall methodCallFromAny(Any any) throws Exception {
        org.jgroups.upgrade_server.MethodCall mc=any.unpack(org.jgroups.upgrade_server.MethodCall.class);
        String name=mc.getArguments(0).unpack(StringValue.class).getValue();
        int num=mc.getArguments(1).unpack(Int32Value.class).getValue();
        return new MethodCall((short)mc.getId(), name, num);
    }
}
