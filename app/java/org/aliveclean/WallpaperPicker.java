package org.aliveclean;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;

/** Public wallpaper intents first, then the wallpaper app's regular entry point. */
final class WallpaperPicker {
    private static final String[] PACKAGES={
        "com.oplus.wallpapers","com.android.wallpaper.livepicker",
        "com.google.android.apps.wallpaper","com.android.wallpaper",
        "com.heytap.themestore","com.oplus.themestore","com.android.thememanager"
    };

    // Only the component-specific preview confirms this wallpaper with RESULT_OK.
    // Lists and app launchers may apply a different wallpaper; verify changed IDs.
    static boolean open(Activity activity,ComponentName wallpaper,int request){
        if(start(activity,new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,wallpaper),request))return true;
        if(start(activity,new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),request))return false;
        if(start(activity,new Intent(Intent.ACTION_SET_WALLPAPER),request))return false;
        for(String name:PACKAGES){
            Intent launch=activity.getPackageManager().getLaunchIntentForPackage(name);
            if(launch!=null){
                // NEW_TASK causes an immediate canceled result, before the user applies.
                launch.setFlags(0);
                if(start(activity,launch,request))return false;
            }
        }
        throw new ActivityNotFoundException("未找到可用的系统壁纸应用，请启用系统壁纸或主题应用后重试");
    }

    private static boolean start(Activity activity,Intent intent,int request){
        try{activity.startActivityForResult(intent,request);return true;}
        catch(ActivityNotFoundException|SecurityException e){
            android.util.Log.w("AliveClean","Wallpaper entry unavailable: "+intent,e);return false;
        }
    }

    static boolean applied(WallpaperManager manager,ComponentName expected,int result,
            boolean reliableResult,int previousHomeId,int previousLockId){
        boolean confirmed=reliableResult&&result==Activity.RESULT_OK;
        WallpaperInfo home=manager.getWallpaperInfo();
        if(home!=null&&expected.equals(home.getComponent())
                &&(confirmed||manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)!=previousHomeId))return true;
        if(Build.VERSION.SDK_INT>=34){
            WallpaperInfo lock=manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
            if(lock!=null&&expected.equals(lock.getComponent())
                    &&(confirmed||manager.getWallpaperId(WallpaperManager.FLAG_LOCK)!=previousLockId))return true;
        }
        return false;
    }
}
