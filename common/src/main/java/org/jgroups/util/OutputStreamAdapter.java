package org.jgroups.util;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * An OutputStream facade wrapping a DataOutput
 * @author Bela Ban
 */
public class OutputStreamAdapter extends OutputStream {
    protected final DataOutput out;

    public OutputStreamAdapter(DataOutput output) {
        this.out=Objects.requireNonNull(output);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

}
