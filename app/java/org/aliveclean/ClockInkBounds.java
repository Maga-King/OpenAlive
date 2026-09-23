package org.aliveclean;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.widget.TextView;
import android.view.View;
import android.view.ViewParent;

/** Drawn single-line digits, independent of temporary font-animation layout padding. */
final class ClockInkBounds {
    private final Rect ink=new Rect();
    private final RectF mapped=new RectF();
    private final Matrix transform=new Matrix();
    boolean measure(TextView view,float drawOffsetY,Rect result){
        if(!local(view,drawOffsetY))return false;
        transform.reset();view.transformMatrixToGlobal(transform);transform.mapRect(mapped);
        mapped.roundOut(result);
        return !result.isEmpty();
    }
    boolean measureLayout(TextView view,float drawOffsetY,View ancestor,Rect result){
        if(!local(view,drawOffsetY))return false;
        // Intrinsic text layout only. Transient scale/translation belongs to the
        // handoff animation, not the destination anchor's font inset.
        for(View current=view;current!=ancestor;){
            mapped.offset(current.getLeft(),current.getTop());
            ViewParent parent=current.getParent();if(!(parent instanceof View))return false;
            current=(View)parent;
        }
        mapped.roundOut(result);return !result.isEmpty();
    }
    private boolean local(TextView view,float drawOffsetY){
        Layout layout=view.getLayout();
        if(layout==null||layout.getLineCount()!=1)return false;
        CharSequence text=layout.getText();
        if(text==null||text.length()==0||text.length()>16)return false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);if(!Character.isDigit(c)&&c!=':'&&c!='\uff1a'&&!Character.isWhitespace(c))return false;
        }
        for(android.graphics.drawable.Drawable drawable:view.getCompoundDrawables())if(drawable!=null)return false;
        view.getPaint().getTextBounds(text,0,text.length(),ink);
        if(ink.isEmpty())return false;
        float x=view.getCompoundPaddingLeft()+layout.getLineLeft(0)-view.getScrollX();
        float y=view.getBaseline()+drawOffsetY-view.getScrollY();
        mapped.set(ink);mapped.offset(x,y);
        return true;
    }
}
