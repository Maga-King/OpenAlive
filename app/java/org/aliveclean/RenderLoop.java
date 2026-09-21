package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import android.opengl.*;
import android.os.*;
import android.util.Log;
import android.view.Surface;
import android.view.Choreographer;
import java.io.*;
import java.nio.ByteBuffer;

/** All EGL, native handles and textures belong to this single worker. */
final class RenderLoop {
    private final Context context;
    private final android.content.SharedPreferences preferences;
    private final HandlerThread thread=new HandlerThread("AliveGL");
    private final Handler handler;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext egl=EGL14.EGL_NO_CONTEXT;
    private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private long scene;
    private SceneMotion motion;
    private FrameMotion frameMotion;
    private final FrameSample frameSample=new FrameSample();
    private MaskFrames masks;
    private Choreographer choreographer;
    private final VsyncPacer pacer=new VsyncPacer();
    private boolean framePosted;
    private long frameTime;
    private int photo,decorator,width,height,mode=1;
    private final int[] auxiliary=new int[6];
    private int homeWidth,homeHeight;
    private float aspect=1;
    private boolean visible=true;
    private boolean stateReported;
    private long clockToken,seenClockToken;
    private final AodMotionWindow aodMotion=new AodMotionWindow();
    private final boolean preview;
    private AodRegion aodRegion;
    private int displayId;
    private volatile boolean closed;
    private final Runnable renderTask=()->render(frameTime);
    // Queue GL work after all animation callbacks for this vsync have run.
    private final Choreographer.FrameCallback frame=this::onVsync;
    private final Runnable reloadTask=()->reloadScene();
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->reload();
    RenderLoop(Context context){this(context,SceneOptions.APPLIED);}
    RenderLoop(Context context,String name){this.context=context.getApplicationContext();preview=SceneOptions.DRAFT.equals(name);preferences=context.getSharedPreferences(name,0);thread.start();handler=new Handler(thread.getLooper());preferences.registerOnSharedPreferenceChangeListener(changes);}
    void attach(Surface surface,int w,int h){handler.post(()->{release();if(!surface.isValid()||w<1||h<1)return;try{init(surface,w,h);schedule();}catch(Exception e){Log.e("AliveClean","Renderer initialization failed",e);release();}});}
    void detach(){handler.post(this::release);}
    void mode(int next){mode(next,true);}
    void clockTransition(long token){handler.post(()->{if(preview||token==seenClockToken)return;seenClockToken=token;clockToken=token;if(token!=0)schedule();});}
    private void reportClock(boolean submitted){
        long token=clockToken;clockToken=0;
        if(token!=0&&!preview){{if(Diagnostics.TRACE)Log.i("AliveClean","Frame clock token="+token+" submitted="+submitted);}SceneChannel.frameReady(token,submitted);}
    }
    void aodRegion(AodRegion region,int display){
        if(region==null||region.displayId!=display)return;
        handler.post(()->{displayId=display;aodRegion=region;applyAodRegion();schedule();});
    }
    private void applyAodRegion(){
        if(motion!=null&&aodRegion!=null&&aodRegion.fits(displayId,width,height)){
            if(frameMotion==null)motion.followClock(aodRegion.sceneX(),aodRegion.sceneY());
            else frameMotion.followClock(aodRegion.sceneX(),aodRegion.sceneY());
        }
    }
    void mode(int next,boolean animate){handler.post(()->{if(mode!=next){stateReported=false;frameSample.clear();if(next==0)aodMotion.enter(System.nanoTime());else aodMotion.leave();}mode=next;if(motion!=null){if(frameMotion!=null){frameMotion.change(next,motion);if(!animate)frameMotion.finish();if(!visible)frameMotion.pause(true);}motion.change(next);if(!animate)motion.finish();if(!visible)motion.pause(true);}schedule();});}
    void visible(boolean value){handler.post(()->{visible=value;if(motion!=null)motion.pause(!value);if(frameMotion!=null)frameMotion.pause(!value);stopFrames();if(value){pacer.reset();schedule();}else if(clockToken!=0)reportClock(false);});}
    void reload(){if(!closed){handler.removeCallbacks(reloadTask);handler.post(reloadTask);}}
    private void reloadScene(){if(scene==0)return;try{if(motion!=null)motion.close();closeFrame();NativeScene.destroy(scene);scene=0;GLES30.glDeleteTextures(1,new int[]{photo},0);photo=0;clearAuxiliary();SceneOptions options=new SceneOptions(preferences);photo=loadPhoto(options.photo,true);createScene(options);schedule();}catch(Exception e){Log.e("AliveClean","Scene reload failed",e);release();}}
    void close(){if(closed)return;closed=true;preferences.unregisterOnSharedPreferenceChangeListener(changes);handler.post(()->{release();thread.quitSafely();});}
    private void schedule(){if(visible&&scene!=0&&!framePosted){framePosted=true;choreographer.postFrameCallback(frame);}}
    private void onVsync(long time){framePosted=false;frameTime=time;handler.post(renderTask);}
    private void stopFrames(){if(choreographer!=null)choreographer.removeFrameCallback(frame);framePosted=false;handler.removeCallbacks(renderTask);}
    private void createScene(SceneOptions options)throws IOException{
        int lock=options.pairedFrame()?0:options.lock,home=options.pairedFrame()?6:options.home;
        scene=NativeScene.create(width,height,mode,options.aod,lock,home);
        if(scene==0)throw new IllegalStateException("Native scene creation failed; see shader log");
        motion=new SceneMotion(width,height,mode,PhotoStyle.supported(options.aod)?-1:options.aod,lock,home);
        if(PhotoStyle.supported(options.aod)){
            PhotoStyle style=PhotoStyle.get(options.aod);
            frameMotion=new FrameMotion(width,height,mode,options.pairedFrame(),style);
            if(options.pairedFrame()){
                auxiliary[5]=loadPhoto(options.framePhoto,false);
                NativeScene.texture(scene,5,auxiliary[5],homeWidth,homeHeight);
                NativeScene.frameCrop(scene,options.frameX,options.frameY,options.frameSize,options.frameAngle);
            }
            int[] id=new int[1];GLES30.glGenTextures(1,id,0);auxiliary[0]=id[0];GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,id[0]);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGB,1,1,0,GLES30.GL_RGB,GLES30.GL_UNSIGNED_BYTE,ByteBuffer.allocateDirect(3));
            NativeScene.texture(scene,0,id[0],style.size,style.size);
            // A decoded mask belongs to the current animation sample. Deferring
            // its upload to the next vsync would advance the timeline first and
            // repeatedly miss that mask, leaving the old shape until the end.
            masks=new MaskFrames(context.getAssets(),handler,()->{if(visible&&scene!=0)render(frameTime);},style);
        }
        applyAodRegion();
        choreographer=Choreographer.getInstance();pacer.reset();
        NativeScene.tint(scene,options.color);
        if(!options.pairedFrame()&&!options.followLock&&!options.homePhoto.equals(options.photo)&&new File(context.getFilesDir(),options.homePhoto).isFile()){
            auxiliary[4]=loadPhoto(options.homePhoto,false);NativeScene.texture(scene,4,auxiliary[4],homeWidth,homeHeight);
        }
        if(options.lock==3){loadAuxiliary(1,"shader/photo/lock/groundGlass/gray.png",false);loadAuxiliary(2,"shader/photo/lock/groundGlass/mask.png",true);}
        if(options.aod==101)loadAuxiliary(3,"shader/photo/aod/fullAod/gray.png",false);
    }
    private void loadAuxiliary(int slot,String path,boolean repeat)throws IOException{
        try(InputStream in=context.getAssets().open(path)){
            Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IOException("Invalid texture: "+path);
            try{auxiliary[slot]=upload(b);if(repeat){GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_REPEAT);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_REPEAT);}NativeScene.texture(scene,slot,auxiliary[slot],b.getWidth(),b.getHeight());}finally{b.recycle();}
        }
    }
    private void clearAuxiliary(){GLES30.glDeleteTextures(auxiliary.length,auxiliary,0);java.util.Arrays.fill(auxiliary,0);}
    private void init(Surface surface,int w,int h)throws IOException{
        width=w;height=h;display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("eglInitialize");
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        int[] attrs={EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
        if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("No GLES3 EGL config");
        egl=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        window=EGL14.eglCreateWindowSurface(display,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
        if(!EGL14.eglMakeCurrent(display,window,window,egl))throw new IllegalStateException("eglMakeCurrent");
        EGL14.eglSwapInterval(display,1);SceneOptions options=new SceneOptions(preferences);photo=loadPhoto(options.photo,true);
        try(InputStream in=context.getAssets().open("shader/photo/aod/lensPhoto/lens_decorator.png")){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IOException("Invalid decorator");decorator=upload(b);b.recycle();}
        createScene(options);{if(Diagnostics.TRACE)Log.i("AliveClean","GLES3 scene ready "+w+"x"+h);}
    }
    private int loadPhoto(String name,boolean primary){
        File file=new File(context.getFilesDir(),name);Bitmap bitmap=null;
        if(file.exists())try{android.graphics.ImageDecoder.Source source=android.graphics.ImageDecoder.createSource(file);bitmap=android.graphics.ImageDecoder.decodeBitmap(source,(decoder,info,s)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();float ratio=Math.min(1f,2048f/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)));decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);});}catch(IOException e){Log.e("AliveClean","Photo decode failed",e);}
        if(bitmap==null){bitmap=Bitmap.createBitmap(720,1440,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint p=new Paint(3);p.setShader(new LinearGradient(0,0,720,1440,new int[]{0xff183955,0xffce865c,0xff5b526e},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,720,1440,p);p.setShader(null);p.setColor(0xffe6b282);canvas.drawCircle(490,480,230,p);p.setColor(0xff254452);canvas.drawOval(-220,800,930,1880,p);}
        if(primary)aspect=(float)bitmap.getWidth()/bitmap.getHeight();else{homeWidth=bitmap.getWidth();homeHeight=bitmap.getHeight();}int id=upload(bitmap);bitmap.recycle();return id;
    }
    private static int upload(Bitmap bitmap){
        int[] ids=new int[1];GLES30.glGenTextures(1,ids,0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,ids[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        if(bitmap.hasAlpha()&&bitmap.isPremultiplied()){
            // getPixels returns straight ARGB; the effect shader performs alpha composition.
            int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
            ByteBuffer rgba=ByteBuffer.allocateDirect(pixels.length*4);
            for(int p:pixels)rgba.put((byte)(p>>16)).put((byte)(p>>8)).put((byte)p).put((byte)(p>>24));
            rgba.flip();GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA,w,h,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,rgba);
        }else GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);
        return ids[0];
    }
    private void render(long time){
        if(!visible||scene==0)return;
        try{
            boolean resumedSample=frameMotion!=null&&frameSample.pending;
            motion.advance(time,preview||mode!=0?1f:aodMotion.speed(time));
            if(frameMotion!=null){
                // Do not chase a newer animator sample while waiting for its mask:
                // a busy app can otherwise starve every intermediate mask until
                // the final one. Geometry, photo blend and paper share one sample.
                frameSample.capture(frameMotion,motion,SystemClock.uptimeMillis());
                if(!masks.upload(frameSample.mask,auxiliary[0])){if(!masks.failed())schedule();else reportClock(false);return;}
                if(!NativeScene.frame(scene,frameSample.effect))throw new IllegalStateException("Invalid frame effect packet");
            }
            if(Diagnostics.TRACE&&mode==0&&!stateReported&&motion.values[0]>.999f){stateReported=true;Log.i("AliveClean","AOD frame lensScale="+motion.values[4]+" x="+motion.values[8]+" y="+motion.values[9]+" viewport="+width+"x"+height+" photo="+photo);}
            boolean moving=motion.transitionActive()||(frameMotion!=null?frameMotion.active():mode==0&&(preview||aodMotion.running(time)));
            if(pacer.due(time)||!moving){
                if(!NativeScene.render(scene,photo,decorator,aspect,frameMotion==null?motion.packet():frameSample.base))throw new IllegalStateException("Invalid native frame packet");
                if(!EGL14.eglSwapBuffers(display,window))throw new IllegalStateException("EGL swap error "+EGL14.eglGetError());
                if(clockToken!=0&&mode==1){if(frameMotion==null)reportClock(false);else if(frameSample.expanded)reportClock(true);}
                if(frameMotion!=null){
                    if(Diagnostics.TRACE){long wait=SystemClock.uptimeMillis()-frameSample.capturedAt;if(wait>80)Log.i("AliveClean","Frame sample mask="+frameSample.mask+" waitMs="+wait);}
                    frameSample.clear();
                }
            }
            if(moving||frameSample.pending||resumedSample)schedule();
        }catch(Exception e){Log.e("AliveClean","Frame failed",e);release();}
    }
    private void closeFrame(){if(scene!=0)reportClock(false);frameSample.clear();if(frameMotion!=null){frameMotion.close();frameMotion=null;}if(masks!=null){masks.close();masks=null;}}
    private void release(){stopFrames();handler.removeCallbacks(reloadTask);closeFrame();if(motion!=null){motion.close();motion=null;}if(display!=EGL14.EGL_NO_DISPLAY){if(scene!=0){NativeScene.destroy(scene);scene=0;}clearAuxiliary();GLES30.glDeleteTextures(2,new int[]{photo,decorator},0);photo=decorator=0;EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);if(egl!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,egl);EGL14.eglTerminate(display);EGL14.eglReleaseThread();}display=EGL14.EGL_NO_DISPLAY;egl=EGL14.EGL_NO_CONTEXT;window=EGL14.EGL_NO_SURFACE;}
}
