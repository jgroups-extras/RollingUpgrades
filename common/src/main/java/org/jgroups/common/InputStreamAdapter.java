package org.jgroups.common;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * An InputStream facade wrapping DataInput
 * @author Bela Ban
 * @since x.y
 */
public class InputStreamAdapter extends InputStream {
    protected final DataInput in;

    public InputStreamAdapter(DataInput in) {
        this.in=Objects.requireNonNull(in);
    }

    @Override
    public int read() throws IOException {
        return in.readByte();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytes_read=0;
        while(bytes_read < len) {
            if(off+1 > b.length)
                return bytes_read > 0? bytes_read : -1;
            try {
                int c=read();
                b[off++]=(byte)c;
                bytes_read++;
            }
            catch(EOFException eof) {
                return bytes_read > 0? bytes_read : -1;
            }
        }
        return bytes_read;
    }

    @Override
    public long skip(long n) throws IOException {
        return in.skipBytes((int)n);
    }

}
