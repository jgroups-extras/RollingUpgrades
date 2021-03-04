package org.jgroups.common;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Bela Ban
 * @since  1.1.1
 */
public class ConnectionStatus {
    enum State {disconnected, connecting, connected, disconnecting};

    protected final Lock sl=new ReentrantLock();
    protected State      state=State.disconnected;


    public State getState() {
        sl.lock();
        try {
            return state;
        }
        finally {
            sl.unlock();
        }
    }

    public void setState(ConnectionStatus.State to) {
        sl.lock();
        try {
            state=to;
        }
        finally {
            sl.unlock();
        }
    }

    public boolean setState(ConnectionStatus.State expected, ConnectionStatus.State to) {
        sl.lock();
        try {
            if(state == expected) {
                state=to;
                return true;
            }
            return false;
        }
        finally {
            sl.unlock();

        }
    }

    public boolean isState(ConnectionStatus.State expected) {
        sl.lock();
        try {
            return state == expected;
        }
        finally {
            sl.unlock();
        }
    }

    public boolean isStateOneOf(ConnectionStatus.State ... states) {
        sl.lock();
        try {
            if(states != null) {
                for(ConnectionStatus.State st : states) {
                    if(state == st)
                        return true;
                }
            }
            return false;
        }
        finally {
            sl.unlock();
        }
    }



    public String toString() {
        return state.toString();
    }
}
