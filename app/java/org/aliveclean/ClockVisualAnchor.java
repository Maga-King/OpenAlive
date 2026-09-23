package org.aliveclean;

import android.graphics.Rect;

/** Transfer the glyph inset into the accepted destination; never follow a live view. */
final class ClockVisualAnchor {
    static boolean vertical(Rect destination,Rect layout,Rect glyphs){
        if(destination.isEmpty()||layout.isEmpty()||glyphs.isEmpty())return false;
        float scale=(float)destination.height()/layout.height();
        int top=Math.round(destination.top+(glyphs.top-layout.top)*scale);
        int bottom=Math.round(destination.top+(glyphs.bottom-layout.top)*scale);
        if(bottom<=top)return false;
        destination.top=top;destination.bottom=bottom;return true;
    }
}
