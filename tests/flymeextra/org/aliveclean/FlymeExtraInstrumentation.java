package org.aliveclean;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.opengl.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual production GLES + MediaCodec paths. Never blocks the codec's GL looper. */
public final class FlymeExtraInstrumentation extends Instrumentation {
    private static final int W=360,H=780;
    private final CountDownLatch finished=new CountDownLatch(1);
    private final StringBuilder log=new StringBuilder();
    private HandlerThread thread;
    private Handler gl;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface=EGL14.EGL_NO_SURFACE;
    private WallpaperRenderer renderer;
    private int checks,caseIndex,variant,phase,callbacks;
    private boolean success;
    private long deadline,started,phaseStart,hiddenTime;
    private byte[] first,previous,hidden;
    private final ByteBuffer readback=ByteBuffer.allocateDirect(W*H*4);

    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void require(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    public void onStart(){
        thread=new HandlerThread("FlymeExtraGL");thread.start();gl=new Handler(thread.getLooper());
        gl.post(()->guard(()->{initEgl();next();}));
        try{
            if(!finished.await(150,TimeUnit.SECONDS)){
                log.append("TIMEOUT: GL/codec task did not complete\n");
                success=false;
            }
        }catch(InterruptedException error){Thread.currentThread().interrupt();success=false;}
        thread.quitSafely();
        Bundle result=new Bundle();result.putString("stream",log.toString());finish(success?-1:1,result);
    }
    private interface Task{void run()throws Exception;}
    private void guard(Task task){
        if(finished.getCount()==0)return;
        try{task.run();}catch(Throwable error){log.append(android.util.Log.getStackTraceString(error));end(false);}
    }
    private void initEgl(){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] v=new int[2],count=new int[1];EGLConfig[] config=new EGLConfig[1];
        require(EGL14.eglInitialize(display,v,0,v,1),"EGL initialize");
        int[] attrs={EGL14.EGL_RENDERABLE_TYPE,64,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
        require(EGL14.eglChooseConfig(display,attrs,0,config,0,1,count,0)&&count[0]>0,"EGL config");
        context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
        require(EGL14.eglMakeCurrent(display,surface,surface,context),"EGL current");
        log.append("GPU: ").append(GLES30.glGetString(GLES30.GL_RENDERER)).append('\n');
    }
    private WallpaperRenderer create(int id,boolean keep)throws Exception{
        // Exercise the production factory, including all six ID mappings.
        return WallpaperRenderer.create(getTargetContext().getAssets(),id,false,W,H,1,keep,()->callbacks++);
    }
    private void next()throws Exception{
        if(renderer!=null){renderer.visible(false);renderer.close();renderer=null;checkGl("release");}
        if(caseIndex>=12){end(true);return;}
        int slot=caseIndex%6;variant=slot<3?501+slot:401+slot-3;
        log.append("begin ").append(variant).append(" cycle ").append(caseIndex/6).append('\n');
        if(slot<3){stereo();caseIndex++;gl.post(()->guard(this::next));return;}
        renderer=create(variant,caseIndex>=6);renderer.continuous(true);renderer.visible(true);
        byte[] initial=draw();nonblack(initial,"Video immediate first frame fallback");save(initial,"fallback");
        callbacks=0;phase=0;first=previous=hidden=null;
        started=System.nanoTime();deadline=started+15_000_000_000L;
        scheduleVideo();
    }
    private void stereo()throws Exception{
        renderer=create(variant,false);
        require(renderer instanceof StereoRenderer,"Stereo factory "+variant);
        StereoMotion motion=(StereoMotion)renderer.motion();
        long now=1_000_000_000L;motion.advance(now,1);
        first=draw();nonblack(first,"Stereo first frame");save(first,"first");
        for(int frame=1;frame<=90;frame++){
            now+=16_666_667L;motion.gyroscope(.3f,.8f,.15f,now);motion.advance(now,1);renderer.render();
        }
        byte[] moved=draw();changed(first,moved,"Stereo synthetic gyro pixels");save(moved,"gyro");
        motion.pause();motion.advance(now+600_000_000_000L,1);
        same(moved,draw(),"Stereo pause has no elapsed jump");
        // Allow original return-to-neutral, then compare the scene crop itself.
        now+=600_000_000_000L;
        for(int frame=0;frame<360;frame++){now+=16_666_667L;motion.advance(now,1);}
        motion.change(1,false);byte[] lock=draw();same(first,lock,"Stereo neutral lock returns");
        motion.configure(0,false);motion.change(0,false);byte[] aod=draw();nonblack(aod,"Stereo AOD");same(lock,aod,"Stereo source AOD/lock crop");
        motion.gyroscope(4,4,4,now);motion.advance(now+16_666_667L,1);same(aod,draw(),"Stereo AOD ignores gyro");
        motion.change(2,true);
        for(int frame=0;frame<90;frame++){now+=16_666_667L;motion.advance(now,1);}
        byte[] home=draw();nonblack(home,"Stereo home");changed(lock,home,"Stereo scene animation crop");save(home,"home");
        renderer.close();renderer=null;checkGl("Stereo close before keep-lock");
        renderer=create(variant,true);renderer.motion().change(2,false);
        byte[] kept=draw();same(lock,kept,"Stereo keep-lock retains lock pixels");
        log.append(variant).append(" gyro/scenes/pause/keepLock GPU OK\n");
    }
    private void scheduleVideo(){gl.postDelayed(()->guard(this::videoTick),20);}
    private void videoTick()throws Exception{
        long now=System.nanoTime();require(now<deadline,"Video async deadline "+variant+" phase="+phase);
        renderer.motion().advance(now,1);byte[] pixels=draw();
        if(phase==0){
            // The production thumbnail fallback is deliberately nonblack too.
            // Only a consumed SurfaceTexture frame proves codec output exists.
            java.lang.reflect.Field decoded=FlymeVideoRenderer.class.getDeclaredField("hasFrame");
            decoded.setAccessible(true);
            if(decoded.getBoolean(renderer)&&isNonblack(pixels)){
                nonblack(pixels,"Video first decoded frame");require(callbacks>0,"SurfaceTexture asynchronous callback");
                first=pixels;save(pixels,"first");phase=1;phaseStart=now;
            }
        }else if(phase==1&&now-phaseStart>=1_000_000_000L){
            nonblack(pixels,"Video continuous frame");changed(first,pixels,"Video consecutive decoded frames");save(pixels,"playing");
            renderer.motion().change(0,true);phase=2;phaseStart=now;
        }else if(phase==2&&now-phaseStart>=300_000_000L){
            nonblack(pixels,"Video scene 0");renderer.motion().change(1,true);phase=3;phaseStart=now;
        }else if(phase==3&&now-phaseStart>=300_000_000L){
            nonblack(pixels,"Video scene 1");renderer.motion().change(2,true);phase=4;phaseStart=now;
        }else if(phase==4&&now-phaseStart>=300_000_000L){
            nonblack(pixels,"Video scene 2 / keep-lock factory");
            renderer.visible(false);hiddenTime=((FlymeVideoMotion)renderer.motion()).sourceTimeUs();
            require(!renderer.motion().active(),"Hidden video inactive");phase=5;phaseStart=now;
        }else if(phase==5&&now-phaseStart>=150_000_000L){
            hidden=pixels;phase=6;phaseStart=now;
        }else if(phase==6&&now-phaseStart>=350_000_000L){
            require(((FlymeVideoMotion)renderer.motion()).sourceTimeUs()==hiddenTime,"Hidden video source clock frozen");
            same(hidden,pixels,"Hidden decoded surface remains stable");
            renderer.visible(true);renderer.motion().advance(now,1);
            long resumed=((FlymeVideoMotion)renderer.motion()).sourceTimeUs();
            require(resumed>=hiddenTime&&resumed-hiddenTime<50_000,"Resume excludes hidden wall time");
            phase=7;phaseStart=now;previous=pixels;
        }else if(phase==7&&now-phaseStart>=1_000_000_000L){
            nonblack(pixels,"Video resumed frame");changed(previous,pixels,"Codec recreated and continues after visibility");save(pixels,"resumed");
            log.append(variant).append(" decode/frames/scenes/hide/resume OK callbacks=").append(callbacks).append('\n');
            caseIndex++;next();return;
        }
        scheduleVideo();
    }
    private byte[] draw(){
        // State left by a previous family must not leave a 1px/culled output.
        GLES30.glEnable(GLES30.GL_BLEND);GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST);GLES30.glScissor(0,0,1,1);
        renderer.render();readback.clear();
        GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,readback);checkGl("draw/read "+variant);
        byte[] pixels=new byte[readback.capacity()];readback.get(pixels);return pixels;
    }
    private void checkGl(String label){int error=GLES30.glGetError();require(error==GLES30.GL_NO_ERROR,label+" GL error="+error);}
    private boolean isNonblack(byte[] pixels){
        long sum=0;int lit=0,n=0;
        for(int p=0;p<pixels.length;p+=64){int rgb=(pixels[p]&255)+(pixels[p+1]&255)+(pixels[p+2]&255);sum+=rgb;if(rgb>15)lit++;n++;}
        return sum/(double)n>8&&lit>n*.04;
    }
    private void nonblack(byte[] pixels,String label){require(isNonblack(pixels),label+" has visible image");}
    private double difference(byte[] a,byte[] b){
        long total=0;int n=0;for(int p=0;p<a.length;p+=64)for(int c=0;c<3;c++){total+=Math.abs((a[p+c]&255)-(b[p+c]&255));n++;}
        return total/(double)n;
    }
    private void changed(byte[] a,byte[] b,String label){double diff=difference(a,b);require(diff>.05,label+" mean difference="+diff);}
    private void same(byte[] a,byte[] b,String label){double diff=difference(a,b);require(diff<.005,label+" mean difference="+diff);}
    private void save(byte[] pixels,String stage)throws Exception{
        if(caseIndex>=6)return;
        int[] colors=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int p=((H-1-y)*W+x)*4;colors[y*W+x]=0xff000000|((pixels[p]&255)<<16)|((pixels[p+1]&255)<<8)|(pixels[p+2]&255);
        }
        Bitmap bitmap=Bitmap.createBitmap(colors,W,H,Bitmap.Config.ARGB_8888);
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"flymeextra-"+variant+"-"+stage+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        finally{bitmap.recycle();}
    }
    private void end(boolean ok){
        if(finished.getCount()==0)return;
        try{if(renderer!=null){renderer.close();renderer=null;}}
        catch(Throwable error){ok=false;log.append(android.util.Log.getStackTraceString(error));}
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
        if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
        if(display!=EGL14.EGL_NO_DISPLAY)EGL14.eglTerminate(display);
        EGL14.eglReleaseThread();success=ok;
        if(ok)log.append("FLYME_EXTRA_OK checks=").append(checks).append(" cases=").append(caseIndex).append('\n');
        finished.countDown();
    }
}
