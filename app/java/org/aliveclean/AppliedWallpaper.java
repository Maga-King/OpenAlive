package org.aliveclean;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.os.Build;

/** Resolve the actual lock wallpaper, including a lock screen that follows home. */
final class AppliedWallpaper {
    static WallpaperInfo lock(WallpaperManager manager){
        WallpaperInfo info=Build.VERSION.SDK_INT>=34
                ?manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK):manager.getWallpaperInfo();
        if(info==null&&manager.getWallpaperId(WallpaperManager.FLAG_LOCK)<0)info=manager.getWallpaperInfo();
        return info;
    }

    static boolean supported(WallpaperInfo info){
        if(info==null)return false;
        ComponentName component=info.getComponent();
        if(!"org.aliveclean".equals(component.getPackageName()))return false;
        switch(component.getClassName()){
            case "org.aliveclean.CleanWallpaper":
            case "org.aliveclean.XiaomiWallpaper$Earth":
            case "org.aliveclean.XiaomiWallpaper$Moon":
            case "org.aliveclean.XiaomiWallpaper$Mars":
            case "org.aliveclean.XiaomiWallpaper$Saturn":
            case "org.aliveclean.XiaomiWallpaper$Snowmountain":
            case "org.aliveclean.XiaomiWallpaper$Geometry":return true;
            default:return false;
        }
    }
}
