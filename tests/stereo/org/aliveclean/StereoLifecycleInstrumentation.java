package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real RenderLoop + preferences + ImageReader output; no wallpaper/system settings. */
public final class StereoLifecycleInstrumentation extends Instrumentation {
    private static final String TRACE="stereo-lifecycle.txt";
    private final StringBuilder log=new StringBuilder();
    private boolean video,ordinary;
    private int checks,frames;
    private Harness current;

    @Override public void onCreate(Bundle args){
        super.onCreate(args);video=args!=null&&"true".equals(args.getString("video"));
        ordinary=args!=null&&"true".equals(args.getString("ordinary"));start();
    }
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable failure=null;
        try{
            sceneOptionsRoundTrip();
            for(int id=501;id<=503;id++){
                current=new Harness(id,false);current.stereo();current.close();current=null;
            }
            current=new Harness(501,true);current.preview();current.close();current=null;
            if(video){current=new Harness(401,false);current.dynamic("video");current.close();current=null;}
            if(ordinary){current=new Harness(301,false);current.dynamic("gradient");current.close();current=null;}
        }catch(Throwable error){failure=error;}
        finally{
            if(current!=null)try{current.close();}catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
            try(OutputStream out=getTargetContext().openFileOutput(TRACE,Context.MODE_PRIVATE)){
                out.write(log.toString().getBytes(StandardCharsets.UTF_8));
            }catch(Throwable output){if(failure==null)failure=output;else failure.addSuppressed(output);}
        }
        if(failure==null){result.putString("stream",log+"STEREO_LIFECYCLE_OK checks="+checks+" frames="+frames+" trace="+TRACE+"\n");finish(-1,result);}
        else{result.putString("stream",log+"STEREO_LIFECYCLE_FAILED\n"+android.util.Log.getStackTraceString(failure));finish(0,result);}
    }
    private void require(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private void note(String value){log.append(value).append('\n');Bundle status=new Bundle();status.putString("stream",value+"\n");sendStatus(1,status);}
    private static void delay(long millis)throws InterruptedException{
        // Avoid SystemClock.sleep's uptime-based remainder/retry loop. A
        // bounded timed park also lets interruption terminate the test.
        new CountDownLatch(1).await(millis,TimeUnit.MILLISECONDS);
    }
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private interface Step {void run()throws Exception;}
    private static void on(Handler handler,Step step)throws Exception{
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();
        if(!handler.post(()->{try{step.run();}catch(Throwable failed){error.set(failed);}finally{done.countDown();}}))throw new AssertionError("Render handler rejected work");
        if(!done.await(10,TimeUnit.SECONDS))throw new AssertionError("Render handler stalled");
        if(error.get()!=null)throw new AssertionError("Render handler assertion",error.get());
    }
    private void sceneOptionsRoundTrip(){
        Context app=getTargetContext();SharedPreferences source=app.getSharedPreferences("lifecycle.options.source",0),saved=app.getSharedPreferences("lifecycle.options.saved",0);
        try{
            for(int value:new int[]{-50,0,37,50})for(boolean aod:new boolean[]{false,true}){
                require(source.edit().clear().putInt("cosmic",502).putInt("stereo_sensitivity",value).putBoolean("stereo_aod_animation",aod).commit(),"source prefs commit");
                SceneOptions snapshot=new SceneOptions(source);require(snapshot.save(saved),"SceneOptions save");SceneOptions restored=new SceneOptions(saved);
                require(restored.cosmic==502&&restored.stereoSensitivity==value&&restored.stereoAodAnimation==aod,"SceneOptions sensitivity/AOD roundtrip");
            }
            source.edit().putInt("stereo_sensitivity",999).commit();require(new SceneOptions(source).stereoSensitivity==50,"upper sensitivity clamp");
            source.edit().putInt("stereo_sensitivity",-999).commit();require(new SceneOptions(source).stereoSensitivity==-50,"lower sensitivity clamp");
            source.edit().clear().commit();SceneOptions defaults=new SceneOptions(source);require(defaults.stereoSensitivity==0&&defaults.stereoAodAnimation,"original defaults");
            note("SceneOptions save/restore/defaults/clamps OK");
        }finally{source.edit().clear().commit();saved.edit().clear().commit();}
    }

    private final class Harness implements AutoCloseable {
        final int id;
        final boolean preview;
        final SharedPreferences prefs;
        final RenderLoop loop;
        final Handler handler,drain;
        final HandlerThread drainThread=new HandlerThread("StereoTestImages");
        final ImageReader reader;
        final Surface surface;
        final AtomicInteger output=new AtomicInteger();
        final AtomicReference<Throwable> imageFailure=new AtomicReference<>();
        Object renderer,window,egl;
        StereoSensor sensor;
        boolean closed;
        long sensorTime=1_000_000_000L;

        Harness(int id,boolean preview)throws Exception{
            this.id=id;this.preview=preview;Context app=getTargetContext();
            stage("creating production RenderLoop");
            prefs=app.getSharedPreferences(preview?SceneOptions.DRAFT:"lifecycle.scene."+id,0);
            require(prefs.edit().clear().putInt("cosmic",id).putBoolean("cosmic_continuous_aod",true).putBoolean("cosmic_continuous_home",true)
                    .putInt("stereo_sensitivity",0).putBoolean("stereo_aod_animation",true).commit(),"initial preferences");
            drainThread.start();drain=new Handler(drainThread.getLooper());
            reader=ImageReader.newInstance(180,390,PixelFormat.RGBA_8888,4);surface=reader.getSurface();
            reader.setOnImageAvailableListener(images->{
                try{Image image;while((image=images.acquireNextImage())!=null){try{output.incrementAndGet();}finally{image.close();}}}
                catch(Throwable error){imageFailure.compareAndSet(null,error);}
            },drain);
            loop=new RenderLoop(app,preview?SceneOptions.DRAFT:"lifecycle.scene."+id);handler=(Handler)field(loop,"handler");
            try{
                loop.mode(0,false);loop.attach(surface,180,390);barrier();waitFrames(0,1,8000,"initial ImageReader frame");
                on(handler,()->{
                    renderer=field(loop,"cosmic");window=field(loop,"window");egl=field(loop,"egl");sensor=(StereoSensor)field(loop,"stereoSensor");
                    require(renderer!=null,"production renderer initialized "+id);
                    require((Integer)field(loop,"mode")==0,"start in scene0/AOD");
                    if(id>=501){require(renderer instanceof StereoRenderer,"production stereo renderer");require(sensor!=null&&field(sensor,"gyro")!=null,"device gyroscope required");}
                });
            }catch(Exception|Error error){close();throw error;}
        }
        void barrier()throws Exception{on(handler,()->{});}
        void stage(String label){note("STEREO_STAGE id="+id+" preview="+preview+" stage="+label+" frames="+output.get());}
        void settle()throws Exception{barrier();delay(140);on(drain,()->{});checkImages();}
        void checkImages(){require(imageFailure.get()==null,"ImageReader drain failure: "+imageFailure.get());}
        void same()throws Exception{on(handler,()->{
            require(field(loop,"cosmic")==renderer,"renderer identity preserved");require(field(loop,"window")==window&&field(loop,"egl")==egl,"EGL surface/context preserved");
            require(field(loop,"attachedSurface")==surface&&surface.isValid(),"attached surface retained");
        });}
        void active(boolean expected)throws Exception{on(handler,()->require((Boolean)field(sensor,"active")==expected,"sensor active="+expected));}
        void gyro(float value)throws Exception{on(handler,()->{
            Constructor<SensorEvent> constructor=SensorEvent.class.getDeclaredConstructor(int.class);constructor.setAccessible(true);
            SensorEvent event=constructor.newInstance(3);event.sensor=(Sensor)field(sensor,"gyro");event.timestamp=(sensorTime+=16_666_667L);event.values[0]=value;event.values[1]=value*.7f;event.values[2]=0;
            sensor.onSensorChanged(event);
        });}
        void pulse()throws Exception{gyro(1.2f);delay(20);gyro(1.2f);delay(20);gyro(1.2f);}
        void waitFrames(int before,int minimum,long timeout,String label)throws Exception{
            stage("wait frames: "+label);
            long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
            int maximum=(int)Math.max(1,(timeout+19)/20);
            for(int attempt=0;attempt<maximum&&output.get()-before<minimum;attempt++){
                if(System.nanoTime()>=deadline)break;
                delay(20);checkImages();
            }
            require(output.get()-before>=minimum,label+" actual="+(output.get()-before));barrier();
        }
        void quiet(String label)throws Exception{
            stage("quiet: "+label);
            settle();int before=output.get();delay(400);on(drain,()->{});checkImages();
            require(output.get()==before,label+" submitted "+(output.get()-before)+" unexpected frames");
            on(handler,()->require(!(Boolean)field(loop,"framePosted"),label+" Choreographer stopped"));
        }
        void prefs(int sensitivity,boolean aod)throws Exception{
            stage("preferences sensitivity="+sensitivity+" aod="+aod);
            require(prefs.edit().putInt("stereo_sensitivity",sensitivity).putBoolean("stereo_aod_animation",aod).commit(),"hot preferences commit");
            // SharedPreferences dispatches listeners on main. Wait for that
            // delivery before observing the queued GL-thread reload.
            runOnMainSync(()->{});barrier();settle();same();
            on(handler,()->{
                SceneOptions options=(SceneOptions)field(loop,"renderedOptions");
                require(options.stereoSensitivity==sensitivity&&options.stereoAodAnimation==aod,"RenderLoop consumed live SceneOptions");
                StereoMotion motion=(StereoMotion)((WallpaperRenderer)renderer).motion();
                require(Math.abs((Float)field(motion,"sensitivityGain")-StereoMotion.multiplier(sensitivity))<.00001f,"live sensitivity gain");
                require((Boolean)field(motion,"allowAod")==aod,"live AOD motion gate");
            });
        }
        void stereo()throws Exception{
            stage("AOD enabled gyro");
            active(true);int before=output.get();pulse();waitFrames(before,2,3000,"scene0 enabled gyro wakes real RenderLoop");
            on(handler,()->require(((WallpaperRenderer)renderer).motion().active(),"scene0 gyro motion is active"));
            prefs(37,false);active(false);delay(1800);quiet("AOD disabled");
            before=output.get();pulse();quiet("AOD disabled gyro ignored");require(output.get()==before,"AOD disabled produced no gyro frames");
            stage("AOD disabled, home gyro enabled");
            loop.mode(2,false);barrier();active(true);before=output.get();pulse();waitFrames(before,1,3000,"AOD-off still permits home gyro");same();
            loop.mode(0,false);barrier();active(false);delay(1800);quiet("return to disabled AOD");
            prefs(-25,true);active(true);before=output.get();pulse();waitFrames(before,2,3000,"AOD re-enabled resumes gyro");
            stage("hidden");
            loop.visible(false);barrier();active(false);same();quiet("black screen hidden");
            stage("hidden gyro pulse");
            before=output.get();pulse();loop.resumeDisplay();quiet("hidden gyro/display wake ignored");require(output.get()==before,"hidden has zero frames");
            stage("visible recovery");
            loop.visible(true);barrier();active(true);before=output.get();pulse();waitFrames(before,1,3000,"visible resumes output");same();
            stage("opaque backdrop");
            loop.backdrop(true);barrier();active(false);same();quiet("opaque lyrics backdrop");
            stage("covered gyro pulse");
            before=output.get();pulse();loop.resumeDisplay();quiet("covered gyro/display wake ignored");require(output.get()==before,"covered has zero frames");
            // Preference reload and scene transitions cannot wake a covered
            // renderer; uncovering must use the existing surface and object.
            prefs(12,true);loop.mode(2,false);barrier();active(false);quiet("covered prefs/scene update");same();
            stage("uncover");
            loop.backdrop(false);barrier();active(true);before=output.get();pulse();waitFrames(before,1,3000,"uncover resumes");same();
            loop.visible(false);loop.backdrop(true);loop.backdrop(false);barrier();active(false);quiet("uncover keeps hidden paused");
            loop.visible(true);loop.mode(0,false);barrier();active(true);before=output.get();pulse();waitFrames(before,1,3000,"second visible recovery");
            note("Stereo "+id+": AOD/home sensor gates, sensitivity hot reload, hidden, backdrop, surface identity, recovery OK frames="+output.get());
        }
        void preview()throws Exception{
            stage("preview ignores backdrop");
            active(true);loop.backdrop(true);barrier();on(handler,()->require(!(Boolean)field(loop,"backdropCovered"),"preview ignores applied backdrop"));
            active(true);int before=output.get();pulse();waitFrames(before,2,3000,"preview still renders beneath applied backdrop event");same();
            loop.backdrop(false);loop.visible(false);barrier();active(false);quiet("preview visibility still pauses");
            loop.visible(true);barrier();active(true);before=output.get();pulse();waitFrames(before,1,3000,"preview visibility resumes");
            note("Preview ignores backdrop; independent visibility pauses/resumes OK");
        }
        void dynamic(String family)throws Exception{
            stage(family+" lifecycle");
            int before=output.get();waitFrames(before,3,4000,family+" animates");
            loop.backdrop(true);barrier();same();quiet(family+" opaque backdrop");
            loop.backdrop(false);barrier();before=output.get();waitFrames(before,3,4000,family+" uncover resumes");same();
            loop.visible(false);barrier();quiet(family+" hidden");loop.visible(true);barrier();before=output.get();waitFrames(before,3,4000,family+" visible resumes");same();
            note(family+": backdrop/hidden pause/resume without surface rebuild OK frames="+output.get());
        }
        @Override public void close()throws Exception{
            if(closed)return;closed=true;
            stage("closing");
            loop.close();((HandlerThread)field(loop,"thread")).join(10000);
            require(!((HandlerThread)field(loop,"thread")).isAlive(),"render thread closed");
            on(drain,()->{});frames+=output.get();reader.setOnImageAvailableListener(null,null);surface.release();reader.close();drainThread.quitSafely();drainThread.join(3000);
            prefs.edit().clear().commit();
        }
    }
}
