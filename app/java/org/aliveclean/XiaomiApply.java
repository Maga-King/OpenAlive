package org.aliveclean;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** System-UID adapter restricted to this app's six wallpaper components. */
public final class XiaomiApply {
    static void apply(Context c,XiaomiPacks.Pack p)throws Exception {
        if(android.os.Process.myUid()/100000!=0)throw new IOException(I18n.t("目前仅支持主用户"));
        String path=c.getApplicationInfo().sourceDir;
        String command="CLASSPATH='"+path.replace("'","'\"'\"'")+"' /system/bin/app_process /system/bin org.aliveclean.XiaomiApply "+p.id;
        java.lang.Process process=new ProcessBuilder("su","1000","-c",command).redirectErrorStream(true).start();StringBuilder result=new StringBuilder();
        Thread reader=new Thread(()->{try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){for(String s;(s=in.readLine())!=null;)synchronized(result){if(result.length()<8192)result.append(s).append('\n');}}catch(IOException ignored){}},"XiaomiApplyOutput");reader.start();
        if(!process.waitFor(25,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException(I18n.t("壁纸应用超时，请检查 Root 授权"));}reader.join(1000);
        String output;synchronized(result){output=result.toString();}
        if(process.exitValue()!=0||!output.contains("XIAOMI_APPLIED"))throw new IOException(I18n.t("系统未完成壁纸应用：")+output.trim());
    }
    public static void main(String[] args){try{
        if(android.os.Process.myUid()!=1000)throw new SecurityException("System UID required");
        if(args.length!=1||!Arrays.asList("moon","earth","mars","snowmountain","geometry","saturn").contains(args[0]))throw new IllegalArgumentException("Unsupported component");
        Looper.prepareMainLooper();Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);Context c=(Context)at.getMethod("getSystemContext").invoke(thread);
        WallpaperManager wm=WallpaperManager.getInstance(c);String family=args[0];ComponentName component=new ComponentName("org.aliveclean","org.aliveclean.XiaomiWallpaper$"+Character.toUpperCase(family.charAt(0))+family.substring(1));
        try{WallpaperManager.class.getMethod("setWallpaperComponentWithFlags",ComponentName.class,int.class).invoke(wm,component,WallpaperManager.FLAG_SYSTEM|WallpaperManager.FLAG_LOCK);}
        catch(InvocationTargetException e){
            // HyperOS may throw after the binder transaction, in its optional
            // ThemeManager notification. Accept only a verified successful apply.
            WallpaperInfo actual=wm.getWallpaperInfo();if(!(e.getCause() instanceof SecurityException)||actual==null||!component.equals(actual.getComponent()))throw e;
        }
        WallpaperInfo actual=wm.getWallpaperInfo();if(actual==null||!component.equals(actual.getComponent()))throw new IllegalStateException("Wallpaper verification failed");
        if(Build.VERSION.SDK_INT>=34){WallpaperInfo lock=wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK);if(lock==null&&wm.getWallpaperId(WallpaperManager.FLAG_LOCK)<0)lock=actual;if(lock==null||!component.equals(lock.getComponent()))throw new IllegalStateException("Lock wallpaper verification failed");}
        System.out.println("XIAOMI_APPLIED");System.exit(0);
    }catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}}
}
