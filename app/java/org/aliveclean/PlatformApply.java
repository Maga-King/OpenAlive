package org.aliveclean;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;
import org.json.JSONObject;

/** Short-lived root-launched platform adapter, inside the same APK. No resident daemon. */
public final class PlatformApply {
    private static final String PROVIDER="com.oplus.aod.AodMachineHelperProvider";
    public static void main(String[] args) {
        String stage="startup";
        System.out.println("ALIVE_ADAPTER_STARTED");
        try{
            if(Process.myUid()!=1000)throw new SecurityException("Platform adapter requires the system UID");
            if(args.length!=1||(!"local-aod".equals(args[0])&&!"panoramic-aod".equals(args[0])&&!"clock-startup".equals(args[0])&&!"motion-permission".equals(args[0])))throw new IllegalArgumentException("Unsupported operation");
            if(android.os.Build.VERSION.SDK_INT<31)throw new UnsupportedOperationException("Local AOD adapter requires Android 12 or later");
            if("clock-startup".equals(args[0])){
                stage="clock-startup";
                ColorOsClockStartupPolicy.allow();
                System.out.println("ALIVE_RESULT "+new JSONObject().put("ok",true).put("clockStartupAllowed",true));
                System.exit(0);
            }
            stage="wallpaper";
            Looper.prepareMainLooper();
            Class<?> thread=Class.forName("android.app.ActivityThread");Object instance=thread.getMethod("systemMain").invoke(null);
            Context context=(Context)thread.getMethod("getSystemContext").invoke(instance);
            if("motion-permission".equals(args[0])){
                stage="motion-permission";
                boolean allowed=ColorOsMotionPermission.allow(context);
                System.out.println("ALIVE_RESULT "+new JSONObject().put("ok",true).put("motionAllowed",allowed));
                System.exit(0);
            }
            WallpaperManager manager=WallpaperManager.getInstance(context);
            WallpaperInfo info=AppliedWallpaper.lock(manager);
            if(!AppliedWallpaper.supported(info))throw new IllegalStateException("请在系统壁纸应用器中将 OpenAlive 应用到锁屏或桌面与锁屏");
            stage="settings";
            JSONObject report;
            try(PlatformProvider settings=new PlatformProvider("settings",0)){
            int before=settings.secureInt("Setting_AodState",-1);
            int local=settings.secureInt("Setting_AodState_Local",0);
            int target="panoramic-aod".equals(args[0])?2:before==0||before==1?before:local==1?1:0;
            // This provider executes the official Settings + selected clock + theme transaction.
            // In particular, classic mode needs more than writing Setting_AodState=0.
            stage="mode";
            try(PlatformProvider aod=new PlatformProvider(PROVIDER,0)){
            Bundle result=aod.call(target==2?"set_aod_display_panoramic_mode":target==1?"set_aod_display_inspiration_mode":"set_aod_display_classic_mode",null,null);
            if(result==null||result.getInt("apiproxy_biz_code",0)>=400)throw new IllegalStateException("ColorOS rejected the AOD mode: "+result);
            stage="enable";
            Bundle enabled=aod.call("set_aod_display_open",null,null);
            if(enabled==null||enabled.getInt("apiproxy_biz_code",0)>=400)throw new IllegalStateException("ColorOS rejected AOD enable: "+enabled);
            }
            stage="verify";
            int after=settings.secureInt("Setting_AodState",-1);
            int on=settings.secureInt("Setting_AodSwitchEnable",0);
            if(after!=target||on!=1)throw new IllegalStateException("AOD verification failed: expected="+target+", actual="+after+", enabled="+on);
            report=new JSONObject().put("ok",true).put("before",before).put("mode",after);
            }
            // A missing vendor battery interface must not undo a successful wallpaper apply.
            try{ColorOsBackgroundPolicy.allow();report.put("backgroundAllowed",true);}
            catch(Exception background){report.put("backgroundAllowed",false).put("backgroundError",background.toString());}
            try{report.put("motionAllowed",ColorOsMotionPermission.allow(context));}
            catch(Exception motion){report.put("motionAllowed",false).put("motionError",motion.toString());}
            // Release both leases before exiting: System.exit does not run finally blocks.
            System.out.println("ALIVE_RESULT "+report);System.exit(0);
        }catch(Throwable error){
            try{System.out.println("ALIVE_RESULT "+new JSONObject().put("ok",false).put("stage",stage).put("error",error.toString()));}catch(Exception ignored){}
            System.exit(1);
        }
    }
}
