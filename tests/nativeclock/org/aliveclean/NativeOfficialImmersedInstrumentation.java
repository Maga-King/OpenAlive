package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Compare the cropped lyrics adapter with an independent, installed digital plugin. */
public final class NativeOfficialImmersedInstrumentation extends Instrumentation {
    private static final long TIME=1791994560000L;
    private static final String[] DIGITS={"getTimeHourOnesView","getTimeHourTensView","getTimeSymbolView","getTimeMinuteOnesView","getTimeMinuteTensView"};
    private final BiFunction<String,Bundle,Bundle> rawListener=(name,args)->null;
    private NativeOfficialClockFaces wrapped;
    private View rawRoot;
    private BiFunction<String,Bundle,Bundle> rawCalls;
    private BiPredicate<BiFunction<String,Bundle,Bundle>,Boolean> rawCallbacks;

    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();StringBuilder evidence=new StringBuilder();ClockTestActivity activity=null;
        Throwable[] failure={null};
        try{
            Context app=getTargetContext();
            activity=(ClockTestActivity)startActivitySync(new android.content.Intent(app,ClockTestActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            final ClockTestActivity host=activity;
            runOnMainSync(()->{try{create(host);}catch(Throwable error){failure[0]=error;}});
            checkFailure(failure);
            // Official lifecycle collection and render stages resume on the main queue.
            for(int pass=0;pass<3;pass++){
                waitForIdleSync();android.os.SystemClock.sleep(200);
                runOnMainSync(()->{try{layout(host);}catch(Throwable error){failure[0]=error;}});
                checkFailure(failure);
            }
            waitForIdleSync();
            runOnMainSync(()->{try{verify(evidence);}catch(Throwable error){failure[0]=error;}});
            checkFailure(failure);
            runOnMainSync(()->{try{
                verifyColorReset();host.content.removeView(wrapped);
                host.content.addView(wrapped,new FrameLayout.LayoutParams(-1,-2));layout(host);
            }catch(Throwable error){failure[0]=error;}});
            checkFailure(failure);waitForIdleSync();android.os.SystemClock.sleep(200);
            runOnMainSync(()->{try{layout(host);verify(evidence);}catch(Throwable error){failure[0]=error;}});
            checkFailure(failure);
            runOnMainSync(()->{try{
                host.content.removeView(wrapped);wrapped.close();wrapped.close();
                require(wrapped.getChildCount()==0,"wrapped release retained official root");
                wrapped.command("setTime",tick());require(wrapped.getChildCount()==0,"released wrapper accepted content");
                wrapped=null;
                rawCallbacks.test(rawListener,false);rawCalls.apply("release",null);rawCalls=null;
                host.content.removeAllViews();
            }catch(Throwable error){failure[0]=error;}});
            checkFailure(failure);
            evidence.append("NATIVE_OFFICIAL_IMMERSED_OK pipeline=SYSTEM_UI scene=KEYGUARD_IMMERSED raw_parity=true blur_restored=true color_reset=true reattach=true release=true\n");
            result.putString("stream",evidence.toString());finish(-1,result);
        }catch(Throwable error){result.putString("stream",evidence+android.util.Log.getStackTraceString(error));finish(0,result);}
        finally{
            if(activity!=null){final ClockTestActivity host=activity;runOnMainSync(()->{
                try{if(wrapped!=null)wrapped.close();}catch(Throwable ignored){}
                try{if(rawCalls!=null){rawCallbacks.test(rawListener,false);rawCalls.apply("release",null);}}catch(Throwable ignored){}
                host.content.removeAllViews();host.finish();
            });}
        }
    }

    @SuppressWarnings("unchecked")
    private void create(ClockTestActivity host)throws Exception{
        Context context=new SystemUiContext(host);
        require(context.createDisplayContext(host.getDisplay()).getPackageName().equals("com.android.systemui"),"display context lost SystemUI identity");
        NativeClockGeometry geometry=new NativeClockGeometry(context);
        wrapped=new NativeOfficialClockFaces(context,geometry);
        View nativeRoot=wrapped.getChildAt(0);
        require(nativeRoot.getClass().getName().equals("com.oplus.keyguard.clock.digital.ui.view.ClockViewRoot"),"wrapper did not load installed digital view");
        Object raw=((Function<String,Object>)NativeClockProvider.stockProvider(context)).apply("com.oplus.keyguard.clock.digital");
        rawRoot=((IntFunction<View>)raw).apply(1);rawCalls=(BiFunction<String,Bundle,Bundle>)raw;
        rawCallbacks=(BiPredicate<BiFunction<String,Bundle,Bundle>,Boolean>)raw;
        require(rawCallbacks.test(rawListener,true),"raw official callback rejected");
        Bundle defaults=rawCalls.apply("getDefaultClockStyleConfig",new Bundle());
        require(defaults!=null&&defaults.containsKey("styleData"),"raw official defaults missing");
        rawCalls.apply("setStyleData",defaults);
        rawCalls.apply("onClockStateChanged",scene());
        rawCalls.apply("setTime",tick());wrapped.command("setTime",tick());
        Bundle color=new Bundle();color.putInt("clockColorMode",3);color.putInt("clockPrimaryColor",Color.WHITE);
        rawCalls.apply("setClockColor",color);wrapped.color(3,Color.WHITE,null);
        rawCalls.apply("unblockRender",null);wrapped.command("unblockRender",null);
        // Both views remain attached. The raw instance sits behind the compact crop.
        host.content.addView(rawRoot,new FrameLayout.LayoutParams(-1,-1));
        host.content.addView(wrapped,new FrameLayout.LayoutParams(-1,-2));
        layout(host);
    }

    private void layout(ClockTestActivity host){
        int width=host.getResources().getDisplayMetrics().widthPixels,height=host.getResources().getDisplayMetrics().heightPixels;
        rawRoot.measure(exact(width),exact(height));rawRoot.layout(0,0,width,height);
        wrapped.measure(exact(width),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        wrapped.layout(0,0,width,wrapped.getMeasuredHeight());
        host.content.invalidate();
    }

    private void verify(StringBuilder evidence)throws Exception{
        View nativeRoot=wrapped.getChildAt(0);
        Object state=call(nativeRoot,"getRenderedViewState");
        require(state!=null,"official render lifecycle did not finish");
        require("SYSTEM_UI".equals(String.valueOf(call(state,"getProcess"))),"official pipeline did not use SystemUI process");
        require("KEYGUARD_IMMERSED".equals(String.valueOf(call(state,"getScene"))),"official pipeline did not enter immersed scene");
        Object single=immersed(nativeRoot),rawSingle=immersed(rawRoot);
        View time=(View)call(single,"getClockTimeView"),date=(View)call(single,"getDateMessageView");
        require(time.getVisibility()==View.VISIBLE&&date.getVisibility()==View.VISIBLE,"official date/time scene hidden");
        TextView day=(TextView)call(single,"getDateTextView"),week=(TextView)call(single,"getWeekTextView");
        require(day.getText().length()>0&&week.getText().length()>0,"official date/week content missing");
        require(day.getHeight()>0&&week.getHeight()>0,"official date/week not measured");
        compareText(day,(TextView)call(rawSingle,"getDateTextView"),nativeRoot,rawRoot,"date");
        compareText(week,(TextView)call(rawSingle,"getWeekTextView"),nativeRoot,rawRoot,"week");
        int nonemptyDigits=0,blurred=0;
        for(String getter:DIGITS){
            View digit=(View)call(single,getter),rawDigit=(View)call(rawSingle,getter);
            require(digit!=null&&rawDigit!=null,"official numeric control missing: "+getter);
            TextView text=(TextView)call(digit,"getVisibleTextView"),rawText=(TextView)call(rawDigit,"getVisibleTextView");
            compareText(text,rawText,nativeRoot,rawRoot,getter);
            if(text.getText().length()>0)nonemptyDigits++;
            blurred+=blurRoundTrip(text);
        }
        blurred+=blurRoundTrip(day)+blurRoundTrip(week);
        require(nonemptyDigits>=4,"official hour/minute text missing");require(blurred==7,"official date/digit blur controls missing");
        RectF digits=new RectF();wrapped.numberBounds(digits);
        require(!digits.isEmpty(),"official numeric bounds empty");
        require(digits.left>=-1&&digits.right<=wrapped.getWidth()+1&&digits.top>=-1&&digits.bottom<=wrapped.getHeight()+1,"official digits outside cropped body: "+digits
                +" crop="+wrapped.top()+","+wrapped.getHeight()+" root="+localRect(nativeRoot,wrapped)
                +" time="+localRect(time,wrapped)+" date="+localRect(date,wrapped)
                +" parent="+localRect((View)call(single,"getClockTimeViewParent"),wrapped));
        RectF body=new RectF();
        for(Object value:(List<?>)call(single,"getLayoutContentViews$KeyguardPersonalityClocks_release")){
            View leaf=(View)value;if(leaf.getVisibility()==View.VISIBLE&&leaf.getWidth()>0&&leaf.getHeight()>0)body.union(localRect(leaf,nativeRoot));
        }
        require(!body.isEmpty(),"official body bounds empty");
        require(Math.abs(wrapped.top()-Math.floor(body.top))<=1,"crop top differs from official content");
        require(Math.abs(wrapped.contentHeight()-(Math.ceil(body.bottom)-Math.floor(body.top)))<=1,"crop height differs from official content");
        require(wrapped.getHeight()==wrapped.contentHeight(),"reported crop height differs from measured wrapper");
        float dp=wrapped.getHeight()/wrapped.getResources().getDisplayMetrics().density;
        require(dp>20&&dp<90,"official lyrics height implausible: "+dp);
        Bitmap shot=Bitmap.createBitmap(wrapped.getWidth(),wrapped.getHeight(),Bitmap.Config.ARGB_8888);
        try{
            wrapped.draw(new Canvas(shot));int[] pixels=new int[shot.getWidth()*shot.getHeight()];
            shot.getPixels(pixels,0,shot.getWidth(),0,0,shot.getWidth(),shot.getHeight());int ink=0;
            for(int pixel:pixels)if(Color.alpha(pixel)>64)ink++;
            require(ink>100,"official crop draws no date/time pixels");
            evidence.append("official_raw_parity date=").append(day.getText()).append(" week=").append(week.getText())
                    .append(" numeric_controls=").append(nonemptyDigits).append(" blur_controls=").append(blurred)
                    .append(" top=").append(wrapped.top()).append(" height_dp=").append(dp).append(" ink=").append(ink).append('\n');
        }finally{shot.recycle();}
    }

    private static void compareText(TextView actual,TextView expected,View root,View raw,String name){
        require(actual!=null&&expected!=null,"official text missing: "+name);
        require(actual.getClass()==expected.getClass(),"official control class changed: "+name);
        require(actual.getText().toString().equals(expected.getText().toString()),"official text changed: "+name);
        require(Math.abs(actual.getTextSize()-expected.getTextSize())<.01f,"official text size changed: "+name);
        require(java.util.Objects.equals(actual.getTypeface(),expected.getTypeface()),"official typeface changed: "+name);
        require(java.util.Objects.equals(actual.getFontVariationSettings(),expected.getFontVariationSettings()),"official font axes changed: "+name);
        require(actual.getCurrentTextColor()==expected.getCurrentTextColor(),"official text color changed: "+name);
        RectF a=localRect(actual,root),b=localRect(expected,raw);
        require(Math.abs(a.left-b.left)<=1&&Math.abs(a.top-b.top)<=1&&Math.abs(a.right-b.right)<=1&&Math.abs(a.bottom-b.bottom)<=1,
                "official layout changed: "+name+" wrapped="+a+" raw="+b);
    }
    private static int blurRoundTrip(View text)throws Exception{
        Method getter=text.getClass().getMethod("getBlurRatio"),setter=text.getClass().getMethod("setBlurRatio",float.class);
        float original=((Number)getter.invoke(text)).floatValue();
        try{setter.invoke(text,12f);require(Math.abs(((Number)getter.invoke(text)).floatValue()-12f)<.001f,"official blur ratio not applied");}
        finally{setter.invoke(text,original);}
        require(Math.abs(((Number)getter.invoke(text)).floatValue()-original)<.001f,"official blur ratio not restored");return 1;
    }
    @SuppressWarnings("unchecked")
    private void verifyColorReset()throws Exception{
        java.lang.reflect.Field field=NativeOfficialClockFaces.class.getDeclaredField("calls");field.setAccessible(true);
        BiFunction<String,Bundle,Bundle> actual=(BiFunction<String,Bundle,Bundle>)field.get(wrapped);
        float[] defaults=rawCalls.apply("getClockColor",null).getFloatArray("clockColorHsl");
        float[] custom={120f,.5f,.25f};org.json.JSONArray depth=new org.json.JSONArray();for(float value:custom)depth.put(value);
        wrapped.color(3,Color.WHITE,depth);
        require(java.util.Arrays.equals(custom,actual.apply("getClockColor",null).getFloatArray("clockColorHsl")),"official HSL update lost");
        wrapped.color(3,Color.WHITE,null);
        require(java.util.Arrays.equals(defaults,actual.apply("getClockColor",null).getFloatArray("clockColorHsl")),"official default HSL retained previous custom depth");
    }
    private static Object immersed(View root)throws Exception{
        Object container=call(root,"getClockContainer");require(container!=null,"official clock container missing");
        for(Map.Entry<?,?> entry:((Map<?,?>)call(container,"getSceneViewMap")).entrySet())
            if("KEYGUARD_IMMERSED".equals(String.valueOf(entry.getKey())))return call(entry.getValue(),"getSingleClockView");
        throw new AssertionError("official immersed scene entry missing");
    }
    private static RectF localRect(View view,View ancestor){
        RectF rect=new RectF(0,0,view.getWidth(),view.getHeight());Matrix m=new Matrix(),parent=new Matrix(),inverse=new Matrix();
        view.transformMatrixToGlobal(m);ancestor.transformMatrixToGlobal(parent);
        require(parent.invert(inverse),"official coordinate transform singular");m.mapRect(rect);inverse.mapRect(rect);return rect;
    }
    private static Object call(Object target,String method)throws Exception{return target.getClass().getMethod(method).invoke(target);}
    private static int exact(int size){return View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY);}
    private static Bundle tick(){Bundle value=new Bundle();value.putLong("time",TIME);return value;}
    private static Bundle scene(){Bundle value=new Bundle();value.putInt("uiState",2);value.putInt("clockSize",2);value.putBoolean("isAnim",false);return value;}
    private static void checkFailure(Throwable[] failure)throws Throwable{if(failure[0]!=null)throw failure[0];}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}

    /** Preserve host identity through the display-context conversion used in production. */
    private static final class SystemUiContext extends ContextWrapper{
        SystemUiContext(Context base){super(base);}
        @Override public String getPackageName(){return "com.android.systemui";}
        @Override public Context createDisplayContext(Display display){return new SystemUiContext(getBaseContext().createDisplayContext(display));}
    }
}
