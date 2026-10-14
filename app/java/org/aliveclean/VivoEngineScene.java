package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Original Vivo controllers with an OpenAlive texture and lifecycle host. GL-thread owned. */
final class VivoEngineScene implements AutoCloseable {
    static final String BASE="com.vivo.livewallpaper.box.personalization.",R="com.vivo.livewallpaper.sdk.r2d.render.";
    private final VivoCalls api;
    private final int family;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ConcurrentLinkedQueue<Runnable> glTasks=new ConcurrentLinkedQueue<>();
    private final Runnable invalidate;
    private Object render,controller,plugin,layerContract;
    private VivoTextureLayer layer;
    private VivoSensorGain sensorGain;
    private Bitmap liquidMask;
    private int photoWidth,photoHeight;
    private VivoOptions options;
    private volatile boolean closed,visible;
    private volatile boolean flashReady;
    private boolean preview,visibilitySet,running;
    private int mode=1;
    private final int width,height;
    private final VivoWallpaperZoom zoom=new VivoWallpaperZoom();
    private final VivoWallpaperDim dim=new VivoWallpaperDim();
    private final VivoDimPass dimPass=new VivoDimPass();
    private float nightLevel=1,aodMask=.5f;
    private volatile float drawnScale=1;

    VivoEngineScene(VivoEngineRuntime runtime,VivoOptions options,int width,int height,List<Bitmap> photos,
            Bitmap subject,Bitmap paint,boolean preview,Runnable invalidate)throws Exception{
        api=new VivoCalls(runtime);family=options.family;this.width=width;this.height=height;this.preview=preview;this.invalidate=invalidate;this.options=VivoOptions.parse(options.json());
        zoom.enabled(options.wallpaperZoom,mode,System.nanoTime());
        try{
            String renderName=family==VivoOptions.LIQUID?"naturaleffects.NaturalEffectsPreRender":family==VivoOptions.RASTER?"raster.RasterPreRender":"effectscard.EffectsCardPreRender";
            Object settings=api.make(R+"b",new Class<?>[0]);api.set(settings,"designWidth",width);api.set(settings,"designHeight",height);
            Object environment=api.make("a5.l",new Class<?>[]{Context.class},runtime);
            render=api.make(BASE+"plugin."+renderName,new Class<?>[]{api.type(R+"b"),api.type("a5.l")},settings,environment);
            Class<?> callback=api.type(R+"f");
            Object host=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(o,m,args)->{
                switch(m.getName()){
                    case "queueEvent":if(!closed){glTasks.add((Runnable)args[0]);invalidate.run();}return null;
                    case "requestRending":case "resume":if(!closed&&visible)invalidate.run();return null;
                    case "hashCode":return System.identityHashCode(o);
                    case "equals":return o==args[0];
                    case "toString":return "OpenAlive render host";
                    default:return null;
                }
            });
            api.call(render,"setCallback",new Class<?>[]{callback},host);
            api.call(render,"glSurfaceCreate");api.call(render,"glSurfaceChange",new Class<?>[]{int.class,int.class},width,height);
            boolean displayP3=VivoColorSpace.isDisplayP3(android.opengl.EGL14.eglGetCurrentDisplay(),android.opengl.EGL14.eglGetCurrentSurface(android.opengl.EGL14.EGL_DRAW));
            api.call(render,"setWideColorGamutEnable",new Class<?>[]{boolean.class},displayP3);
            layer=new VivoTextureLayer(api,render,width,height,photos,subject,paint,options.viewport(),family!=VivoOptions.RASTER&&options.area==3,family!=VivoOptions.FLASH&&!uprightLiquid(),displayP3);
            if(videoRaster())layer.video(api,runtime.getFilesDir(),options);
            layerContract=layer.proxy;Class<?> layerType=api.type("com.android.systemui.plugins.IVAGLayer");
            if(family==VivoOptions.LIQUID){
                Object config=api.make("D3.a",new Class<?>[0]);
                Object bean=bean(BASE+"plugin.effectscard.EffectsCardCustomDataBean",options.engineJson());
                api.call(config,"setCustomDataBean",new Class<?>[]{bean.getClass()},bean);
                // The editor is a live wallpaper preview, not Vivo's eye-button demo.
                api.set(config,"a",1);api.set(config,"c",width);api.set(config,"d",height);api.set(config,"e",true);
                api.set(config,"surfaceType",2);api.set(config,"isPreview",true);
                if(options.style==1){
                    photoWidth=photos.get(0).getWidth();photoHeight=photos.get(0).getHeight();
                    Bitmap source=options.area==1?(paint==null?subject:paint):subject;
                    if(options.area==4){liquidMask=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);liquidMask.eraseColor(Color.WHITE);}
                    else if(source!=null){
                        liquidMask=source.copy(Bitmap.Config.ARGB_8888,true);
                        if(options.area==3){
                            int[] pixels=new int[liquidMask.getWidth()*liquidMask.getHeight()];liquidMask.getPixels(pixels,0,liquidMask.getWidth(),0,0,liquidMask.getWidth(),liquidMask.getHeight());
                            for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|(~pixels[i]&0xffffff);
                            liquidMask.setPixels(pixels,0,liquidMask.getWidth(),0,0,liquidMask.getWidth(),liquidMask.getHeight());
                        }
                    }
                    liquidWeights(config,options.viewport());
                }
                controller=api.make(BASE+"plugin.naturaleffects.NaturalEffectsPreCtrl",new Class<?>[]{render.getClass()},render);
                api.call(controller,"create",new Class<?>[]{config.getClass(),layerType},config,layerContract);
                api.call(controller,"onGLSurfaceChanged",new Class<?>[]{int.class,int.class},width,height);
            }else{
                Object data=bean(BASE+"PageContext$Data","{}");
                Object wrap=api.make(BASE+"ThemeEditInterfaceWrap",new Class<?>[]{api.type(BASE+"PageManager")},(Object)null);
                if(family==VivoOptions.RASTER){
                    plugin=api.make(BASE+"plugin.raster.GLRasterWallpaperPrePlugin",new Class<?>[]{Context.class,Context.class,data.getClass(),wrap.getClass(),String.class},runtime,runtime,data,wrap,"layer_img1_bg_wallpaper");
                    api.set(plugin,"mCustomDataBean",bean(BASE+"plugin.raster.RasterCustomDataBean",options.engineJson()));
                }else{
                    Object bean=bean(BASE+"plugin.effectscard.EffectsCardCustomDataBean",options.engineJson());api.set(data,"mEffectsCardCustomData",bean);
                    plugin=api.make(BASE+"plugin.effectscard.GLEffectsCardWallpaperPrePlugin",new Class<?>[]{Context.class,Context.class,data.getClass(),wrap.getClass(),String.class,String.class,String.class},runtime,runtime,data,wrap,"layer_img1_bg_wallpaper",runtime.getFilesDir().getPath(),"200115356");
                    api.set(plugin,"mCustomDataBean",bean);
                }
                api.set(plugin,"mRender",render);
                api.call(plugin,family==VivoOptions.RASTER?"loadSource":"loadFlashCardSource",new Class<?>[]{layerType},layerContract);
                // Host photos are already in the surface color space; decoded video
                // still uses the original engine's sRGB-to-P3 shader when needed.
                api.call(render,"setImageSRGBToP3Enable",new Class<?>[]{boolean.class},false);
                api.call(render,"setVideoSRGBToP3Enable",new Class<?>[]{boolean.class},displayP3&&videoRaster());
                controller=api.get(plugin,"mIEffectCtrl");
                if(controller==null)throw new IllegalStateException("Original Vivo controller unavailable");
                if(family==VivoOptions.FLASH){
                    Object config=api.get(controller,"g");
                    api.call(config,"setCustomDataBean",new Class<?>[]{api.type(BASE+"plugin.effectscard.EffectsCardCustomDataBean")},api.get(plugin,"mCustomDataBean"));
                    api.set(config,"a",1); // Bundled, byte-identical downloaded textures.
                    api.set(config,"b",false); // Native wallpaper texture/mask and highlight coordinates.
                    api.set(config,"isPreview",true); // Host owns visibility and render suspension.
                    layer.bindLiveFlashMask(api,render,controller,options.area);
                    api.set(controller,"i",true); // Host visibility starts the real sensor mode, not the eye-button demo.
                }
                api.call(controller,family==VivoOptions.RASTER?"onGLSurfaceChanged":"b",new Class<?>[]{int.class,int.class},width,height);
                sensorGain=new VivoSensorGain(runtime,api,sensor(),options.sensitivity);
            }
        }catch(Exception e){close();throw e;}
    }
    private Object bean(String type,String json)throws Exception{
        Object result=api.type("e3.l").getMethod("a",String.class,Class.class).invoke(null,json,api.type(type));
        if(result==null)throw new IllegalArgumentException("Invalid Vivo configuration: "+type);return result;
    }
    void visible(boolean value){
        if(closed)return;
        if(visibilitySet&&visible==value){if(value)updateAnimation();return;}
        visibilitySet=true;
        visible=value;
        if(!value){zoom.pause();dim.pause();}
        updateAnimation();
        if(value)invalidate.run();
    }
    private void updateAnimation(){
        boolean value=visible&&(mode!=0||animateInAod());
        if(!value)flashReady=false;
        main.post(()->{if(closed||controller==null)return;try{
            if(running==value){
                // A display/AOD callback can arrive without a visibility edge.
                // Restore only the subscription; replaying the controller would
                // reset the flash posture while AOD is still visible.
                if(value&&sensorGain!=null)sensorGain.start();
                return;
            }
            if(!value)stop();
            else if(family==VivoOptions.LIQUID)api.call(controller,"playAnim");
            else if(family==VivoOptions.RASTER){
                Object sensor=sensor();
                api.set(sensor,"e",0L); // Never integrate the time spent asleep.
                api.call(controller,"d",new Class<?>[]{boolean.class},videoRaster());
            }
            else {
                // n3.g.g(0,false), reached through a(), resets both springs, the
                // intensity and the three-sample posture calibration as Vivo does
                // on wake. Also discard the host's suspended sensor time span;
                // no velocity sample may integrate time spent with the screen off.
                Object sensor=api.get(api.get(controller,"f"),"e");
                api.set(sensor,"e",0L);api.set(sensor,"f",0f);api.set(sensor,"g",0f);api.set(sensor,"h",0f);
                api.call(controller,"a");flashReady=true;invalidate.run();
            }
            if(value&&sensorGain!=null)sensorGain.start();
            running=value;
        }catch(Exception e){android.util.Log.e("AliveClean","Vivo lifecycle failed",e);}});
    }
    void mode(int next,boolean animate){transition(next,animate,nightLevel,aodMask);}
    void transition(int next,boolean animate,float night,float mask){
        mode=next;nightLevel=night;aodMask=mask;long now=System.nanoTime();
        zoom.mode(next,animate,now);dim.target(nightLevel,aodMask,next,animate,now);updateAnimation();
    }
    void darkWallpaper(boolean dark,boolean animate){nightLevel=dark?.76f:1f;dim.target(nightLevel,aodMask,mode,animate,System.nanoTime());invalidate.run();}
    boolean animateInAod(){return options.animateInAod();}
    boolean transitionActive(){return zoom.active()||dim.active();}
    boolean updateSensitivity(VivoOptions next){
        if(closed||!options.sameScene(next))return false;
        if(options.wallpaperZoom!=next.wallpaperZoom){zoom.enabled(next.wallpaperZoom,mode,System.nanoTime());invalidate.run();}
        options=VivoOptions.parse(next.json());int relative=next.sensitivity;
        main.post(()->{if(!closed&&sensorGain!=null)sensorGain.relative(relative);});
        return true;
    }
    void draw()throws Exception{
        drain();api.call(render,"glSurfaceDraw");
        Object matrix=api.call(render,"getMatrixManager");
        float scale=zoom.sample(System.nanoTime());
        drawnScale=scale;
        // Original renderer coordinates are centred, with positive Y upward.
        // Screen Y=40% is +10% of the height in this coordinate system.
        api.call(matrix,"c");
        try{
            api.call(matrix,"i",new Class<?>[]{float.class,float.class,float.class},0f,height*.1f*(1-scale),0f);
            api.call(matrix,"e",new Class<?>[]{float.class,float.class},scale,scale);
            // The native preview renders into a vertically inverted editor FBO.
            // Lattice physics, masks and touch positions use the live screen axes.
            // Cancel that FBO rotation and upload an upright bitmap as the live renderer does.
            if(uprightLiquid())api.call(matrix,"d",new Class<?>[]{float.class,float.class,float.class},180f,1f,0f);
            if(family==VivoOptions.FLASH)drawFlash();else api.call(controller,"onGLSurfaceDraw");
        }finally{api.call(matrix,"b");}
        dimPass.draw(dim.sample(System.nanoTime()),width,height);
    }
    private boolean uprightLiquid(){return family==VivoOptions.LIQUID&&options.style==1;}
    private boolean videoRaster(){return family==VivoOptions.RASTER&&!options.video.isEmpty();}
    private Object sensor()throws Exception{return videoRaster()?api.get(api.get(controller,"f"),"d"):api.get(api.get(controller,"f"),"e");}
    private void drawFlash()throws Exception{
        // p3.a.e: use the original wallpaper render nodes without p3.c's editor-FBO
        // rotation. That rotation reverses the spatial relationship to iMouse and
        // changes the highlight/rainbow result even when gyro angles are identical.
        Object animation=api.get(controller,"f"),scene=api.get(controller,"c"),node=api.get(scene,"i"),parameters=api.get(node,"c");
        // Visibility reaches GL before the main-thread sensor restart. Never draw
        // the previous wake session's highlight in that intervening frame.
        boolean ready=flashReady;
        api.set(parameters,"d",ready?api.get(animation,"n"):0f);api.set(parameters,"e",ready?api.get(animation,"o"):0f);
        api.set(parameters,"f",ready?api.call(animation,"c"):0f);api.set(parameters,"a",1f);
        api.set(node,"m",api.get(api.get(controller,"g"),"surfaceType"));
        api.call(render,"push");
        try{Object matrix=api.call(render,"getMatrixManager");api.call(scene,"a",new Class<?>[]{matrix.getClass()},matrix);}
        finally{api.call(render,"pop");}
    }
    void crop(PhotoViewport viewport)throws Exception{
        layer.crop(viewport);
        if(family==VivoOptions.LIQUID&&liquidMask!=null){
            liquidWeights(api.call(controller,"getConfig"),viewport);
            api.call(controller,"onGLSurfaceChanged",new Class<?>[]{int.class,int.class},width,height);
        }
        api.call(controller,family==VivoOptions.FLASH?"e":"reLoadSource",new Class<?>[]{api.type("com.android.systemui.plugins.IVAGLayer")},layerContract);
        if(family==VivoOptions.FLASH)layer.bindLiveFlashMask(api,render,controller,options.area);
        invalidate.run();
    }
    private void liquidWeights(Object config,PhotoViewport viewport)throws Exception{
        if(liquidMask==null)return;
        PhotoViewport crop=new PhotoViewport();crop.dimensions(photoWidth,photoHeight,width,height);crop.restore(viewport.centerX(),viewport.centerY(),viewport.zoom());
        int w=liquidMask.getWidth(),h=liquidMask.getHeight();
        Rect rect=new Rect(Math.round(crop.left()*w),Math.round(crop.top()*h),Math.round((crop.left()+crop.width())*w),Math.round((crop.top()+crop.height())*h));
        int columns=width<height?20:Math.round(20f*width/height),rows=width<height?Math.round(20f*height/width):20;
        float[] weights=(float[])api.type("Q3.c").getMethod("k",Bitmap.class,Rect.class,int.class,int.class).invoke(null,liquidMask,rect,columns,rows);
        api.set(config,"b",weights);
        // Vivo's live SpringLatticeEffectStrategy reads effect.maskWeights;
        // the separate eye-button strategy reads b. Keep both contracts identical.
        Object effect=api.make(BASE+"bean.EffectJsonBean",new Class<?>[0]);api.set(effect,"maskWeights",weights);api.set(config,"effect",effect);
    }
    void touch(int action,float x,float y){
        if(family!=VivoOptions.LIQUID)return;
        final float tx=.5f+(x-.5f)/drawnScale,ty=.4f+(y-.4f)/drawnScale;
        main.post(()->{if(closed||!visible)return;try{
            String json="{\"data\":{\"eventAction\":"+action+",\"x\":"+tx+",\"y\":"+ty+"}}";
            Object touch=bean(BASE+"bean.TouchDataBean",json);
            api.call(controller,"onWallpaperTouchEvent",new Class<?>[]{touch.getClass()},touch);
        }catch(Exception e){android.util.Log.w("AliveClean","Vivo touch unavailable",e);}});
    }
    private void stop()throws Exception{
        if(sensorGain!=null)sensorGain.close();
        if(family!=VivoOptions.FLASH)api.call(controller,"stopAnim");
        else {Object animation=api.get(controller,"f");api.call(animation,"b");api.call(animation,"f");api.call(api.get(controller,"e"),"h");}
    }
    private void drain(){for(Runnable r;(r=glTasks.poll())!=null;)r.run();}
    @Override public void close(){
        if(closed)return;visible=false;
        // The original animation controllers are main-thread objects. Wait only from GL;
        // no UI path waits on GL, avoiding the inverse lock order during surface teardown.
        FutureTask<Void> shutdown=new FutureTask<>(()->{
            if(controller!=null){stop();if(family==VivoOptions.LIQUID)api.call(controller,"destroy");
                else if(family==VivoOptions.FLASH)api.call(plugin,"stopDraw");}
            return null;
        });
        if(Looper.myLooper()==Looper.getMainLooper())shutdown.run();else main.post(shutdown);
        try{shutdown.get(3,TimeUnit.SECONDS);if(family==VivoOptions.FLASH){FutureTask<Void> barrier=new FutureTask<>(()->null);main.post(barrier);barrier.get(3,TimeUnit.SECONDS);}drain();
            if(family==VivoOptions.RASTER&&controller!=null){Object res=api.get(controller,"b");api.call(controller,"destroy");if(res!=null&&!videoRaster())((ExecutorService)api.get(res,"h")).shutdownNow();}
        }catch(Exception e){android.util.Log.w("AliveClean","Vivo renderer release incomplete",e);}
        closed=true;main.removeCallbacksAndMessages(null);glTasks.clear();dimPass.close();if(layer!=null){layer.close();layer=null;}if(liquidMask!=null){liquidMask.recycle();liquidMask=null;}
        // A deleted but still-current program can survive until the next renderer
        // binds another one. Do not let Vivo's FBO helper try to restore that name.
        android.opengl.GLES30.glUseProgram(0);android.opengl.GLES30.glBindVertexArray(0);
    }
}
