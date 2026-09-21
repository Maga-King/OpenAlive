package org.aliveclean;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.widget.TextView;

/** Drawn single-line digits, independent of temporary font-animation layout padding. */
final class ClockInkBounds {
    private final Rect ink=new Rect();
    private final RectF mapped=new RectF();
    private final Matrix transform=new Matrix();
    boolean measure(TextView view,float drawOffsetY,Rect result){
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
        transform.reset();view.transformMatrixToGlobal(transform);transform.mapRect(mapped);
        mapped.roundOut(result);
        return !result.isEmpty();
    }
}
