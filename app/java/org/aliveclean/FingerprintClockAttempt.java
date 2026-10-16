package org.aliveclean;

/** Visual-only lifetime; this never authorizes or initiates an unlock. Main-thread owned. */
final class FingerprintClockAttempt {
    private long serial;
    private volatile boolean active;
    private boolean accepted;
    long begin(){active=true;accepted=false;return ++serial;}
    long token(){return serial;}
    boolean active(){return active;}
    void accepted(){if(active)accepted=true;}
    boolean cancelBeforeUnlock(long token){
        if(!active||accepted||serial!=token)return false;
        clear();return true;
    }
    boolean expire(long token){
        if(!active||serial!=token)return false;
        clear();return true;
    }
    void clear(){active=false;accepted=false;serial++;}
}
