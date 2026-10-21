package org.aliveclean;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.concurrent.*;

/** Real BLAST SurfaceViews: release old framework surface before renderer handoff. */
public final class XiaomiSurfaceProbe extends Instrumentation {
    String family,digest;XiaomiPlayer player;XiaomiSurfaceActivity activity;SurfaceView view;
    final Handler main=new Handler(Looper.getMainLooper());
    interface Work {void run()throws Exception;}
    void ui(Work work)throws Exception{
        Throwable[] error={null};CountDownLatch latch=new CountDownLatch(1);
        main.post(()->{try{work.run();}catch(Throwable e){error[0]=e;}finally{latch.countDown();}});
        if(!latch.await(15,TimeUnit.SECONDS))throw new IOException("Main thread timed out");
        if(error[0]!=null)throw new Exception(error[0]);
    }
    void note(String value){Bundle b=new Bundle();b.putString("stream",value+"\n");sendStatus(0,b);}
    public void onCreate(Bundle args){super.onCreate(args);family=args.getString("family");digest=args.getString("digest");start();}
    void newSurface()throws Exception{
        CountDownLatch created=new CountDownLatch(1);
        ui(()->{view=activity.addSurface();view.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder h){}
            public void surfaceChanged(SurfaceHolder h,int f,int w,int height){if(w>0&&height>0)created.countDown();}
            public void surfaceDestroyed(SurfaceHolder h){}
        });});
        if(!created.await(10,TimeUnit.SECONDS))throw new IOException("No visible SurfaceView");
    }
    void removeSurface()throws Exception{
        CountDownLatch gone=new CountDownLatch(1);
        ui(()->{view.getHolder().addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder h){}
            public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
            public void surfaceDestroyed(SurfaceHolder h){gone.countDown();}
        });player.surfaceDestroyed();activity.removeSurface();});
        if(!gone.await(10,TimeUnit.SECONDS))throw new IOException("Old surface was not destroyed");
    }
    void pixels(String step)throws Exception{
        SystemClock.sleep(1600);Bitmap bitmap=Bitmap.createBitmap(180,396,Bitmap.Config.ARGB_8888);
        CountDownLatch copied=new CountDownLatch(1);int[] status={-1};
        ui(()->PixelCopy.request(view,bitmap,result->{status[0]=result;copied.countDown();},main));
        if(!copied.await(5,TimeUnit.SECONDS)||status[0]!=PixelCopy.SUCCESS)throw new AssertionError(step+" PixelCopy="+status[0]);
        int lit=0;for(int y=0;y<bitmap.getHeight();y+=4)for(int x=0;x<bitmap.getWidth();x+=4){int c=bitmap.getPixel(x,y);if(((c>>16)&255)+((c>>8)&255)+(c&255)>30)lit++;}
        bitmap.recycle();note(step+" nonblack="+lit);
        if(lit<20)throw new AssertionError(step+" black output");
    }
    public void onStart(){Bundle result=new Bundle();boolean ok=false;
        try{
            activity=(XiaomiSurfaceActivity)startActivitySync(new Intent(getTargetContext(),XiaomiSurfaceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            File apk=new File(getTargetContext().getFilesDir(),family+".apk");
            if(!apk.exists())try(InputStream in=getTargetContext().getAssets().open(family+".apk");FileOutputStream out=new FileOutputStream(apk)){apk.setReadOnly();byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
            XiaomiPlayer.Runtime runtime=XiaomiPlayer.prepare(getTargetContext(),family,apk,digest);
            newSurface();ui(()->{player=new XiaomiPlayer(runtime,view.getHolder(),activity);player.surfaceChanged(360,792,60);player.running(true);player.send("Night");player.initialize(0,true);player.send("ForceLock");});
            pixels("initial preview");
            for(int i=0;i<12;i++){
                note("handoff "+i+" destroying old BLAST surface");removeSurface();SystemClock.sleep(100);newSurface();
                final boolean preview=i%2!=0;
                ui(()->{player.surface(view.getHolder());player.surfaceChanged(360,792,60);player.running(true);player.initialize(0,preview);player.send(preview?"ForceLock":"ForceLand");});
                pixels("handoff "+i);
            }
            removeSurface();ui(()->{player.close();player=null;});SystemClock.sleep(1200);
            ok=true;result.putString("stream","XIAOMI_SURFACE_OK "+family+" swaps=12 teardown=ok\n");
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{try{ui(()->{if(player!=null)player.close();if(activity!=null)activity.finish();});}catch(Exception ignored){}}
        finish(ok?-1:1,result);
    }
}
