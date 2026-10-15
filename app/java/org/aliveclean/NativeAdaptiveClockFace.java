package org.aliveclean;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

/** The selected vendor's normal clock, with separate layout and scene transforms. */
final class NativeAdaptiveClockFace extends ViewGroup {
    private final View content;
    private final NativeClockFaces faces;
    private final float horizontalMinimumDigitHeight,verticalMinimumDigitHeight;
    private int expectedBottom=Integer.MAX_VALUE,top,widgets;
    private int naturalHeight,minimumHeight,occupiedHeight;
    private float contentScale=1;

    NativeAdaptiveClockFace(Context context,View content,NativeClockFaces faces,float horizontalMinimumDigitHeight,float verticalMinimumDigitHeight){
        super(context);this.content=content;this.faces=faces;
        this.horizontalMinimumDigitHeight=horizontalMinimumDigitHeight;this.verticalMinimumDigitHeight=verticalMinimumDigitHeight;
        setClipChildren(false);setClipToPadding(false);
        addView(content,new LayoutParams(-1,-2));
    }
    boolean budget(int bottom,int top,int widgets){
        if(expectedBottom==bottom&&this.top==top&&this.widgets==widgets)return false;
        expectedBottom=bottom;this.top=top;this.widgets=widgets;requestLayout();
        return true;
    }
    int naturalHeight(){return naturalHeight;}
    int minimumHeight(){return minimumHeight;}
    View content(){return content;}
    void mapContentBounds(RectF bounds){
        Matrix matrix=new Matrix(content.getMatrix());
        matrix.postTranslate(content.getLeft()-getScrollX(),content.getTop()-getScrollY());matrix.mapRect(bounds);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        content.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        naturalHeight=content.getMeasuredHeight();
        // Resolve the vendor's original leaf geometry even while the big scene is hidden.
        content.layout(0,0,width,naturalHeight);
        float digitHeight=faces.lockDigitHeight();
        float minimumDigitHeight=faces.lockDigitsStacked()?verticalMinimumDigitHeight:horizontalMinimumDigitHeight;
        double minimumScale=Float.isFinite(digitHeight)&&digitHeight>0&&Float.isFinite(minimumDigitHeight)&&minimumDigitHeight>0
                ?Math.min(1d,minimumDigitHeight/(double)digitHeight):1d;
        minimumHeight=Math.min(naturalHeight,(int)Math.ceil(naturalHeight*minimumScale));
        long available=expectedBottom==Integer.MAX_VALUE?naturalHeight:(long)expectedBottom-top-widgets;
        occupiedHeight=(int)Math.max(minimumHeight,Math.min(naturalHeight,available));
        contentScale=naturalHeight>0?occupiedHeight/(float)naturalHeight:1;
        applyContentScale(width);
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(occupiedHeight,heightSpec));
    }
    private void applyContentScale(int width){
        content.setPivotX(width/2f);content.setPivotY(0);
        content.setScaleX(contentScale);content.setScaleY(contentScale);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        content.layout(0,0,r-l,naturalHeight);applyContentScale(r-l);
    }
}
