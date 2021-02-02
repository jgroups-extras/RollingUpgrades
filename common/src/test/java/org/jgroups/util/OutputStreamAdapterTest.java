package org.jgroups.util;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Bela Ban
 * @since  1.1.1
 */
@Test(singleThreaded=true)
public class OutputStreamAdapterTest {
    protected ByteArrayOutputStream outstream;
    protected static final byte[] HELLO="hello".getBytes();

    @BeforeMethod protected void init() {
        outstream=new ByteArrayOutputStream(16);
    }

    public void testWrite() throws IOException {
        DataOutput out=new DataOutputStream(outstream);
        assert outstream.size() == 0;
        OutputStreamAdapter ad=new OutputStreamAdapter(out);
        for(byte b: HELLO)
            ad.write(b);
        assert outstream.size() == 5;
        byte[] buf=outstream.toByteArray();
        assert Arrays.equals(buf, HELLO);
    }

    public void testWriteArray() throws IOException {
        DataOutput out=new DataOutputStream(outstream);
        assert outstream.size() == 0;
        OutputStreamAdapter ad=new OutputStreamAdapter(out);
        ad.write(HELLO);
        assert outstream.size() == 5;
        byte[] buf=outstream.toByteArray();
        assert Arrays.equals(buf, HELLO);
    }

    public void testWriteArrayWithOffset() throws IOException {
        DataOutput out=new DataOutputStream(outstream);
        assert outstream.size() == 0;
        OutputStreamAdapter ad=new OutputStreamAdapter(out);
        ad.write(HELLO, 2, 3);
        assert outstream.size() == 3;
        byte[] buf=outstream.toByteArray();
        assert Arrays.equals(buf, "llo".getBytes());
    }


}
