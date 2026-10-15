package org.aliveclean;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Crops only outer whitespace; the installed digital plugin owns all content. */
final class NativeOfficialClockFaces extends ViewGroup implements AutoCloseable {
    private final BiFunction<String,Bundle,Bundle> calls;
    private final BiPredicate<BiFunction<String,Bundle,Bundle>,Boolean> callbacks;
    private final View nativeRoot;
    private final BiFunction<String,Bundle,Bundle> listener=this::callback;
    private int top,height;
    private boolean closed;
    private Runnable geometryChanged;
    private BiFunction<String,Bundle,Bundle> hostCallback;
    private int colorMode=-1,primaryColor;
    private float[] defaultDepth,lastDepth;
    private android.view.ViewTreeObserver observedTree;
    private final java.lang.reflect.Method containerGetter;
    private java.lang.reflect.Method scenesGetter,singleGetter,contentsGetter,timeGetter,dateGetter,digitsGetter;
    private View clockTime;
    private final android.view.ViewTreeObserver.OnPreDrawListener measured=()->{refreshGeometry();return true;};

    @SuppressWarnings("unchecked")
    NativeOfficialClockFaces(Context host,NativeClockGeometry geometry)throws Exception{
        super(host);
        top=geometry.immersedTop();height=geometry.immersedDateSize()+geometry.immersedTimeSize();
        Function<String,Object> provider=(Function<String,Object>)NativeClockProvider.stockProvider(host);
        Object plugin=provider.apply("com.oplus.keyguard.clock.digital");
        calls=(BiFunction<String,Bundle,Bundle>)plugin;
        callbacks=(BiPredicate<BiFunction<String,Bundle,Bundle>,Boolean>)plugin;
        nativeRoot=((IntFunction<View>)plugin).apply(1);
        if(nativeRoot==null)throw new IllegalStateException("stock digital root unavailable");
        containerGetter=nativeRoot.getClass().getMethod("getClockContainer");
        try{
            addView(nativeRoot,new LayoutParams(-1,-1));
            callbacks.test(listener,true);
            Bundle defaults=calls.apply("getDefaultClockStyleConfig",new Bundle());
            if(defaults==null||!defaults.containsKey("styleData"))throw new IllegalStateException("Official clock defaults unavailable");
            org.json.JSONArray hsl=new org.json.JSONObject(defaults.getString("styleData")).getJSONArray("primaryColorDepthHSL");
            defaultDepth=new float[]{(float)hsl.getDouble(0),(float)hsl.getDouble(1),(float)hsl.getDouble(2)};
            calls.apply("setStyleData",defaults);
            // This private instance always represents the official lyrics scene.
            Bundle scene=new Bundle();scene.putInt("uiState",2);scene.putInt("clockSize",2);scene.putBoolean("isAnim",false);
            calls.apply("onClockStateChanged",scene);
            Bundle tick=new Bundle();tick.putLong("time",System.currentTimeMillis());calls.apply("setTime",tick);
            calls.apply("unblockRender",null);
        }catch(Exception|Error failure){
            try{close();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
    }
    private Bundle callback(String command,Bundle args){
        if(closed)return null;
        // Actual official leaf bounds determine the crop in both SystemUI and
        // editor hosts; avoid alternating reported font metrics and view bounds.
        // The outer provider owns widgets, style data and host geometry.
        if("requestWallpaperBitmap".equals(command)&&hostCallback!=null)return hostCallback.apply(command,args);
        return null;
    }
    void callbacks(BiFunction<String,Bundle,Bundle> host,Runnable changed){hostCallback=host;geometryChanged=changed;}
    int top(){return top;}
    int contentHeight(){return height;}
    private void refreshGeometry(){
        if(closed)return;
        try{
            Object container=containerGetter.invoke(nativeRoot);if(container==null)return;
            if(scenesGetter==null)scenesGetter=container.getClass().getMethod("getSceneViewMap");
            java.util.Map<?,?> scenes=(java.util.Map<?,?>)scenesGetter.invoke(container);
            Object single=null;
            for(java.util.Map.Entry<?,?> entry:scenes.entrySet())if("KEYGUARD_IMMERSED".equals(String.valueOf(entry.getKey()))){
                if(singleGetter==null)singleGetter=entry.getValue().getClass().getMethod("getSingleClockView");
                single=singleGetter.invoke(entry.getValue());break;
            }
            if(single==null)return;
            if(contentsGetter==null){
                contentsGetter=single.getClass().getMethod("getLayoutContentViews$KeyguardPersonalityClocks_release");
                timeGetter=single.getClass().getMethod("getClockTimeView");dateGetter=single.getClass().getMethod("getDateMessageView");
            }
            // This private instance is a keyguard lyrics renderer even inside
            // the editor/test process, whose stock baseline may be LAUNCHER.
            View time=(View)timeGetter.invoke(single),date=(View)dateGetter.invoke(single);
            clockTime=time;
            if(time!=null)time.setVisibility(VISIBLE);if(date!=null)date.setVisibility(VISIBLE);
            java.util.List<?> children=(java.util.List<?>)contentsGetter.invoke(single);
            RectF total=new RectF();Matrix rootMatrix=new Matrix(),inverse=new Matrix();nativeRoot.transformMatrixToGlobal(rootMatrix);
            if(!rootMatrix.invert(inverse))return;
            for(Object child:children){View view=(View)child;if(view.getVisibility()!=VISIBLE||view.getWidth()==0||view.getHeight()==0)continue;
                RectF area=new RectF(0,0,view.getWidth(),view.getHeight());Matrix m=new Matrix();view.transformMatrixToGlobal(m);m.mapRect(area);inverse.mapRect(area);total.union(area);
            }
            if(total.isEmpty())return;
            // Screen-to-local inversion during the outer spring can add tiny
            // float errors to integer layout bounds; do not request a new layout
            // every frame because 320 became 319.99997.
            int nextTop=Math.round(total.top),nextHeight=Math.round(total.bottom)-nextTop;
            if(nextTop!=top||nextHeight!=height){top=nextTop;height=nextHeight;requestLayout();if(geometryChanged!=null){removeCallbacks(geometryChanged);post(geometryChanged);}}
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("Official immersed geometry",unavailable);}
    }
    void command(String name,Bundle args){if(!closed)calls.apply(name,args);}
    void color(int mode,int value,org.json.JSONArray depth){
        int nativeMode=mode>=5?3:mode==0?2:mode;
        int nativeColor=mode>=5?0xffffffff:value;
        float[] effectiveDepth=depth!=null&&depth.length()==3
                ?new float[]{(float)depth.optDouble(0),(float)depth.optDouble(1),(float)depth.optDouble(2)}:defaultDepth;
        if(nativeMode==colorMode&&nativeColor==primaryColor&&java.util.Arrays.equals(lastDepth,effectiveDepth))return;
        colorMode=nativeMode;primaryColor=nativeColor;lastDepth=effectiveDepth.clone();
        Bundle args=new Bundle();args.putInt("clockColorMode",nativeMode);args.putInt("clockPrimaryColor",nativeColor);
        args.putFloatArray("clockColorHsl",effectiveDepth.clone());
        command("setClockColor",args);
    }
    void numberBounds(RectF out){
        out.setEmpty();if(closed)return;
        if(clockTime==null)refreshGeometry();if(clockTime==null)return;
        // The stock API adds digit offsets to its fullscreen parent's clipped
        // global rect. Cropping that parent changes its origin, so read the
        // original digit views directly instead of adding the crop twice.
        try{
            if(digitsGetter==null)digitsGetter=clockTime.getClass().getMethod("getDigitalTimeViews");
            Matrix global=new Matrix(),inverse=new Matrix();transformMatrixToGlobal(global);
            if(!global.invert(inverse))return;
            for(Object child:(java.util.List<?>)digitsGetter.invoke(clockTime)){
                View digit=(View)child;if(digit.getVisibility()!=VISIBLE||digit.getWidth()==0||digit.getHeight()==0)continue;
                RectF area=new RectF(0,0,digit.getWidth(),digit.getHeight());Matrix m=new Matrix();
                digit.transformMatrixToGlobal(m);m.mapRect(area);inverse.mapRect(area);out.union(area);
            }
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("Official immersed numeric bounds",unavailable);}
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),screen=getResources().getDisplayMetrics().heightPixels;
        nativeRoot.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(screen,MeasureSpec.EXACTLY));
        setMeasuredDimension(width,resolveSize(height,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){nativeRoot.layout(0,-top,r-l,nativeRoot.getMeasuredHeight()-top);}
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        if(!closed){observedTree=getViewTreeObserver();observedTree.addOnPreDrawListener(measured);}
    }
    private void removeMeasurementObserver(){
        if(observedTree!=null&&observedTree.isAlive())observedTree.removeOnPreDrawListener(measured);
        observedTree=null;
    }
    @Override protected void onDetachedFromWindow(){removeMeasurementObserver();super.onDetachedFromWindow();}
    @Override public void close(){
        if(closed)return;
        closed=true;hostCallback=null;if(geometryChanged!=null)removeCallbacks(geometryChanged);geometryChanged=null;
        removeMeasurementObserver();
        try{callbacks.test(listener,false);}finally{try{calls.apply("release",null);}finally{clockTime=null;removeAllViews();}}
    }
}
