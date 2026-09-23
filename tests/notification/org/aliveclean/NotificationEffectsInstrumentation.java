package org.aliveclean;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NotificationEffectsInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Activity activity=null;
        try{
            NotificationPulseWindow pulse=new NotificationPulseWindow();
            check(!pulse.begin(1000,false,10),"must not own already lit AOD");
            check(pulse.begin(1000,true,10)&&pulse.remaining(1000)==10000,"dark pulse");
            pulse.begin(9000,false,15);check(pulse.remaining(9000)==15000,"extend owned pulse");
            pulse.begin(29000,false,15);check(pulse.remaining(29000)==2000,"notification burst capped");
            pulse.cancel();check(!pulse.active()&&!pulse.begin(30000,false,10),"user wake relinquishes ownership");
            geometry();
            Intent intent=new Intent(getTargetContext(),NotificationTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);final Activity shown=activity;
            StringBuilder info=new StringBuilder();
            for(boolean ring:new boolean[]{true,false}){
                FlymeNotificationLight.Model model=FlymeNotificationLight.load(getTargetContext(),ring);
                final FlymeNotificationLight[] light=new FlymeNotificationLight[1];
                AtomicBoolean finished=new AtomicBoolean();
                runOnMainSync(()->{
                    try{
                        light[0]=new FlymeNotificationLight(shown,model,ring);shown.setContentView(light[0]);
                        Point size=new Point();shown.getDisplay().getRealSize(size);
                        light[0].layoutForDisplay(size.x,size.y,shown.getDisplay().getCutout(),0,0);
                    }catch(Exception e){throw new RuntimeException(e);}
                });
                waitForIdleSync();
                runOnMainSync(()->light[0].play(()->finished.set(true)));
                SystemClock.sleep(Math.min(1000,model.duration()/3));
                Bitmap image=capture(shown);
                check(image!=null,"screenshot unavailable");
                if(ring){
                    int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
                    for(int y=0;y<image.getHeight()/4;y++)for(int x=0;x<image.getWidth();x++){
                        int c=image.getPixel(x,y);if(Color.red(c)>32&&Color.green(c)>32&&Color.blue(c)>32){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
                    }
                    check(right>left&&bottom>top,"missing ring pixels");
                    check(Math.abs((right-left)-(bottom-top))<=3,"ring stretched: "+(right-left)+" x "+(bottom-top));
                    Point size=new Point();shown.getDisplay().getRealSize(size);
                    RectF hole=CameraHoleGeometry.resolve(shown.getDisplay(),shown.getDisplay().getCutout(),size.x,size.y,shown.getResources().getDisplayMetrics().density).get(0);
                    check(Math.abs((left+right)/2f-hole.centerX())<3&&Math.abs((top+bottom)/2f-hole.centerY())<3,"ring not centered on physical camera");
                    double nearest=Double.MAX_VALUE;
                    for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
                        int c=image.getPixel(x,y);if(Color.red(c)>32&&Color.green(c)>32&&Color.blue(c)>32)
                            nearest=Math.min(nearest,Math.hypot(x+.5-hole.centerX(),y+.5-hole.centerY()));
                    }
                    check(nearest>Math.max(hole.width(),hole.height())/2f,"light drawn inside physical camera: "+nearest);
                    info.append("ringPixels=").append(new Rect(left,top,right+1,bottom+1)).append(" camera=").append(hole).append(';');
                }
                long visible=0;for(int y=0;y<image.getHeight();y+=2)for(int x=0;x<image.getWidth();x+=2){int c=image.getPixel(x,y);if((c&0xffffff)!=0)visible++;}
                check(visible>80,"empty official animation "+ring);
                try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),ring?"ring.png":"edge.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
                SystemClock.sleep(model.duration()+500);
                check(finished.get(),"one-shot did not finish "+ring);
                if(ring){
                    runOnMainSync(()->{light[0].setRingColor(NotificationOptions.RING_BLUE);light[0].seek(model.frames/3);});
                    SystemClock.sleep(250);
                    Bitmap tinted=capture(shown);long red=0,blue=0;
                    for(int y=0;y<tinted.getHeight()/4;y++)for(int x=0;x<tinted.getWidth();x++){int c=tinted.getPixel(x,y);red+=Color.red(c);blue+=Color.blue(c);}
                    check(blue>red*3&&blue>1000,"ring tint not applied");
                    try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"ring-blue.png"))){tinted.compress(Bitmap.CompressFormat.PNG,100,out);}tinted.recycle();
                    runOnMainSync(()->{light[0].setRingColor(0xff00ff00);light[0].seek(model.frames/3);});SystemClock.sleep(150);
                    Bitmap custom=capture(shown);long green=0,other=0;
                    for(int y=0;y<custom.getHeight()/4;y++)for(int x=0;x<custom.getWidth();x++){int c=custom.getPixel(x,y);green+=Color.green(c);other+=Color.red(c)+Color.blue(c);}
                    check(green>1000&&other<green/10,"custom ring tint not applied");custom.recycle();
                }
                runOnMainSync(()->{finished.set(false);light[0].play(()->finished.set(true));light[0].stop();});
                SystemClock.sleep(200);check(!finished.get(),"cancel incorrectly completed");
                info.append(ring?"ring":"edge").append(" frames=").append(model.frames).append(" fps=").append(model.fps).append(" visible=").append(visible).append(';');
            }
            result.putString("stream","NOTIFICATION_EFFECTS_OK "+info);
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finally{if(activity!=null){Activity a=activity;runOnMainSync(a::finish);}}
        finish(result.getString("stream","").startsWith("NOTIFICATION_EFFECTS_OK")?-1:1,result);
    }
    private static void geometry(){
        check(Math.abs(FlymeNotificationLight.holes(null,1080,2400,3).get(0).centerX()-540)<1,"default center");
        if(Build.VERSION.SDK_INT>=33){
            Path path=new Path();path.addCircle(70,65,20,Path.Direction.CW);path.addCircle(130,65,20,Path.Direction.CW);
            DisplayCutout cutout=new DisplayCutout.Builder().setCutoutPath(path).setBoundingRectTop(new Rect(50,0,150,85)).build();
            List<RectF> holes=FlymeNotificationLight.holes(cutout,1080,2400,3);
            check(holes.size()==2&&Math.abs(holes.get(0).centerX()-70)<1&&Math.abs(holes.get(1).centerY()-65)<1,"separate actual cutout contours");
        }
        DisplayCutout bounds=new DisplayCutout(new Rect(0,80,0,0),Arrays.asList(new Rect(40,0,80,80)));
        RectF hole=FlymeNotificationLight.holes(bounds,1080,2400,3).get(0);
        check(hole.centerX()==540,"unsafe exclusion rectangle should use default");
        Path exclusion=new Path();exclusion.addRect(682,0,758,160,Path.Direction.CW);
        if(Build.VERSION.SDK_INT>=33){
            DisplayCutout rectangularPath=new DisplayCutout.Builder().setCutoutPath(exclusion).build();
            RectF fallback=FlymeNotificationLight.holes(rectangularPath,1440,3168,4).get(0);
            check(fallback.width()==fallback.height()&&fallback.centerY()==96,"rectangular cutout path must not become a stretched ring");
        }
        RectF actual=CameraHoleGeometry.mapProperty("679,39:761,121",1440,3168,1440,3168,0).get(0);
        check(actual.centerX()==720&&actual.centerY()==80&&actual.width()==82,"ColorOS physical mask");
        check(FlymeNotificationLight.ringSize(actual,4)*72f/111f>=98,"inner edge needs outward clearance");
        RectF reduced=CameraHoleGeometry.mapProperty("679,39:761,121",1440,3168,1080,2376,0).get(0);
        check(reduced.centerX()==540&&reduced.centerY()==60&&reduced.width()==61.5f,"resolution mapping");
        RectF rotated=CameraHoleGeometry.mapProperty("679,39:761,121",1440,3168,3168,1440,1).get(0);
        check(rotated.centerX()==80&&rotated.centerY()==720,"rotation mapping");
        check(CameraHoleGeometry.mapProperty("NaN,0:90,90",1440,3168,1440,3168,0).isEmpty(),"invalid property rejected");
    }
    private static Bitmap capture(Activity activity)throws Exception {
        View decor=activity.getWindow().getDecorView();
        Bitmap bitmap=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);int[] result={-1};
        PixelCopy.request(activity.getWindow(),bitmap,status->{result[0]=status;done.countDown();},new Handler(Looper.getMainLooper()));
        check(done.await(3,java.util.concurrent.TimeUnit.SECONDS)&&result[0]==PixelCopy.SUCCESS,"test window capture failed");return bitmap;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
