package org.jgroups.demos;

import org.jgroups.blocks.Marshaller;
import org.jgroups.common.InputStreamAdapter;
import org.jgroups.common.OutputStreamAdapter;
import org.jgroups.common.Utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Bela Ban
 * @since  1.1.1
 */
public class DemoMarshaller implements Marshaller {

        @Override
        public void objectToStream(Object obj, DataOutput out) throws IOException {
            Utils.pbToStream(obj, new OutputStreamAdapter(out));
        }

        @Override
        public Object objectFromStream(DataInput in) {
            try {
                return Utils.pbFromStream(new InputStreamAdapter(in));
            }
            catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
