package org.aliveclean;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.View;

/** Reversible spacing on the dedicated widget root, in screen coordinates. */
final class AodWidgetSpace {
    interface Bounds { Rect read(); }
    private View view;
    private Bounds bounds;
    private final Matrix matrix=new Matrix();
    private final float[] axis=new float[2];
    private float base,offset,written=Float.NaN;
    private int bottom;

    void bind(View target,Bounds source){
        if(view!=target){clear();view=target;}
        bounds=source;
    }
    int bottom(){return bottom;}
    void update(int floor){
        if(floor<=0||view==null||bounds==null||!view.isAttachedToWindow()||!view.isShown()){restore();return;}
        float current=view.getTranslationY();
        // An external animator owns its newest value. Never subtract an old
        // correction from a value that the platform has already replaced.
        if(current!=written){base=current;offset=0;written=Float.NaN;}
        Rect rect=bounds.read();
        if(rect==null||rect.isEmpty()||!(view.getParent() instanceof View)){restore();return;}
        matrix.reset();((View)view.getParent()).transformMatrixToGlobal(matrix);
        axis[0]=0;axis[1]=1;matrix.mapVectors(axis);
        float scale=axis[1];
        if(!Float.isFinite(scale)||scale<.01f||Math.abs(axis[0])>.001f){restore();return;}
        float next=Math.max(0,offset+(floor-rect.top)/scale);
        // Visible bounds round to pixels. Keep an already aligned correction
        // stable instead of scheduling another traversal for subpixel noise.
        if(next>0&&Math.abs(next-offset)*scale<=1.01f)next=offset;
        bottom=Math.round(rect.bottom+(next-offset)*scale);
        offset=next;written=base+offset;
        if(current!=written)view.setTranslationY(written);
    }
    void restore(){
        if(view!=null&&view.getTranslationY()==written)view.setTranslationY(base);
        offset=0;written=Float.NaN;bottom=0;
    }
    void clear(){restore();view=null;bounds=null;}
}
