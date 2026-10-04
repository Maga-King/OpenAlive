package org.aliveclean;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.*;

/** Adapter to the unmodified Xiaomi players. Each family runs in its own process. */
public final class XiaomiPlayer implements AutoCloseable {
    private static final Map<String,Runtime> runtimes=new HashMap<>();
    public static final class Runtime {
        final String family,path;
        final ClassLoader loader;
        final Context context;
        Runtime(Context host,String family,File apk,String key)throws Exception {
            this.family=family;path=apk.getPath();
            File libs=new File(host.getCodeCacheDir(),"xiaomi-native/"+key);
            if(!libs.isDirectory()&&!libs.mkdirs())throw new IOException("Cannot create Xiaomi runtime directory");
            try(ZipFile zip=new ZipFile(apk)){
                Enumeration<? extends ZipEntry> entries=zip.entries();
                while(entries.hasMoreElements()){
                    ZipEntry e=entries.nextElement();String name=e.getName();
                    if(!name.startsWith("lib/arm64-v8a/")||e.isDirectory())continue;
                    name=name.substring("lib/arm64-v8a/".length());
                    if(name.contains("/")||name.contains("\\")||!name.endsWith(".so"))throw new IOException("Invalid native library entry");
                    File target=new File(libs,name);
                    if(target.isFile()&&target.length()==e.getSize())continue;
                    File part=File.createTempFile("lib-",".part",libs);
                    try{
                        CRC32 crc=new CRC32();long total=0;
                        try(InputStream in=zip.getInputStream(e);FileOutputStream out=new FileOutputStream(part)){
                            if(!part.setReadOnly())throw new IOException("Cannot protect native library");
                            byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;){out.write(b,0,n);crc.update(b,0,n);total+=n;}out.getFD().sync();
                        }
                        if(total!=e.getSize()||crc.getValue()!=e.getCrc())throw new IOException("Incomplete native library");
                        if(!part.renameTo(target))throw new IOException("Cannot commit native library");
                    }finally{if(part.exists())part.delete();}
                }
            }
            loader=new DexClassLoader(path,host.getCodeCacheDir().getPath(),libs.getPath(),Context.class.getClassLoader());
            ApplicationInfo archive=new ApplicationInfo();archive.packageName="com.miui.miwallpaper."+family;
            archive.sourceDir=archive.publicSourceDir=path;archive.uid=android.os.Process.myUid();
            Resources source=host.getPackageManager().getResourcesForApplication(archive);
            Resources resources=new Resources(source.getAssets(),source.getDisplayMetrics(),"org.aliveclean".equals(host.getPackageName())?I18n.config(source.getConfiguration()):source.getConfiguration()){
                @Override public int getIdentifier(String name,String type,String pkg){
                    // Unity looks up its SurfaceView label using the hosting package.
                    // Keep the real host identity for permissions and redirect only resources.
                    return super.getIdentifier(name,type,host.getPackageName().equals(pkg)?archive.packageName:pkg);
                }
            };
            ApplicationInfo info=new ApplicationInfo(host.getApplicationInfo());
            info.sourceDir=info.publicSourceDir=path;info.nativeLibraryDir=libs.getPath();
            context=new ContextWrapper(host){
                @Override public AssetManager getAssets(){return resources.getAssets();}
                @Override public Resources getResources(){return resources;}
                @Override public ClassLoader getClassLoader(){return loader;}
                @Override public Context getApplicationContext(){return this;}
                @Override public ApplicationInfo getApplicationInfo(){return info;}
                @Override public String getPackageCodePath(){return path;}
                @Override public String getPackageResourcePath(){return path;}
            };
        }
    }
    /** APK validation belongs to the pack store. Prepare off the UI thread. */
    public static synchronized Runtime prepare(Context host,String family,File apk,String digest)throws Exception {
        if(!Arrays.asList("earth","moon","mars","saturn","snowmountain","geometry").contains(family)||!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Unknown Xiaomi runtime");
        Runtime cached=runtimes.get(digest);
        if(cached!=null)return cached;
        // Several versions export identical JNI class/library names. Never load a
        // different variant into a process that already owns one of those libraries.
        if(!runtimes.isEmpty())throw new IllegalStateException("A Xiaomi runtime change requires a new renderer process");
        Runtime runtime=new Runtime(host.getApplicationContext(),family,apk,digest);runtimes.put(digest,runtime);return runtime;
    }
    private final Runtime runtime;
    private final boolean unity,snow;
    private final DeferredHolder holder;
    private Object player;
    private Object callbackOwner;
    private boolean running,attached,closed;
    private boolean unityThreadReady,requestedRunning;
    private final ArrayList<String> startupMessages=new ArrayList<>();
    private long threadWaitStarted;
    private boolean ready;
    private Runnable whenReady;
    private java.util.function.Consumer<Exception> whenFailed;
    private boolean initializing;
    private long initStarted;
    private int initChecks,initAttempts;private String initError="";
    private int width,height,initialLand;
    private float refresh;
    private boolean preview,initialized;
    private int moonPhase=Integer.MIN_VALUE;
    private final android.os.Handler mainHandler=new android.os.Handler(Looper.getMainLooper());

    public XiaomiPlayer(Runtime runtime,SurfaceHolder surface,Context displayContext)throws Exception {
        main();this.runtime=runtime;threadWaitStarted=android.os.SystemClock.uptimeMillis();
        unity=Arrays.asList("mars","saturn","geometry").contains(runtime.family);snow="snowmountain".equals(runtime.family);
        holder=new DeferredHolder(surface);
        if(unity){
            Class<?> callbackClass=runtime.loader.loadClass("com.miui.miwallpaper.basesuperwallpaper.SuperWallpaper");
            String service="com.miui.miwallpaper."+runtime.family+".superwallpaper."+Character.toUpperCase(runtime.family.charAt(0))+runtime.family.substring(1)+"SuperWallpaper";
            callbackOwner=runtime.loader.loadClass(service).getConstructor().newInstance();callbackClass.getField("mCurrentWallpaper").set(null,callbackOwner);
            player=runtime.loader.loadClass("com.unity3d.player.UnityPlayer").getConstructor(Context.class).newInstance(runtime.context);
            send("BindClass_com.miui.miwallpaper.basesuperwallpaper.SuperWallpaper");
        }
        else player=runtime.loader.loadClass(snow?"n2.a":"com.miui.mrengine."+("moon".equals(runtime.family)?"Moon":"Earth")+"MrePlayer")
            .getConstructor(Context.class,SurfaceHolder.class,Context.class).newInstance(runtime.context,holder,displayContext);
        // Filament subscribes to the Surface before constructing its Engine. A
        // holder already valid on a restored/async preview must be deferred.
        holder.activate();
        if(!unity)ready=true;else mainHandler.post(startUnity);
    }
    // Unity starts its render thread asynchronously. resume() marks its state
    // resumed even if that thread has no Handler yet, silently dropping the
    // resume/surface messages. Wait for the actual queue before using either API.
    private final Runnable startUnity=new Runnable(){public void run(){
        if(closed||unityThreadReady)return;
        try{
            Object thread=field(player,"m_MainThread");boolean available=false;
            for(Field f:thread.getClass().getDeclaredFields())if(android.os.Handler.class.isAssignableFrom(f.getType())){f.setAccessible(true);available=f.get(thread)!=null;if(available)break;}
            if(!available){
                if(android.os.SystemClock.uptimeMillis()-threadWaitStarted>5000)throw new IOException("Xiaomi Unity render queue did not start");
                mainHandler.postDelayed(this,16);return;
            }
            unityThreadReady=true;
            if(attached)call(player,"displayChanged",new Class[]{int.class,Surface.class},0,holder.getSurface());
            for(String event:new ArrayList<>(startupMessages))send(event);
            startupMessages.clear();running(requestedRunning);
        }catch(Exception e){initError=e.toString();if(whenFailed!=null)whenFailed.accept(e);}
    }};
    public void whenReady(Runnable action){main();whenReady=action;if(ready&&!closed)action.run();}
    public void whenFailed(java.util.function.Consumer<Exception> action){main();whenFailed=action;}
    public void surfaceChanged(int width,int height,float refresh)throws Exception {
        main();if(closed)return;
        this.width=width;this.height=height;this.refresh=refresh;
        if(unity&&unityThreadReady)call(player,"displayChanged",new Class[]{int.class,Surface.class},0,holder.getSurface());
        send("SysRes_"+width+"_"+height);send("Refresh_"+Math.round(refresh));attached=true;
    }
    public void initialize(int land,boolean preview)throws Exception{
        main();this.preview=preview;initialized=true;initialLand=land;send("IsPreview_"+preview);if(preview&&unity)send("LoadAll");
        initializing=unity&&!ready;initStarted=android.os.SystemClock.uptimeMillis();initChecks=0;initAttempts=0;initError="";
        mainHandler.removeCallbacks(readReady);bootstrap();calendarChanged();if(unity&&!ready&&running)mainHandler.post(readReady);
    }
    public void calendarChanged()throws Exception {
        main();if(closed||!initialized||preview||!"moon".equals(runtime.family))return;
        // The real Moon service (not its preview) supplies this lunar input.
        // Invoke the original pure calculation, avoiding its MIUI Settings writes.
        int value=(Integer)call(runtime.loader.loadClass("com.miui.miwallpaper.moon.superwallpaper.MoonPhaseUpdateHelper"),"getCurrentMoonPhase",new Class[0]);
        if(value!=moonPhase){send("Phrase_"+value);moonPhase=value;}
    }
    private final Runnable readReady=new Runnable(){public void run(){
        if(closed||ready||!attached||!running)return;
        initChecks++;
        if(android.os.SystemClock.uptimeMillis()-initStarted>30000){initializing=false;mainHandler.removeCallbacks(retry);Exception e=new IOException(I18n.t("小米原版渲染器初始化超时，请重新打开壁纸"));initError=e.toString();if(whenFailed!=null)whenFailed.accept(e);return;}
        try{if(Boolean.TRUE.equals(field(callbackOwner,"mInited"))){ready=true;initializing=false;mainHandler.removeCallbacks(retry);if(whenReady!=null)whenReady.run();return;}}catch(Exception e){android.util.Log.e("OpenAliveXiaomi","Original initialization status",e);return;}
        mainHandler.postDelayed(this,100);
    }};
    private final Runnable retry=()->{try{if(!closed&&!ready&&attached&&running&&initializing)bootstrap();}catch(Exception e){if(whenFailed!=null)whenFailed.accept(e);}};
    private void bootstrap()throws Exception{
        initAttempts++;
        if(unity){send("BindClass_com.miui.miwallpaper.basesuperwallpaper.SuperWallpaper");send("SysRes_"+width+"_"+height);send("ResScale_1.0");send("Refresh_"+Math.round(refresh));}
        send("Land_"+initialLand);
        mainHandler.removeCallbacks(retry);if(unity&&!ready&&running&&initializing)mainHandler.postDelayed(retry,3000);
    }
    public void send(String event)throws Exception {
        main();if(closed)return;
        // These two Filament revisions do not handle the Unity ForceAOD
        // command. Their static middle AOD pose is named ForceStaticAOD_1.
        // Leaving ForceAOD untranslated retains the previous close-up camera.
        if(!unity&&!snow&&"ForceAOD".equals(event))event="ForceStaticAOD_1";
        if(unity&&!unityThreadReady){startupMessages.add(event);return;}
        if(unity)call(player.getClass(),"UnitySendMessage",new Class[]{String.class,String.class,String.class},"Main Camera","Message",event);
        else call(player,snow?"b":"sendMessage",new Class[]{String.class},event);
    }
    public void running(boolean value)throws Exception {
        main();value=value&&attached&&!closed;requestedRunning=value;if(unity&&!unityThreadReady)return;if(value==running)return;
        if(snow){
            Choreographer choreographer=(Choreographer)field(player,"e");Choreographer.FrameCallback frame=(Choreographer.FrameCallback)field(player,"f");
            if(value){call(runtime.loader.loadClass("com.xiaomi.utils.JNIUtils"),"nResume",new Class[0]);choreographer.postFrameCallback(frame);}
            else{choreographer.removeFrameCallback(frame);call(runtime.loader.loadClass("com.xiaomi.utils.JNIUtils"),"nPause",new Class[0]);}
        }else{
            if(unity)call(player,"windowFocusChanged",new Class[]{boolean.class},value);
            call(player,value?"resume":"pause",new Class[0]);
        }
        running=value;
        if(value)calendarChanged();
        if(unity&&!ready){mainHandler.removeCallbacks(readReady);mainHandler.removeCallbacks(retry);
            if(value&&initializing){initStarted=android.os.SystemClock.uptimeMillis();bootstrap();mainHandler.post(readReady);}}
    }
    public boolean isRunning(){return running;}
    public String status(){String callback="n/a";if(callbackOwner!=null)try{callback=String.valueOf(field(callbackOwner,"mInited"));}catch(Exception e){callback=e.toString();}
        return "threadReady="+unityThreadReady+" attached="+attached+" running="+running+" ready="+ready+" initializing="+initializing+" checks="+initChecks+" attempts="+initAttempts+" callback="+callback+" error="+initError;}
    public void surface(SurfaceHolder source)throws Exception{main();if(holder.source==source)return;surfaceDestroyed();holder.rebind(source);}
    public void surfaceDestroyed()throws Exception {
        main();running(false);attached=false;mainHandler.removeCallbacks(retry);mainHandler.removeCallbacks(readReady);
        if(unity&&unityThreadReady&&!closed)call(player,"displayChanged",new Class[]{int.class,Surface.class},0,null);
    }
    @Override public void close()throws Exception {
        main();if(closed)return;running(false);closed=true;mainHandler.removeCallbacks(startUnity);startupMessages.clear();mainHandler.removeCallbacks(retry);mainHandler.removeCallbacks(readReady);
        holder.close();Object old=player;player=null;
        // Unity's original destroy() can terminate the process. Call only in a
        // dedicated wallpaper/preview process, never the editor or clock host.
        call(old,snow?"a":"destroy",new Class[0]);
    }
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Xiaomi renderer must run on its main looper");}
    private static Object field(Object target,String name)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception{
        try{return (target instanceof Class?(Class<?>)target:target.getClass()).getMethod(name,types).invoke(target instanceof Class?null:target,args);}
        catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw e;}
    }
    private static final class DeferredHolder implements SurfaceHolder,SurfaceHolder.Callback {
        SurfaceHolder source;final Surface pending=invalidSurface();final ArrayList<Callback> callbacks=new ArrayList<>();boolean active,closed;
        private static Surface invalidSurface(){android.graphics.SurfaceTexture texture=new android.graphics.SurfaceTexture(false);Surface s=new Surface(texture);s.release();texture.release();return s;}
        DeferredHolder(SurfaceHolder source){this.source=source;source.addCallback(this);}
        void rebind(SurfaceHolder next){if(closed)return;if(active&&source.getSurface().isValid())surfaceDestroyed(source);source.removeCallback(this);source=next;source.addCallback(this);if(active&&source.getSurface().isValid()){surfaceCreated(source);Rect r=source.getSurfaceFrame();surfaceChanged(source,android.graphics.PixelFormat.RGBX_8888,r.width(),r.height());}}
        void activate(){active=true;pending.release();if(source.getSurface().isValid()){surfaceCreated(source);Rect r=source.getSurfaceFrame();surfaceChanged(source,android.graphics.PixelFormat.RGBX_8888,r.width(),r.height());}}
        void close(){if(closed)return;closed=true;source.removeCallback(this);callbacks.clear();if(!active)pending.release();}
        @Override public void addCallback(Callback c){if(!callbacks.contains(c))callbacks.add(c);}
        @Override public void removeCallback(Callback c){callbacks.remove(c);}
        @Override public void surfaceCreated(SurfaceHolder h){if(active&&!closed)for(Callback c:new ArrayList<>(callbacks))c.surfaceCreated(this);}
        @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int height){if(active&&!closed)for(Callback c:new ArrayList<>(callbacks))c.surfaceChanged(this,f,w,height);}
        @Override public void surfaceDestroyed(SurfaceHolder h){if(active&&!closed)for(Callback c:new ArrayList<>(callbacks))c.surfaceDestroyed(this);}
        @Override public Surface getSurface(){return active?source.getSurface():pending;}
        @Override public Rect getSurfaceFrame(){return active?source.getSurfaceFrame():new Rect();}
        @Override public boolean isCreating(){return source.isCreating();}
        @Override public void setType(int type){source.setType(type);}
        @Override public void setFixedSize(int w,int h){source.setFixedSize(w,h);}
        @Override public void setSizeFromLayout(){source.setSizeFromLayout();}
        @Override public void setFormat(int f){source.setFormat(f);}
        @Override public void setKeepScreenOn(boolean on){source.setKeepScreenOn(on);}
        @Override public Canvas lockCanvas(){return source.lockCanvas();}
        @Override public Canvas lockCanvas(Rect r){return source.lockCanvas(r);}
        @Override public Canvas lockHardwareCanvas(){return source.lockHardwareCanvas();}
        @Override public void unlockCanvasAndPost(Canvas c){source.unlockCanvasAndPost(c);}
    }
}
