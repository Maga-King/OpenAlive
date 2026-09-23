package org.aliveclean;

import android.graphics.Rect;

/** Transfer the glyph inset into the accepted destination; never follow a live view. */
final class ClockVisualAnchor {
    static boolean project(Rect destination,Rect layout,Rect glyphs){
        if(destination.isEmpty()||layout.isEmpty()||glyphs.isEmpty())return false;
        float sx=(float)destination.width()/layout.width(),sy=(float)destination.height()/layout.height();
        int left=Math.round(destination.left+(glyphs.left-layout.left)*sx);
        int right=Math.round(destination.left+(glyphs.right-layout.left)*sx);
        int top=Math.round(destination.top+(glyphs.top-layout.top)*sy);
        int bottom=Math.round(destination.top+(glyphs.bottom-layout.top)*sy);
        if(bottom<=top||right<=left)return false;
        destination.set(left,top,right,bottom);return true;
    }
}
