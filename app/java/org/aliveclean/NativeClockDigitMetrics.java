package org.aliveclean;

import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.TextView;
import java.util.List;

/** Stable digit metrics, independent of the current minute's glyphs. */
final class NativeClockDigitMetrics {
    private NativeClockDigitMetrics(){}
    static float textHeight(TextView text){
        if(text.getLayout()==null||text.getLayout().getLineCount()!=1)return 0;
        Rect bounds=new Rect();text.getPaint().getTextBounds("0123456789",0,10,bounds);
        return bounds.height();
    }
    static boolean stacked(List<RectF> digits){
        float top=Float.NEGATIVE_INFINITY,bottom=Float.POSITIVE_INFINITY;
        int count=0;
        for(RectF digit:digits){
            if(digit.isEmpty())continue;
            top=Math.max(top,digit.top);bottom=Math.min(bottom,digit.bottom);count++;
        }
        // Digits on the same row share a vertical interval; separated rows do not.
        return count>1&&top>=bottom;
    }
}
