package org.aliveclean;

/** Photo AOD's official three-second render window and linear speed countdown. */
final class AodMotionWindow {
    static final int DURATION_MS=3000;
    private long start=-1;
    void enter(long nanos){start=nanos;}
    void leave(){start=-1;}
    float speed(long nanos){return start<0?0f:Math.max(0f,1f-Math.max(0,nanos-start)/(DURATION_MS*1_000_000f));}
    boolean running(long nanos){return speed(nanos)>0;}
}
