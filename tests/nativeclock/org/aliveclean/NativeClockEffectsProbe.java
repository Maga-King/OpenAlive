package org.aliveclean;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import org.json.JSONObject;

/** Hardware rendered scene/material regression probe, with no saved-system writes. */
public final class NativeClockEffectsProbe extends Activity {
    private FrameLayout stage;
    private FrameLayout host;
    private NativeClockHostFixture nativeHost;
    private NativeOriginalClockPlugin clock;
    private final ArrayList<String> ids=new ArrayList<>();
    private Bitmap wallpaper;
    private int index,checked,materialChecks,geometryTransitions;
    private long start;
    private boolean intermediate,midCapture,moving;
    private Object controller;
    private final StringBuilder detail=new StringBuilder();
    @Override public void onCreate(Bundle args){
        super.onCreate(args);stage=new FrameLayout(this);setContentView(stage);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stage.post(()->safe(()->{
            if(getApplicationInfo().targetSdkVersion!=37)throw new AssertionError("SDK37 required");
            int w=stage.getWidth(),h=stage.getHeight();wallpaper=NativeClockMaterial.softGradient(w,h);
            stage.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(),wallpaper));
            ids.add(NativeFlymeClockPlugin.ID);ids.add(NativeFlymeClockPlugin.HORIZONTAL_ID);ids.add(NativeFlymeArtworkPlugin.PERSPECTIVE);
            for(NativeHyperOsStyles.Style s:NativeHyperOsStyles.ALL)ids.add(s.id);
            bootstrap();
        }));
    }
    private void bootstrap()throws Exception{
        android.content.Context plugin=createPackageContext("com.oplus.keyguard.personality.clocks",CONTEXT_INCLUDE_CODE|CONTEXT_IGNORE_SECURITY);
        Class<?> type=plugin.getClassLoader().loadClass("com.oplus.keyguard.clock.digital.ui.controller.ColorController");
        controller=type.getConstructor(boolean.class).newInstance(true);
        Class<?> coloring=plugin.getClassLoader().loadClass("com.oplus.keyguard.clock.common.color.contract.IOplusColorListener");
        Object region=java.lang.reflect.Proxy.newProxyInstance(plugin.getClassLoader(),new Class<?>[]{coloring},(proxy,method,args)->{
            switch(method.getName()){
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy==args[0];
                case "toString": return "ClockSamplingProbe";
                case "isActive": return true;
                case "getSamplingGridBlocks": return new Rect[]{new Rect((Rect)args[0])};
                case "getColoringAreasRelativeToScreen": return new Rect[]{new Rect(0,0,wallpaper.getWidth(),wallpaper.getHeight())};
                case "getColoringAreasFixedOffset": return new int[]{0,0};
            }
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==float.class)return 1f;
            if(method.getReturnType()==int.class)return 0;
            return null;
        });
        type.getMethod("addColoringListener",coloring).invoke(controller,region);
        type.getMethod("setColoringConfig",int.class,int.class,boolean.class,int.class,boolean.class,boolean.class).invoke(controller,1,0,true,0,false,false);
        type.getMethod("setScreenShotEx",Bitmap.class,int[].class,boolean.class,Long.class).invoke(controller,wallpaper,new int[]{wallpaper.getWidth(),wallpaper.getHeight()},true,1L);
        java.lang.reflect.Field store=type.getSuperclass().getDeclaredField("wallpaperBitmapStore");store.setAccessible(true);Object value=store.get(controller);
        if(value.getClass().getMethod("getBitmap").invoke(value)!=null)throw new AssertionError("Live-first baseline no longer drops input");
        if(!NativeClockMaterialBootstrap.seed(controller,wallpaper,new int[]{wallpaper.getWidth(),wallpaper.getHeight()},2L))throw new AssertionError("Seed not attempted");
        start=android.os.SystemClock.uptimeMillis();waitSeed();
    }
    private void waitSeed(){stage.postDelayed(()->safe(()->{
        if(Boolean.TRUE.equals(controller.getClass().getMethod("isColoringPrepared").invoke(controller))){
            detail.append("stock_live_first_drop_reproduced=true sampler_recovered=true\n");
            controller.getClass().getMethod("release").invoke(controller);controller=null;next();return;
        }
        if(android.os.SystemClock.uptimeMillis()-start>10000)throw new AssertionError("Native sampler never became ready");
        waitSeed();
    }),50);}
    private void next()throws Exception{
        if(index==ids.size()){
            if(geometryTransitions==0)throw new AssertionError("Every transition lacked geometry");
            done("NATIVE_EFFECTS_OK styles="+index+" transitions="+checked+" glass="+materialChecks+"\ngeometryTransitions="+geometryTransitions+"\n"+detail);return;
        }
        // Setup scopes are bit flags: clock=2, widgets=4, base UI=8.
        nativeHost=new NativeClockHostFixture(this,6);
        Bundle sample=new Bundle();sample.putParcelable("wallpaperBitmap",wallpaper);
        nativeHost.customClock.apply("setWallpaperBitmap",sample);
        JSONObject style=new JSONObject(nativeHost.read());
        style.put("pkg",ids.get(index)).put("clockStyleConfig",NativeClockHostFixture.config(ids.get(index)));
        NativeClockEditor.findHost((View)nativeHost.root).write(style.toString());clock=nativeHost.customClock;
        if(clock==null||!ids.get(index).equals(clock.id))throw new AssertionError("Native host did not select the requested clock");
        // Exercise the installed ColorOS root/container, not only a look-alike.
        // This remains an isolated app, not the real SystemUI process.
        host=new FrameLayout(this);
        stage.addView(host,new FrameLayout.LayoutParams(-1,-1));
        host.addView((View)nativeHost.root,new FrameLayout.LayoutParams(-1,-1));
        if((index&1)!=0){host.setPivotX(stage.getWidth()/2f);host.setPivotY(0);host.setScaleX(.72f);host.setScaleY(.72f);}
        scene(1,false);scene(2,false);
        Bundle time=new Bundle();time.putLong("time",System.currentTimeMillis());clock.apply("setTime",time);
        JSONObject config=new JSONObject(clock.apply("getStyleData",null).getString("styleData"));config.put("coloringType",1);
        Bundle args=new Bundle();args.putString("styleData",config.toString());clock.apply("setStyleData",args);
        stage.postDelayed(()->safe(()->{
            if(!clock.materialApplied())throw new AssertionError("Initial glass absent "+ids.get(index)+" "+NativeClockLoadState.snapshot());
            assertWidgets(true);
            begin(3);
        }),120);
    }
    private void scene(int state,boolean anim){
        Bundle args=new Bundle();args.putInt("uiState",state);args.putInt("clockSize",1);args.putBoolean("isAnim",anim);
        try{nativeHost.call("onClockStateChanged",args);}catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private void begin(int state)throws Exception{
        android.graphics.RectF before=new android.graphics.RectF();
        if(clock.apply(8) instanceof NativeHyperOsFace)((NativeHyperOsFace)clock.apply(8)).numberBounds(before);
        detail.append(ids.get(index)).append(" state=").append(state).append(" from=").append(before).append('\n');
        scene(state,true);start=android.os.SystemClock.uptimeMillis();intermediate=false;moving=false;midCapture=false;waitTransition(state);
    }
    private void waitTransition(int state){stage.postDelayed(()->safe(()->{
        long elapsed=android.os.SystemClock.uptimeMillis()-start;
        View view=clock.apply(8);
        if(clock.sceneAnimating()&&view.getAlpha()>0){
            if(view.getAlpha()!=1)throw new AssertionError("Crossfade returned "+ids.get(index));
            int visible=0;for(int i=0;i<clock.clockContainer.getChildCount();i++)
                if(clock.clockContainer.getChildAt(i).getVisibility()==View.VISIBLE)visible++;
            if(visible!=1)throw new AssertionError("Multiple clock faces visible");
            // Several original faces keep digit size but change their position.
            intermediate=true;
            if(view.getScaleY()!=1||view.getTranslationX()!=0||view.getTranslationY()!=0)moving=true;
            if(!clock.materialApplied())throw new AssertionError("Glass absent during handoff "+ids.get(index));
            checkClipping(view);
            if(state==2&&index==0&&!midCapture&&elapsed>100){
                midCapture=true;capture("clock-effects-mid.png",()->{});
            }
        }
        if(clock.sceneAnimating()){
            if(elapsed>3500)throw new AssertionError("Scene did not settle "+ids.get(index)+" "+NativeClockLoadState.snapshot());
            waitTransition(state);return;
        }
        if(!intermediate){
            if(view instanceof NativeHyperOsFace){
                java.lang.reflect.Field field=NativeHyperOsFace.class.getDeclaredField("numbers");field.setAccessible(true);
                for(Object item:(java.util.List<?>)field.get(view)){
                    View number=(View)item;detail.append(number).append(" alpha=").append(number.getAlpha()).append('\n');
                    try{detail.append(number.getClass().getMethod("getTextBoundsWithPosition").invoke(number)).append('\n');}catch(Exception ignored){}
                }
            }
            throw new AssertionError("No intermediate frames "+ids.get(index)+" "+NativeClockLoadState.snapshot()+"\n"+detail+"\n"+view);
        }
        if(view.getAlpha()!=1||view.getScaleY()!=1||view.getTranslationY()!=0)throw new AssertionError("Scene residue");
        if(clock.clockContainer.getChildCount()!=4)throw new AssertionError("Original face lost after overlay");
        checked++;
        assertWidgets(state==2||state==5);
        if(moving)geometryTransitions++;
        if(state==3){begin(2);return;}
        material();
    }),16);}
    private void checkClipping(View face){
        Rect visible=clock.apply("getClockVisibleRect",null).getParcelable("visibleRect");
        android.graphics.RectF ink=new android.graphics.RectF(visible);
        if(face instanceof NativeHyperOsFace){
            ((NativeHyperOsFace)face).contentBounds(ink);
            android.graphics.Matrix full=new android.graphics.Matrix();face.transformMatrixToGlobal(full);full.mapRect(ink);
        }
        // Only screen-visible pixels are expected; some original large presets
        // intentionally extend beyond the display at their unscaled endpoint.
        android.graphics.Matrix matrix=new android.graphics.Matrix();host.transformMatrixToGlobal(matrix);
        android.graphics.RectF screen=new android.graphics.RectF(0,0,host.getWidth(),host.getHeight());matrix.mapRect(screen);
        if(clock.id.contains(".doodle.")){
            if(ink.left<screen.left-2||ink.right>screen.right+2)throw new AssertionError("Weekday artwork outside preview "+ink+" "+screen);
        }
        if(!ink.intersect(screen))return;
        for(android.view.ViewParent p=face.getParent();p instanceof android.view.ViewGroup;p=p.getParent()){
            android.view.ViewGroup group=(android.view.ViewGroup)p;
            if(group==host)break;
            if(!group.getClipChildren()&&!group.getClipToPadding()&&group.getClipBounds()==null&&!group.getClipToOutline())continue;
            android.graphics.RectF limit=new android.graphics.RectF(0,0,group.getWidth(),group.getHeight());
            if(group.getClipToPadding())limit.set(group.getPaddingLeft(),group.getPaddingTop(),group.getWidth()-group.getPaddingRight(),group.getHeight()-group.getPaddingBottom());
            Rect clip=group.getClipBounds();if(clip!=null)limit.intersect(new android.graphics.RectF(clip));
            matrix.reset();group.transformMatrixToGlobal(matrix);matrix.mapRect(limit);limit.inset(-2,-2);
            if(!limit.contains(ink))throw new AssertionError("Animated digits clipped by "+group+" ink="+ink+" limit="+limit);
        }
    }
    private void material()throws Exception{
        stage.postDelayed(()->safe(()->{
            if(!clock.materialApplied())throw new AssertionError("Glass absent "+ids.get(index)+" "+NativeClockLoadState.snapshot());
            materialChecks++;
            if(index==0||index==3||index==17)capture("clock-effects-"+index+".png",()->safe(this::endStyle));else endStyle();
        }),100);
    }
    private void endStyle()throws Exception{
        verifyColorPixels(0xffff2020);
        verifyColorPixels(0xff2040ff);
        setDraftColor(1,0xffffffff);
        setDraftColor(5,0xffffffff);
        if(!clock.materialApplied())throw new AssertionError("Soft material absent "+clock.id);
        if(index==0){
            clock.apply("setWallpaperBitmap",null);
            if(!clock.materialApplied())throw new AssertionError("Soft mode depends on wallpaper callback");
            stage.setBackgroundColor(android.graphics.Color.BLACK);
            stage.postDelayed(()->capture("clock-effects-soft.png",()->safe(this::endSoft)),100);
            return;
        }
        endSoft();
    }
    private void endSoft()throws Exception{
        if(index==0){
            verifyWallpaperSoft(false);return;
        }
        finishStyle();
    }
    private Bitmap softFirst,softSample;
    private void verifyWallpaperSoft(boolean second)throws Exception{
        setDraftColor(6,0xffffffff);
        softSample=Bitmap.createBitmap(128,256,Bitmap.Config.ARGB_8888);
        softSample.eraseColor(second?0xff2040ff:0xffff2020);
        Bundle input=new Bundle();input.putParcelable("wallpaperBitmap",softSample);clock.apply("setWallpaperBitmap",input);
        if(!clock.materialApplied())throw new AssertionError("Wallpaper soft material absent");
        stage.setBackgroundColor(android.graphics.Color.BLACK);
        stage.postDelayed(()->safe(()->{
            Bitmap frame=Bitmap.createBitmap(getWindow().getDecorView().getWidth(),getWindow().getDecorView().getHeight(),Bitmap.Config.ARGB_8888);
            PixelCopy.request(getWindow(),frame,status->safe(()->{
                if(status!=PixelCopy.SUCCESS)throw new AssertionError("Soft material copy failed "+status);
                if(!second){softFirst=frame;verifyWallpaperSoft(true);return;}
                int changed=0;
                for(int y=0;y<frame.getHeight();y+=2)for(int x=0;x<frame.getWidth();x+=2){
                    int a=softFirst.getPixel(x,y),b=frame.getPixel(x,y);
                    if(android.graphics.Color.red(a)>android.graphics.Color.red(b)+30&&android.graphics.Color.blue(b)>android.graphics.Color.blue(a)+30)changed++;
                }
                frame.recycle();softFirst.recycle();softFirst=null;
                if(changed<100)throw new AssertionError("Soft material ignored real wallpaper: "+changed);
                detail.append("wallpaper_soft_sampled_pixels=").append(changed).append('\n');
                Bundle original=new Bundle();original.putParcelable("wallpaperBitmap",wallpaper);clock.apply("setWallpaperBitmap",original);
                stage.postDelayed(()->capture("clock-effects-wallpaper-soft.png",()->safe(this::finishStyle)),100);
            }),new Handler());
        }),120);
    }
    private void finishStyle()throws Exception{
        if(index==0){
            Bundle sample=new Bundle();sample.putParcelable("wallpaperBitmap",wallpaper);clock.apply("setWallpaperBitmap",sample);
            stage.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(),wallpaper));
        }
        setDraftColor(1,0xffffffff);
        scene(5,false);assertWidgets(true);
        scene(3,false);assertWidgets(false);if(!clock.materialApplied())throw new AssertionError("AOD glass absent");
        scene(2,true);scene(3,true);scene(2,false);
        if(clock.sceneAnimating())throw new AssertionError("Interrupted scene retained spring");
        nativeHost.close();nativeHost=null;clock=null;stage.removeAllViews();index++;stage.post(()->safe(this::next));
    }
    private void setDraftColor(int mode,int color)throws Exception{
        JSONObject outer=new JSONObject(nativeHost.read()),config=new JSONObject(outer.getString("clockStyleConfig"));
        NativeOriginalClockPlugin.writeColorMode(config,mode);config.put("color",color);outer.put("clockStyleConfig",config.toString());
        NativeClockEditor.findHost((View)nativeHost.root).write(outer.toString());
    }
    private void verifyColorPixels(int color)throws Exception{
        setDraftColor(3,color);
        View face=clock.apply(8);
        Bitmap raster=Bitmap.createBitmap(face.getWidth(),face.getHeight(),Bitmap.Config.ARGB_8888);
        face.draw(new Canvas(raster));
        int[] pixels=new int[raster.getWidth()*raster.getHeight()];raster.getPixels(pixels,0,raster.getWidth(),0,0,raster.getWidth(),raster.getHeight());
        int colored=0;
        for(int pixel:pixels)if(android.graphics.Color.alpha(pixel)>180&&
                Math.abs(android.graphics.Color.red(pixel)-android.graphics.Color.red(color))<12&&
                Math.abs(android.graphics.Color.blue(pixel)-android.graphics.Color.blue(color))<12)colored++;
        raster.recycle();
        if(colored<100)throw new AssertionError("Preview did not paint chosen color: "+clock.id+" pixels="+colored+" config="+nativeHost.read());
        detail.append(clock.id).append(" palettePixels=").append(colored).append('\n');
    }
    private void assertWidgets(boolean visible)throws Exception{
        // The native scene controller owns the plugin root. The outer container
        // belongs to SystemUI's shade/one-shot alpha flow, absent in this host.
        android.view.ViewGroup outer=(android.view.ViewGroup)nativeHost.widgetContainer();
        // Non-SystemUI hosts unload widgets in low-power AOD (state 3).
        if(outer.getChildCount()==0){if(!visible)return;throw new AssertionError("Native widget plugin missing");}
        View container=outer.getChildAt(0);
        if(container.getAlpha()!=(visible?1f:0f))throw new AssertionError("Native widget scene alpha="+container.getAlpha()+" expected visible="+visible+" style="+ids.get(index));
    }
    private void capture(String name,Runnable next){
        Bitmap out=Bitmap.createBitmap(getWindow().getDecorView().getWidth(),getWindow().getDecorView().getHeight(),Bitmap.Config.ARGB_8888);
        PixelCopy.request(getWindow(),out,result->{try{
            if(result!=PixelCopy.SUCCESS)throw new AssertionError("PixelCopy "+result);
            try(java.io.FileOutputStream file=openFileOutput(name,MODE_PRIVATE)){out.compress(Bitmap.CompressFormat.PNG,100,file);}
            out.recycle();next.run();
        }catch(Throwable failure){done(android.util.Log.getStackTraceString(failure));}},new Handler());
    }
    private interface Task{void run()throws Exception;}
    private void safe(Task task){try{task.run();}catch(Throwable failure){done(android.util.Log.getStackTraceString(failure));}}
    private void done(String result){
        if(nativeHost!=null){try{nativeHost.close();}catch(Exception ignored){}nativeHost=null;clock=null;}
        try(java.io.FileOutputStream file=openFileOutput("effects.txt",MODE_PRIVATE)){file.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}
        android.util.Log.i("OpenAliveClockProbe",result);finish();
    }
}
