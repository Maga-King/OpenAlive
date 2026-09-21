package org.aliveclean;

/** Follow the display's monotonic vsync timestamps without a second FPS cap. */
final class VsyncPacer {
    private long lastFrame=Long.MIN_VALUE;
    void reset(){lastFrame=Long.MIN_VALUE;}
    boolean due(long vsyncNanos) {
        if(vsyncNanos<=lastFrame)return false;
        lastFrame=vsyncNanos;return true;
    }
}
