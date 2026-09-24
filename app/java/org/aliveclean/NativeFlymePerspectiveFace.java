package org.aliveclean;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Flyme's original perspective controls, driven by the native host clock lifecycle. */
final class NativeFlymePerspectiveFace extends FrameLayout implements AutoCloseable {
    private final OfficialUi ui;
    private final View original;
    private final TextView time,date,week;
    private final Matrix timeMatrix,dateMatrix;
    private final boolean aod,compact;
    private final RectF body=new RectF();
    private final RectF numberInk=new RectF();
    private int nativeHeight;
    private float fit=1,originX,originY;

    NativeFlymePerspectiveFace(Context context,boolean aod,boolean compact)throws Exception{
        super(context);this.aod=aod;this.compact=compact;
        ui=new OfficialUi(context);
        original=ui.inflate("layout_distinctive_self",this);
        ((ViewGroup)original).setClipChildren(false);((ViewGroup)original).setClipToPadding(false);
        addView(original,new LayoutParams(-1,-1));
        time=(TextView)ui.find(original,"tv_distinctive_self_time");
        date=(TextView)ui.find(original,"tv_distinctive_self_date");
        week=(TextView)ui.find(original,"tv_distinctive_self_week");
        Typeface font=Typeface.createFromAsset(ui.getAssets(),"fonts/PlusJakartaSans-ExtraBold.ttf");
        time.setTypeface(font);date.setTypeface(font);
        timeMatrix=drawMatrix(time);dateMatrix=drawMatrix(date);
        // Values are from DistinctiveSelfClockViewModel's original steady states.
        perspective(time,aod?0:1,aod?1:1.125f,aod?1:2);
        perspective(date,aod?0:1,aod?1:1.0909091f,aod?1:2);
        margin(time,aod?"distinctive_self_aod_timemargin_top":"lockscreen_time_margin_top");
        margin(date,aod?"aod_date_margin_top":"lockscreen_date_margin_top");
        date.setAlpha(aod?.8f:1);week.setAlpha(.8f);
        week.setVisibility(aod?VISIBLE:INVISIBLE);
        original.getClass().getMethod("setMaskAlpha",float.class).invoke(original,0f);
        color(0xffffffff);update(System.currentTimeMillis(),TimeZone.getDefault(),true);
    }
    private static Matrix drawMatrix(TextView view)throws Exception{
        Field match=null;
        for(Field field:view.getClass().getDeclaredFields())if(field.getType()==Matrix.class){
            if(match!=null)throw new IllegalStateException("Ambiguous original perspective matrix");match=field;
        }
        if(match==null)throw new IllegalStateException("Missing original perspective matrix");
        match.setAccessible(true);return (Matrix)match.get(view);
    }
    private static void perspective(TextView view,float progress,float x,float y)throws Exception{
        view.getClass().getMethod("setPerspectiveProgress",float.class).invoke(view,progress);
        view.setTextScaleX(x);view.getClass().getMethod("setTextScaleY",float.class).invoke(view,y);
    }
    private void margin(View view,String resource){
        ViewGroup.MarginLayoutParams params=(ViewGroup.MarginLayoutParams)view.getLayoutParams();
        params.topMargin=ui.getResources().getDimensionPixelSize(ui.id("dimen",resource));view.setLayoutParams(params);
    }
    void update(long now,TimeZone zone,boolean format24){
        Calendar calendar=Calendar.getInstance(zone);calendar.setTimeInMillis(now);
        int hour=calendar.get(Calendar.HOUR_OF_DAY);if(!format24){hour%=12;if(hour==0)hour=12;}
        time.setText(String.format(Locale.ROOT,"%02d:%02d",hour,calendar.get(Calendar.MINUTE)));
        date.setText(String.format(Locale.getDefault(),"%02d/%02d",calendar.get(Calendar.MONTH)+1,calendar.get(Calendar.DAY_OF_MONTH)));
        SimpleDateFormat weekday=new SimpleDateFormat(ui.getString(ui.id("string","month_pattern")),Locale.getDefault());
        weekday.setTimeZone(zone);week.setText(weekday.format(calendar.getTime()));
        setContentDescription(time.getText()+" "+date.getText()+" "+week.getText());requestLayout();
    }
    void color(int value){time.setTextColor(value);date.setTextColor(value);week.setTextColor(value);}
    private static RectF ink(TextView view,Matrix draw){
        RectF result=new RectF();Layout layout=view.getLayout();if(layout==null)return result;
        String text=view.getText().toString();
        for(int line=0;line<layout.getLineCount();line++){
            int start=layout.getLineStart(line),end=layout.getLineEnd(line);if(end<=start)continue;
            Path path=new Path();
            float x=layout.getLineLeft(line)+(draw==null?view.getTotalPaddingLeft():0);
            float y=layout.getLineBaseline(line)+(draw==null?view.getTotalPaddingTop():0);
            view.getPaint().getTextPath(text,start,end,x,y,path);
            if(draw!=null)path.transform(draw);
            // computeBounds includes Bezier control points, which are outside
            // the visible perspective glyph. Use the curve itself for tracking.
            float[] curve=path.approximate(.25f);
            float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
            for(int i=0;i<curve.length;i+=3){left=Math.min(left,curve[i+1]);top=Math.min(top,curve[i+2]);right=Math.max(right,curve[i+1]);bottom=Math.max(bottom,curve[i+2]);}
            if(left<right&&top<bottom)result.union(left,top,right,bottom);
        }
        return result;
    }
    private RectF inOriginal(TextView view,Matrix draw){
        RectF box=ink(view,draw);if(box.isEmpty())return box;
        Matrix local=new Matrix();view.transformMatrixToGlobal(local);
        Matrix global=new Matrix(),inverse=new Matrix();original.transformMatrixToGlobal(global);
        if(!global.invert(inverse))throw new IllegalStateException("Singular perspective root");
        local.postConcat(inverse);local.mapRect(box);return box;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);nativeHeight=getResources().getDisplayMetrics().heightPixels;
        original.setScaleX(1);original.setScaleY(1);original.setTranslationX(0);original.setTranslationY(0);
        original.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(nativeHeight,MeasureSpec.EXACTLY));
        original.layout(0,0,width,nativeHeight);
        numberInk.set(inOriginal(time,timeMatrix));body.set(numberInk);body.union(inOriginal(date,dateMatrix));
        if(week.getVisibility()==VISIBLE)body.union(inOriginal(week,null));
        fit=body.isEmpty()?1:Math.min(1,width/body.width());
        if(compact&&!body.isEmpty())fit=Math.min(fit,104*getResources().getDisplayMetrics().density/body.height());
        originX=(width-body.width()*fit)/2-body.left*fit;originY=-body.top*fit;
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize((int)Math.ceil(body.height()*fit),heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        original.layout(0,0,r-l,nativeHeight);original.setPivotX(0);original.setPivotY(0);
        original.setScaleX(fit);original.setScaleY(fit);original.setTranslationX(originX);original.setTranslationY(originY);
    }
    void numberBounds(RectF out){
        out.set(numberInk.left*fit+originX,numberInk.top*fit+originY,numberInk.right*fit+originX,numberInk.bottom*fit+originY);
    }
    @Override public void close(){removeAllViews();ui.close();}
}
