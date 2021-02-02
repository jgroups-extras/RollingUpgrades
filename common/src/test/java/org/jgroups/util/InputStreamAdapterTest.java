package org.jgroups.util;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Tests {@link InputStreamAdapter}
 * @author Bela Ban
 * @since  1.1.1
 */
@Test(singleThreaded=true)
public class InputStreamAdapterTest {
    DataInput in;
    InputStreamAdapter ad;
    protected static final byte[] HELLO="hello world".getBytes();

    @BeforeMethod
    protected void init() {
        in=create(HELLO);
        ad=new InputStreamAdapter(in);
    }

    public void testRead() throws IOException {
        int ch=ad.read();
        assert ch == 'h';
    }

    public void testReadArray() throws IOException {
        byte[] buf=new byte[HELLO.length];
        ad.read(buf, 0, buf.length);
        assert Arrays.equals(buf, HELLO);
    }

    public void testReadArrayWithOffset() throws IOException {
        byte[] buf=new byte[HELLO.length];
        System.arraycopy(HELLO, 0, buf, 0, buf.length);
        ad.read(buf, 6, 5);
        assert Arrays.equals(buf, "hello hello".getBytes());
    }

    public void testReadArrayWithIncorrectLength() throws IOException {
        byte[] buf=new byte[HELLO.length];
        System.arraycopy(HELLO, 0, buf, 0, buf.length);
        ad.read(buf, 6, 6);
        assert Arrays.equals(buf, "hello hello".getBytes());
    }

    public void testReadArrayWithIncorrectLength2() throws IOException {
        byte[] buf=new byte[HELLO.length+1];
        ad.read(buf, 0, HELLO.length+1); // length is too long
        String s1=new String(HELLO).trim(), s2=new String(buf).trim();
        assert s1.equals(s2);
    }

    protected static DataInput create(byte[] buf) {
        return new DataInputStream(new ByteArrayInputStream(buf));
    }
}
