package org.aliveclean.probe;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.json.*;

/** Offscreen clock checks; the runner can show a foreground page to avoid OEM freezing. */
public final class XiaomiAodProbe extends Instrumentation {
    final Handler main=new Handler(Looper.getMainLooper());
    private ClassLoader installedLoader,candidateLoader;
    static final class TrackingContext extends android.content.ContextWrapper {
        int registered,unregistered;
        TrackingContext(Context base){super(base);}
        @Override public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver,android.content.IntentFilter filter){
            android.content.Intent result=super.registerReceiver(receiver,filter);if(receiver!=null)registered++;return result;
        }
        @Override public android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver,android.content.IntentFilter filter,int flags){
            android.content.Intent result=super.registerReceiver(receiver,filter,flags);if(receiver!=null)registered++;return result;
        }
        @Override public void unregisterReceiver(android.content.BroadcastReceiver receiver){super.unregisterReceiver(receiver);unregistered++;}
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    void note(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
    void render(boolean fixed,Context module,Bundle metadata,String family)throws Exception{
        TrackingContext test=new TrackingContext(getTargetContext());String apk=module.getApplicationInfo().sourceDir;
        String path=fixed?test.getApplicationInfo().sourceDir+File.pathSeparator+apk:apk;
        ClassLoader loader=fixed?candidateLoader:installedLoader;
        if(loader==null){
            loader=new DexClassLoader(path,test.getCodeCacheDir().getPath(),null,Context.class.getClassLoader());
            if(fixed)candidateLoader=loader;else installedLoader=loader;
        }
        Class<?> provider=loader.loadClass("org.aliveclean.NativeClockProvider");
        Method resources=provider.getDeclaredMethod("resourceContext",Context.class,android.content.res.AssetManager.class);resources.setAccessible(true);
        Context host=(Context)resources.invoke(null,test,module.getAssets());
        Class<?> type=loader.loadClass("org.aliveclean.XiaomiAodPreview$Renderer");
        Constructor<?> constructor=type.getDeclaredConstructor(Context.class,Bundle.class);constructor.setAccessible(true);
        Object renderer=constructor.newInstance(host,metadata);
        Field field=type.getDeclaredField("view");field.setAccessible(true);View root=(View)field.get(renderer);
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        ((android.view.WindowManager)test.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        int w=dm.widthPixels,h=dm.heightPixels;
        JSONObject result=new JSONObject();result.put("variant",fixed?"candidate":"installed");
        result.put("family",family);
        result.put("width",w);result.put("height",h);result.put("hostDpi",dm.densityDpi);
        result.put("stableDpi",android.util.DisplayMetrics.DENSITY_DEVICE_STABLE);
        result.put("xdpi",dm.xdpi);result.put("ydpi",dm.ydpi);
        result.put("aodDpi",root.getResources().getDisplayMetrics().densityDpi);
        for(String id:new String[]{"time","time2","date","date_lunar","city","city2"}){
            View v=root.findViewById(root.getResources().getIdentifier(id,"id","com.miui.aod"));
            if(!(v instanceof TextView))continue;TextView tv=(TextView)v;
            JSONObject item=new JSONObject();item.put("textSize",tv.getTextSize());item.put("weight",tv.getTypeface().getWeight());
            item.put("visible",tv.getVisibility());item.put("text",tv.getText());
            if(id.equals("time")||id.equals("time2")){
                android.graphics.Typeface original=android.graphics.Typeface.createFromAsset(root.getResources().getAssets(),"fonts/MiClock-Light.otf");
                item.put("originalTimeFont",original.equals(tv.getTypeface()));
                if(!original.equals(tv.getTypeface()))throw new AssertionError("Time font changed: "+id);
            }
            result.put(id,item);
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
        root.layout(0,0,w,h);Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
        int lit=0,minX=w,minY=h,maxX=-1,maxY=-1;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=bitmap.getPixel(x,y);if((c>>>24)>0&&(c&0xffffff)!=0){lit++;minX=Math.min(x,minX);minY=Math.min(y,minY);maxX=Math.max(x,maxX);maxY=Math.max(y,maxY);}}
        result.put("litPixels",lit);result.put("bounds",new JSONArray(new int[]{minX,minY,maxX,maxY}));
        if(lit<100)throw new AssertionError("No clock rendered");
        File file=new File(test.getFilesDir(),fixed?(family.equals("snowmountain")?"candidate.png":"candidate-"+family+".png"):"installed.png");
        try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        if(fixed)checkBattery(renderer,root,test,result,family);
        note(result.toString());
    }
    void checkBattery(Object renderer,View root,TrackingContext host,JSONObject result,String family)throws Exception{
        View battery=find(root,"aod_battery_layout"),icon=find(root,"aod_battery_icon");
        TextView number=(TextView)find(root,"aod_battery_digital");
        View container=find(root,"battery_container");
        if(battery==null||icon==null||number==null||container==null||!battery.getClass().getName().equals("org.aliveclean.XiaomiAodBattery"))
            throw new AssertionError("Battery missing or vendor controller still attached");
        result.put("batteryText",number.getText());result.put("batterySize",number.getTextSize());
        if(container.getVisibility()!=View.VISIBLE||icon.getWidth()<=0||icon.getHeight()<=0)throw new AssertionError("Live battery not visible");
        Method active=renderer.getClass().getDeclaredMethod("active",boolean.class);active.setAccessible(true);
        active.invoke(renderer,true);active.invoke(renderer,true);
        if(host.registered!=1)throw new AssertionError("Duplicate battery receivers");
        active.invoke(renderer,false);active.invoke(renderer,false);
        if(host.unregistered!=1)throw new AssertionError("Battery receiver remains active");
        Method read=battery.getClass().getDeclaredMethod("read",android.content.Intent.class);read.setAccessible(true);
        java.util.Set<Integer> hashes=new java.util.HashSet<>();int count=0;
        for(int level:new int[]{0,1,19,20,50,99,100})for(boolean charging:new boolean[]{false,true}){
            android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED)
                    .putExtra("level",level).putExtra("scale",100).putExtra("plugged",charging?1:0).putExtra("status",charging?2:3);
            read.invoke(battery,intent);
            String expected=root.getResources().getString(root.getResources().getIdentifier("battery_level","string","com.miui.aod"),level);
            if(!number.getText().toString().equals(expected)||container.getVisibility()!=View.VISIBLE)throw new AssertionError("Battery level mismatch: "+level);
            Bitmap frame=Bitmap.createBitmap(icon.getWidth(),icon.getHeight(),Bitmap.Config.ARGB_8888);icon.draw(new Canvas(frame));
            int[] pixels=new int[frame.getWidth()*frame.getHeight()];frame.getPixels(pixels,0,frame.getWidth(),0,0,frame.getWidth(),frame.getHeight());
            hashes.add(java.util.Arrays.hashCode(pixels));
            if(family.equals("snowmountain"))
                try(FileOutputStream out=new FileOutputStream(new File(host.getFilesDir(),"battery-"+level+"-"+charging+".png"))){frame.compress(Bitmap.CompressFormat.PNG,100,out);}
            frame.recycle();count++;
        }
        if(hashes.size()<6)throw new AssertionError("Battery artwork did not change with charge level/state");
        read.invoke(battery,new android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).putExtra("level",-1).putExtra("scale",100));
        if(container.getVisibility()!=View.GONE)throw new AssertionError("Unknown battery fabricated a percentage");
        Class<?> controller=icon.getClass().getClassLoader().loadClass("com.miui.aod.util.BatteryController");
        Field instance=controller.getDeclaredField("sInstance");instance.setAccessible(true);
        if(instance.get(null)!=null)throw new AssertionError("Vendor battery service was started");
        result.put("batteryStatesChecked",count);result.put("distinctBatteryFrames",hashes.size());result.put("receiverReleased",true);
    }
    static View find(View root,String name){return root.findViewById(root.getResources().getIdentifier(name,"id","com.miui.aod"));}
    public void onStart(){Bundle result=new Bundle();boolean ok=false;
        try{
            Context module=getTargetContext().createPackageContext("org.aliveclean",0);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=module.getAssets().open("xiaomi/catalog.json")){byte[] b=new byte[4096];for(int n;(n=in.read(b))!=-1;)bytes.write(b,0,n);}
            JSONArray catalog=new JSONArray(bytes.toString("UTF-8"));
            for(boolean fixed:new boolean[]{false,true})for(int entry=0;entry<catalog.length();entry++){
                JSONObject pack=catalog.getJSONObject(entry);String family=pack.getString("id");
                if(!fixed&&!family.equals("snowmountain"))continue;
                note("render "+(fixed?"candidate ":"installed ")+family);
                JSONObject clock=pack.getJSONObject("clock");
                Bundle metadata=new Bundle();for(java.util.Iterator<String> it=clock.keys();it.hasNext();){String key=it.next();if(key.equals("support_change_with_time"))metadata.putInt(key,clock.getInt(key));else metadata.putFloat(key,(float)clock.getDouble(key));}
                CountDownLatch done=new CountDownLatch(1);Throwable[] error={null};
                main.post(()->{try{render(fixed,module,metadata,family);}catch(Throwable e){error[0]=e;}finally{done.countDown();}});
                if(!done.await(40,TimeUnit.SECONDS))throw new AssertionError("Renderer timed out");
                if(error[0]!=null)throw new Exception(error[0]);
            }
            ok=true;result.putString("stream","XIAOMI_AOD_RENDER_OK\n");
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));}
        finish(ok?-1:1,result);
    }
}
