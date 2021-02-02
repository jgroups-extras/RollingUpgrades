package org.jgroups.util;

import org.jgroups.demos.DemoResponse;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Tests the Marshaller implementations for the demos
 * @author Bela Ban
 * @since  1.1.1
 */
@Test
public class MarshallerTest {


    public void testMarshaller3() throws Exception {
        ByteArray buf=objectToBuffer(5);
        DemoResponse rsp=(DemoResponse)objectFromBuffer(buf.getArray(), buf.getOffset(), buf.getLength());
        System.out.println("rsp = " + rsp);
        assert rsp.getCount() == 5 : rsp;
    }

    public void testMarshaller4() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        DataOutput out=new DataOutputStream(output);
        objectToStream(5, out);
        byte[] b=output.toByteArray();
        ByteArrayInputStream input=new ByteArrayInputStream(b);
        DataInput in=new DataInputStream(input);
        DemoResponse rsp=(DemoResponse)objectFromStream(in);
        System.out.println("rsp = " + rsp);
        assert rsp.getCount() == 5 : rsp;
    }

    @Test(enabled=false)
    public static void main(String[] args) throws Exception {
        ByteArray buf=objectToBuffer(5);

        DemoResponse rsp=(DemoResponse)objectFromBuffer(buf.getArray(), buf.getOffset(), buf.getLength());
        System.out.println("rsp = " + rsp);

        ByteArrayOutputStream output=new ByteArrayOutputStream();
        DataOutput out=new DataOutputStream(output);
        objectToStream(5, out);
        output.flush();

        byte[] b=output.toByteArray();
        ByteArrayInputStream input=new ByteArrayInputStream(b);
        DataInputStream in=new DataInputStream(input);

        DemoResponse obj=(DemoResponse)objectFromStream(in);
        System.out.println("obj = " + obj);
    }


    protected static ByteArray objectToBuffer(Object obj) throws Exception {
        // DemoResponse rsp=DemoResponse.newBuilder().setCount((Integer)obj).build();
        DemoResponse rsp=DemoResponse.newBuilder().setCount((Integer)obj).build();
        byte[] buf=rsp.toByteArray();
        return new ByteArray(buf);

        //ByteArray ret=Utils.Marshaller.objectToBuffer(obj);
        //return new Buffer(ret.getArray(), ret.getOffset(), ret.getLength());
    }

    protected static Object objectFromBuffer(byte[] buf, int offset, int length) throws Exception {
        return DemoResponse.parseFrom(ByteBuffer.wrap(buf, offset, length));
    }


    protected static void objectToStream(Object obj, DataOutput out) throws IOException {
        DemoResponse rsp=DemoResponse.newBuilder().setCount((Integer)obj).build();
        rsp.writeTo(new OutputStreamAdapter(out));
    }

    protected static Object objectFromStream(DataInput in) throws IOException, ClassNotFoundException {
        return DemoResponse.parseFrom(new InputStreamAdapter(in));
    }
}
