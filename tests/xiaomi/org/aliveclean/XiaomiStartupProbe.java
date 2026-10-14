package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Cold original Earth runtime, including its first lock/desktop without an AOD cycle. */
public final class XiaomiStartupProbe extends Instrumentation {
    private XiaomiPlayer player;private ImageReader reader;private volatile long frames,lit;private StringBuilder log=new StringBuilder();
    interface Work{void run()throws Exception;}
    void main(Work work)throws Exception{Throwable[] error={null};java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);new Handler(Looper.getMainLooper()).post(()->{try{work.run();}catch(Throwable t){error[0]=t;}finally{done.countDown();}});if(!done.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Main-thread operation timed out");if(error[0]!=null)throw new Exception(error[0]);}
    void progress(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();boolean ok=false;HandlerThread drain=new HandlerThread("FrameDrain");drain.start();
        try{
            Context c=getTargetContext();startActivitySync(new android.content.Intent(c,XiaomiProbeActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));File apk=new File(c.getFilesDir(),"earth.apk");
            if(!apk.exists())try(InputStream in=c.getAssets().open("earth.apk");FileOutputStream out=new FileOutputStream(apk)){apk.setReadOnly();byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
            progress("prepare");XiaomiPlayer.Runtime runtime=XiaomiPlayer.prepare(c,"earth",apk,"b1480694130ffc4d8132e9dcb2d04ac3220d7762bae150784de5314469ae7df1");progress("prepared");
            Context display=c.createDisplayContext(c.getSystemService(DisplayManager.class).getDisplay(0));
            for(String initial:new String[]{"ForceLock","ForceLand"}){
                progress("create "+initial);frames=lit=0;reader=ImageReader.newInstance(360,792,PixelFormat.RGBA_8888,3);
                reader.setOnImageAvailableListener(r->{try(Image im=r.acquireLatestImage()){if(im==null)return;Image.Plane plane=im.getPlanes()[0];ByteBuffer b=plane.getBuffer();long nonblack=0;for(int y=0;y<im.getHeight();y+=8)for(int x=0;x<im.getWidth();x+=8){int i=y*plane.getRowStride()+x*plane.getPixelStride();if((b.get(i)&255)+(b.get(i+1)&255)+(b.get(i+2)&255)>30)nonblack++;}lit=nonblack;frames++;}},new Handler(drain.getLooper()));
                Holder h=new Holder(reader.getSurface());
                main(()->{player=new XiaomiPlayer(runtime,h,display);player.surfaceChanged(360,792,120);player.running(true);player.send("Night");player.initialize(0,false);player.send(initial);player.send("Offset_33.3");});
                if(!h.layoutFallback)throw new AssertionError("Expected layout-only wallpaper sizing fallback on this high-resolution display");
                progress("created "+initial);SystemClock.sleep(2200);snapshot(initial);
                main(()->player.send("Desk"));SystemClock.sleep(2200);snapshot(initial+" -> Desk");
                main(()->player.send("AOD"));SystemClock.sleep(1200);snapshot(initial+" -> Desk -> AOD");
                main(()->player.send("Desk"));SystemClock.sleep(2200);snapshot(initial+" -> Desk -> AOD -> Desk");
                main(()->{player.close();player=null;});reader.close();reader=null;
            }
            ok=true;log.append("XIAOMI_STARTUP_OK\n");
        }catch(Throwable t){log.append(android.util.Log.getStackTraceString(t));}
        finally{try{main(()->{if(player!=null)player.close();});}catch(Exception ignored){}if(reader!=null)reader.close();drain.quitSafely();}
        result.putString("stream",log.toString());finish(ok?-1:1,result);
    }
    void snapshot(String label)throws Exception{int start=log.length();log.append(label).append(" frames=").append(frames).append(" lit=").append(lit);main(()->{Object original=field(player,"player");for(String name:new String[]{"curState","preState","homeFraction","curAspect","cameraFov","realTimeFov","isHomeTransRunning"})log.append(' ').append(name).append('=').append(field(original,name));});log.append('\n');progress(log.substring(start));if(frames<2||lit<20)throw new AssertionError(label+" did not render visible content");}
    static final class Holder implements SurfaceHolder {
        final Surface surface;boolean layoutFallback;final ArrayList<Callback> callbacks=new ArrayList<>();Holder(Surface s){surface=s;}
        public void addCallback(Callback c){callbacks.add(c);}public void removeCallback(Callback c){callbacks.remove(c);}
        public Surface getSurface(){return surface;}public Rect getSurfaceFrame(){return new Rect(0,0,360,792);}
        public boolean isCreating(){return false;}public void setType(int t){}public void setFixedSize(int w,int h){throw new UnsupportedOperationException("Wallpapers currently only support sizing from layout");}public void setSizeFromLayout(){layoutFallback=true;}public void setFormat(int f){}public void setKeepScreenOn(boolean b){}
        public Canvas lockCanvas(){throw new UnsupportedOperationException();}public Canvas lockCanvas(Rect r){return lockCanvas();}public Canvas lockHardwareCanvas(){return lockCanvas();}public void unlockCanvasAndPost(Canvas c){}
    }
}
