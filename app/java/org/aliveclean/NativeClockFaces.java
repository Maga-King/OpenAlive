package org.aliveclean;

import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import java.util.TimeZone;

/** Original face ownership; transport, native widgets and editor stay shared. */
interface NativeClockFaces extends AutoCloseable {
    void attach(FrameLayout parent);
    void scene(int state,boolean compact);
    View active();
    View lock(boolean compact);
    void update(long time,TimeZone zone,boolean format24);
    void color(int color)throws Exception;
    void numberBounds(RectF out);
    @Override void close();
}
