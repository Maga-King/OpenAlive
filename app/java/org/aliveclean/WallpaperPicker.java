package org.aliveclean;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.widget.Toast;
import java.util.HashSet;
import java.util.Set;

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
        Intent change=target(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER),wallpaper);
        // MATCH_ALL also finds handlers without CATEGORY_DEFAULT. Explicitly route
        // around a stale default or a rejected resolver, keeping the service extra.
        Set<ComponentName> tried=new HashSet<>();
        for(ResolveInfo result:activity.getPackageManager().queryIntentActivities(change,PackageManager.MATCH_ALL)){
            ActivityInfo info=result.activityInfo;
            if(info==null||!info.exported||!info.enabled)continue;
            ComponentName entry=new ComponentName(info.packageName,info.name);
            if(tried.add(entry)&&start(activity,new Intent(change).setComponent(entry),request))return true;
        }
        // Some OEM builds keep the public activity but omit its intent filter.
        // LiveWallpaperPreview itself is private; do not launch it with guessed extras.
        ComponentName legacy=new ComponentName("com.android.wallpaper.livepicker",
                "com.android.wallpaper.livepicker.LiveWallpaperChange");
        if(tried.add(legacy)&&start(activity,new Intent(change).setComponent(legacy),request))return true;
        if(start(activity,target(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),wallpaper),request)){
            manualSelection(activity);return false;
        }
        for(String name:PACKAGES){
            Intent launch=activity.getPackageManager().getLaunchIntentForPackage(name);
            if(launch!=null){
                // NEW_TASK causes an immediate canceled result, before the user applies.
                launch.setFlags(0);
                if(start(activity,target(launch,wallpaper),request)){manualSelection(activity);return false;}
            }
        }
        // Generic SET_WALLPAPER can resolve to a photo gallery. Keep it last.
        if(start(activity,target(new Intent(Intent.ACTION_SET_WALLPAPER),wallpaper),request)){
            manualSelection(activity);return false;
        }
        throw new ActivityNotFoundException("未找到可用的系统壁纸应用，请启用系统壁纸或主题应用后重试");
    }

    private static Intent target(Intent intent,ComponentName wallpaper){
        return intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,wallpaper);
    }

    private static void manualSelection(Activity activity){
        // A launcher is not a component-preview API: OEMs may ignore the extra.
        Toast.makeText(activity,"系统未提供直接预览入口，请在动态壁纸中选择 OpenAlive 对应壁纸；若列表没有它，请启用系统动态壁纸选择器",Toast.LENGTH_LONG).show();
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
