package org.jgroups.demos;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class JChannelTest extends ReceiverAdapter {
    JChannel channel;

    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }

    public void receive(Message msg) {
        String line=String.format("[%s]: %s", msg.getSrc(), stringFromMessage(msg));
        System.out.println(line);
    }

    private void start(String props, String name) throws Exception {
        channel=new JChannel(props);
        if(name != null)
            channel.name(name);
        channel.setReceiver(this);
        channel.connect("ChatCluster");
        eventLoop();
        channel.close();
    }

    private void eventLoop() {
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            try {
                System.out.print("> "); System.out.flush();
                String line=in.readLine().toLowerCase();
                if(line.startsWith("quit") || line.startsWith("exit")) {
                    break;
                }
                Message msg=new Message();
                stringIntoMessage(line, msg);
                channel.send(msg);
            }
            catch(Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    protected static String stringFromMessage(Message msg) {
        return msg != null && msg.getRawBuffer() != null?
          new String(msg.getRawBuffer(), msg.getOffset(), msg.getLength()) : "null";
    }

    protected static void stringIntoMessage(String str, Message msg) {
        byte[] buf=str.getBytes();
        msg.setBuffer(buf);
    }


    public static void main(String[] args) throws Exception {
        String props="config.xml";
        String name=null;

        for(int i=0; i < args.length; i++) {
            if(args[i].equals("-props")) {
                props=args[++i];
                continue;
            }
            if(args[i].equals("-name")) {
                name=args[++i];
                continue;
            }
            help();
            return;
        }

        new JChannelTest().start(props, name);
    }

    protected static void help() {
        System.out.println("JChannelTest [-props XML config] [-name name]");
    }
}
