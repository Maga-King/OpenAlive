package org.aliveclean;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

/** AOD-only content. Time advances independently of wallpaper drawing. */
final class AodClockView extends FrameLayout {
    private final LinearLayout content;
    private final TextView time,date,lunar;
    private final ClockUpdates updates;
    private boolean enabled;
    private int typographyWidth;
    private long formattedMinute=Long.MIN_VALUE;
    private TimeZone formattedZone;
    private Locale formattedLocale,formattedDefaultLocale;
    private boolean formatted24Hour;
    private final int[] screenLocation=new int[2];
    private static Typeface digits;
    private static synchronized Typeface digits(Context context){
        if(digits==null)try{
            Context assets=context;
            if(!context.getPackageName().startsWith("org.aliveclean"))assets=context.createPackageContext("org.aliveclean",0);
            digits=Typeface.createFromAsset(assets.getAssets(),"clock/horizontal.otf");
        }catch(Exception error){android.util.Log.w("AliveClean","Original AOD font unavailable",error);digits=Typeface.create("sans-serif-condensed",Typeface.NORMAL);}
        return digits;
    }
    AodClockView(Context context,boolean preview){
        super(context);setClickable(false);setFocusable(false);setClipChildren(false);
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);
        time=new TextView(context);date=new TextView(context);lunar=new TextView(context);
        for(TextView text:new TextView[]{time,date,lunar}){text.setTextColor(Color.WHITE);text.setGravity(Gravity.CENTER);text.setIncludeFontPadding(false);text.setSingleLine(true);content.addView(text,new LinearLayout.LayoutParams(-2,-2));}
        time.setTypeface(digits(context));date.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));lunar.setTypeface(date.getTypeface());
        addView(content,new LayoutParams(-1,-2,Gravity.TOP));
        updates=new ClockUpdates(context,preview,this::refresh);refresh(System.currentTimeMillis());
    }
    void active(boolean value){enabled=value;updateActivity();}
    void tick(long now){if(enabled)updates.refresh(now);}
    void refresh(long now){
        boolean full=DateFormat.is24HourFormat(getContext());
        Locale locale=getResources().getConfiguration().getLocales().get(0);
        TimeZone zone=TimeZone.getDefault();Locale defaultLocale=Locale.getDefault();long minute=Math.floorDiv(now,60000L);
        if(minute==formattedMinute&&full==formatted24Hour&&locale.equals(formattedLocale)&&defaultLocale.equals(formattedDefaultLocale)&&zone.equals(formattedZone))return;
        Calendar calendar=Calendar.getInstance(zone);calendar.setTimeInMillis(now);
        time.setText(DateFormat.format(full?"HH:mm":"h:mm",calendar));
        boolean chinese=locale.getLanguage().equals("zh");
        date.setText(chinese?DateFormat.format("M月d日",calendar)+"  "+DateFormat.format("EEE",calendar):DateFormat.format(DateFormat.getBestDateTimePattern(locale,"MMMdEEE"),calendar));
        lunar.setVisibility(chinese?VISIBLE:GONE);
        lunar.setText(chinese?AodCalendar.lunar(now,calendar.getTimeZone().getID()):"");
        setContentDescription(time.getText()+" "+date.getText()+" "+lunar.getText());
        formattedMinute=minute;formattedZone=zone;formattedLocale=locale;formattedDefaultLocale=defaultLocale;formatted24Hour=full;
    }
    private void updateActivity(){if(enabled&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE)updates.start();else updates.stop();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec);
        if(w>0&&w!=typographyWidth){
            typographyWidth=w;
            time.setTextSize(TypedValue.COMPLEX_UNIT_PX,w*.13f);
            for(TextView text:new TextView[]{date,lunar}){
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX,w*.038f);
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)text.getLayoutParams();p.topMargin=Math.round(w*.025f);text.setLayoutParams(p);
            }
        }
        // Typography must be set before TextViews measure: a short AOD window may
        // never get another layout pass before the display enters DOZE again.
        super.onMeasure(widthSpec,heightSpec);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);
        // Fixed endpoint below the photo stack; never interpolate into the lock clock.
        content.setTranslationY(getHeight()*.225f+getWidth()*.22243f);
    }
    int notificationTop(){getLocationOnScreen(screenLocation);return Math.round(screenLocation[1]+content.getY()+content.getHeight()+getWidth()*.035f);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();updateActivity();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(updates!=null)updateActivity();}
    @Override protected void onDetachedFromWindow(){updates.stop();super.onDetachedFromWindow();}
}
