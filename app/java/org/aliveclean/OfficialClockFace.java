package org.aliveclean;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Original editor clock layout/control, with host-owned time and lifecycle. */
final class OfficialClockFace extends FrameLayout implements AutoCloseable {
    private final OfficialUi ui;
    private final View clock;
    private final TextView date;
    private final TextView[] digits=new TextView[4];
    private final ClockUpdates updates;
    private long time = System.currentTimeMillis();
    private TimeZone zone = TimeZone.getDefault();
    private boolean format24;
    OfficialClockFace(Context context,boolean preview)throws Exception{
        super(context);ui=new OfficialUi(context);format24=DateFormat.is24HourFormat(context);
        View content=ui.inflate("view_sysui_lockscreen_clock_preview",this);addView(content,new LayoutParams(-1,-2));
        clock=ui.find(content,"tv_lockscreen_time");date=(TextView)ui.find(content,"tv_lockscreen_date");
        String[] names={"text_view_hour_first","text_view_hour_second","text_view_minute_first","text_view_minute_second"};
        for(int i=0;i<4;i++)digits[i]=(TextView)ui.find(clock,names[i]);
        clock.getClass().getMethod("setFontTypeface",Typeface.class).invoke(clock,Typeface.createFromAsset(ui.getAssets(),"fonts/FlymeNumber-VF.ttf"));
        clock.getClass().getMethod("setFontWeight",int.class).invoke(clock,400);
        clock.getClass().getMethod("setTextSize",float.class).invoke(clock,80f);
        clock.getClass().getMethod("b",int.class).invoke(clock,Color.WHITE);date.setTextColor(Color.WHITE);
        updates=new ClockUpdates(context,preview,this::refresh);
    }
    void layoutStyle(int style)throws Exception{
        if(style<0||style>5)throw new IllegalArgumentException("clock style");
        clock.getClass().getMethod("setClockStyle",int.class).invoke(clock,style);
        clock.getClass().getMethod("a").invoke(clock);update(time,zone,format24);
    }
    void refresh(long time){
        update(time,TimeZone.getDefault(),DateFormat.is24HourFormat(getContext()));
    }
    void update(long time,TimeZone zone,boolean format24){
        this.time=time;this.zone=zone;this.format24=format24;
        Calendar calendar=Calendar.getInstance(zone);calendar.setTimeInMillis(time);
        int hour=calendar.get(Calendar.HOUR_OF_DAY);if(!format24){hour%=12;if(hour==0)hour=12;}
        String value=String.format(Locale.ROOT,"%02d%02d",hour,calendar.get(Calendar.MINUTE));
        for(int i=0;i<digits.length;i++)digits[i].setText(value.substring(i,i+1));
        Locale locale=getResources().getConfiguration().getLocales().get(0);
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(locale,"MMMdEEE"),calendar));
        setContentDescription(DateFormat.format(format24?"HH:mm":"hh:mm",calendar)+" "+date.getText());
    }
    void color(int color)throws Exception {clock.getClass().getMethod("b",int.class).invoke(clock,color);date.setTextColor(color);}
    void textSize(float dp)throws Exception {
        if(!Float.isFinite(dp)||dp<=0)throw new IllegalArgumentException("clock text size");
        clock.getClass().getMethod("setTextSize",float.class).invoke(clock,dp);
        requestLayout();
    }
    void numberBounds(android.graphics.RectF out){
        out.setEmpty();
        for(TextView digit:digits){
            android.text.Layout layout=digit.getLayout();
            if(layout==null||digit.getText().length()==0)continue;
            android.graphics.Rect ink=new android.graphics.Rect();
            String value=digit.getText().toString();digit.getPaint().getTextBounds(value,0,value.length(),ink);
            android.graphics.Rect local=new android.graphics.Rect();digit.getDrawingRect(local);offsetDescendantRectToMyCoords(digit,local);
            float x=local.left+digit.getTotalPaddingLeft()+layout.getLineLeft(0)-digit.getScrollX();
            float y=local.top+digit.getTotalPaddingTop()+layout.getLineBaseline(0)-digit.getScrollY();
            out.union(x+ink.left,y+ink.top,x+ink.right,y+ink.bottom);
        }
    }
    void dateBounds(android.graphics.RectF out){android.graphics.Rect r=new android.graphics.Rect();date.getDrawingRect(r);offsetDescendantRectToMyCoords(date,r);out.set(r);}
    void active(boolean enabled){if(enabled)updates.start();else updates.stop();}
    @Override protected void onDetachedFromWindow(){updates.stop();super.onDetachedFromWindow();}
    @Override public void close(){updates.stop();ui.close();}
}
