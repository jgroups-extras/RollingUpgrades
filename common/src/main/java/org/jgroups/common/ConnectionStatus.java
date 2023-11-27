package org.jgroups.common;

/**
 * @author Bela Ban
 * @since  1.1.1
 */
public class ConnectionStatus {
    public enum State {disconnected, connecting, connected, disconnecting};

    protected State state=State.disconnected;


    public synchronized State   getState()              {return state;}
    public synchronized void    setState(State to)      {state=to;}
    public synchronized boolean isState(State expected) {return state == expected;}

    public synchronized boolean setState(State expected, State to) {
        if(state == expected) {
            state=to;
            return true;
        }
        return false;
    }

    public synchronized boolean isStateOneOf(State ... states) {
        if(states != null) {
            for(State st : states) {
                if(state == st)
                    return true;
            }
        }
        return false;
    }

    public synchronized String toString() {
        return state.toString();
    }
}
