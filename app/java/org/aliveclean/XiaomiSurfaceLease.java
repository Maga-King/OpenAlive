package org.aliveclean;

import android.view.Surface;

/** Loaded by the Snow runtime's class loader, so NOLOAD finds its existing engine. */
public final class XiaomiSurfaceLease {
    static { System.loadLibrary("alive_clean"); }
    private long handle;
    public XiaomiSurfaceLease(String frameworkPath) { handle=open(frameworkPath); }
    public void retain(Surface surface) { retain(handle,surface); }
    public void replaced() { replaced(handle); }
    public void destroyed() { if(handle!=0){destroyed(handle);handle=0;} }
    private static native long open(String frameworkPath);
    private static native void retain(long handle,Surface surface);
    private static native void replaced(long handle);
    private static native void destroyed(long handle);
}
