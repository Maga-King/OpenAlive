package org.aliveclean;

/** Keep the CPU awake until the bounded display transition and its final state handoff. */
final class AodTransitionWindow {
    private static final int NATIVE_MAX_SCREEN_MS=5000;
    private static final int SETTLE_MS=500;
    final int screenMs;
    final long cpuMs;
    private AodTransitionWindow(int screenMs){this.screenMs=screenMs;cpuMs=(long)screenMs+SETTLE_MS;}
    static AodTransitionWindow prepare(boolean selected,String reason,boolean reshow,int requestedMs){
        if(!selected||reshow||!"PROCESS_TYPE_PANORAMIC_AOD".equals(reason)
                ||requestedMs<=0||requestedMs>NATIVE_MAX_SCREEN_MS)return null;
        return new AodTransitionWindow(Math.max(requestedMs,AodMotionWindow.DURATION_MS));
    }
}
