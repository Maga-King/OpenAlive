package org.aliveclean;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Looper;
import java.lang.reflect.Method;

/** Explicit device diagnostics; never included in the APK. */
public final class PlatformProbe {
    public static void main(String[] args)throws Exception {
        try {
        Looper.prepareMainLooper();
        Class<?> thread=Class.forName("android.app.ActivityThread");
        Object instance=thread.getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getMethod("getSystemContext").invoke(instance);
        WallpaperManager manager=WallpaperManager.getInstance(context);
        if(args.length==0){for(Method m:WallpaperManager.class.getDeclaredMethods())if(m.getName().contains("WallpaperComponent")||m.getName().contains("Ambient"))System.out.println(m);}
        else if(args[0].equals("apply")){
            Method setter=WallpaperManager.class.getDeclaredMethod("setWallpaperComponent",ComponentName.class);setter.setAccessible(true);
            System.out.println("APPLY_RESULT="+setter.invoke(manager,new ComponentName("org.aliveclean","org.aliveclean.CleanWallpaper")));
        }
        System.exit(0);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
}
