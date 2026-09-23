package org.aliveclean;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.widget.TextView;

/** Drawn single-line digits, independent of temporary font-animation layout padding. */
final class ClockInkBounds {
    private final Rect ink=new Rect();
    private final RectF mapped=new RectF();
    private final Matrix transform=new Matrix();
    private final RectF viewport=new RectF();
    private final Path outline=new Path(),clip=new Path();
    private Class<?> paintType;
    private java.lang.reflect.Method rtPaint;
    boolean measure(TextView view,float drawOffsetY,Rect result){
        if(!local(view,drawOffsetY))return false;
        transform.reset();view.transformMatrixToGlobal(transform);transform.mapRect(mapped);
        mapped.roundOut(result);
        return !result.isEmpty();
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
        Paint paint=view.getPaint();
        if(paintType!=view.getClass()){
            paintType=view.getClass();rtPaint=null;
            try{rtPaint=paintType.getMethod("getRtTextPaint");}catch(NoSuchMethodException ignored){}
        }
        if(rtPaint!=null)try{
            Object value=rtPaint.invoke(view);if(value instanceof Paint)paint=(Paint)value;
        }catch(ReflectiveOperationException ignored){}
        paint.getTextBounds(text,0,text.length(),ink);
        if(ink.isEmpty())return false;
        float x=view.getCompoundPaddingLeft()+layout.getLineLeft(0)-view.getScrollX();
        float y=view.getBaseline()+drawOffsetY-view.getScrollY();
        mapped.set(ink);mapped.offset(x,y);
        // Variable fonts can extend past the visible text viewport. The anchor
        // follows the displayed glyphs, not the offscreen outline in the font.
        viewport.set(view.getCompoundPaddingLeft(),view.getExtendedPaddingTop()+drawOffsetY,
                view.getWidth()-view.getCompoundPaddingRight(),view.getHeight()-view.getExtendedPaddingBottom()+drawOffsetY);
        if(viewport.contains(mapped))return true;
        // Clipping a diagonal digit can also change its horizontal extent;
        // intersecting just the bounding rectangles would keep that hidden part.
        outline.reset();clip.reset();
        paint.getTextPath(text.toString(),0,text.length(),x,y,outline);
        clip.addRect(viewport,Path.Direction.CW);
        if(!outline.op(clip,Path.Op.INTERSECT)||outline.isEmpty())return false;
        outline.computeBounds(mapped,true);return !mapped.isEmpty();
    }
}
