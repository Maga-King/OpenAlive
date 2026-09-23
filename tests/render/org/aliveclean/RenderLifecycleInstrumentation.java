package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercise the installed renderer with a real EGL surface and a draining image consumer. */
public final class RenderLifecycleInstrumentation extends Instrumentation {
    private int variant;
    private boolean continuous;
    @Override public void onCreate(Bundle args){super.onCreate(args);variant=Integer.parseInt(args.getString("variant","9"));continuous=Boolean.parseBoolean(args.getString("continuous","true"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();RenderLoop loop=null;ImageReader reader=null;
        HandlerThread consumer=new HandlerThread("FrameTestConsumer");consumer.start();
        Context context=getTargetContext();String name="scene.lifecycle-test";
        try{
            context.getSharedPreferences(name,0).edit().clear().putInt("cosmic",variant)
                .putBoolean("cosmic_continuous_aod",continuous).putBoolean("cosmic_continuous_home",continuous)
                .putBoolean("sail_continuous_aod",continuous).commit();
            AtomicInteger frames=new AtomicInteger();
            reader=ImageReader.newInstance(360,792,PixelFormat.RGBA_8888,3);
            reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){if(image!=null)frames.incrementAndGet();}},new Handler(consumer.getLooper()));
            loop=new RenderLoop(context,name);loop.attach(reader.getSurface(),360,792);loop.mode(0,false);
            waitFrames(frames,0,5000);SystemClock.sleep(5500);
            int running=frames.get();
            if(continuous)waitFrames(frames,running,2000);
            else{SystemClock.sleep(500);if(frames.get()!=running)throw new AssertionError("Finite AOD did not settle");}
            loop.visible(false);SystemClock.sleep(300);int paused=frames.get();SystemClock.sleep(500);
            if(frames.get()!=paused)throw new AssertionError("Submitted frames while hidden");
            if(continuous){loop.mode(1,true);loop.mode(0,true);}else loop.mode(0,false);
            SystemClock.sleep(500);
            if(frames.get()!=paused)throw new AssertionError("Scene event restarted hidden rendering");
            loop.visible(true);waitFrames(frames,paused,3000);
            result.putString("stream","RENDER_LIFECYCLE_OK variant="+variant+" continuous="+continuous+" initialFrames="+running+" pausedFrames="+paused+" resumedFrames="+frames.get());
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{
            if(loop!=null){loop.visible(false);loop.close();SystemClock.sleep(300);}
            if(reader!=null)reader.close();consumer.quitSafely();context.deleteSharedPreferences(name);
        }
        finish(result.getString("stream","").startsWith("RENDER_LIFECYCLE_OK")?-1:1,result);
    }
    private static void waitFrames(AtomicInteger frames,int before,int timeout){
        long end=SystemClock.uptimeMillis()+timeout;
        while(frames.get()<before+3&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
        if(frames.get()<before+3)throw new AssertionError("Rendering did not resume: "+before+" -> "+frames.get());
    }
}
