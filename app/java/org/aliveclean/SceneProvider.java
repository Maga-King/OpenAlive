package org.aliveclean;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Authenticated Binder bootstrap. No image files or unrestricted operations are exposed. */
public final class SceneProvider extends ContentProvider {
    static final Uri CONFIGURATION=Uri.parse("content://org.aliveclean.scene/configuration");
    static void changed(Context context){context.getContentResolver().notifyChange(CONFIGURATION,null);}
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String argument,Bundle extras){
        Context c=getContext();
        if(c.checkCallingOrSelfPermission("android.permission.STATUS_BAR")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("System UI permission required");
        if(!"connect".equals(method)&&!"configuration".equals(method))throw new IllegalArgumentException("Unknown operation");
        try{
            Bundle result=new Bundle();
            NotificationOptions.export(c,result);
            LockTweaks.export(c,result);
            if("connect".equals(method))result.putBinder("channel",SceneChannel.connect(c,extras==null?null:extras.getBinder("owner"),extras==null?0:extras.getInt("clock_api",0),extras==null?null:extras.getString("clock_error"),extras==null?null:extras.getBinder("clock_feedback")));
            SceneOptions options=new SceneOptions(c.getSharedPreferences(SceneOptions.APPLIED,0));
            result.putInt("aod",options.aod);
            result.putBoolean("vivo_wallpaper",options.vivo!=null);
            result.putBoolean("continuous_aod",options.vivo!=null?options.vivo.animateInAod():options.cosmic!=0?options.cosmicContinuousAod:options.aod==0&&options.sailContinuousAod);
            Bundle xiaomiClock=XiaomiState.lockClock(c);
            if(xiaomiClock!=null){
                result.putInt("aod",101);result.putBoolean("vivo_wallpaper",false);
                result.putBoolean("continuous_aod",false);
                result.putBundle("xiaomi_clock",xiaomiClock);
            }
            // 自适应 AOD: panoramic AOD belongs to ColorOS. No independent clock;
            // the wallpaper only refreshes for the chosen play window.
            if(AdaptiveAod.active(c)){
                int play=AdaptiveAod.playSeconds(c);
                result.putInt("aod",-1);result.remove("xiaomi_clock");result.putBoolean("adaptive_aod",true);
                result.putBoolean("continuous_aod",play!=0);
                result.putLong("continuous_aod_ms",play>0?play*1000L:0);
            }
            return result;
        }catch(Exception error){throw new IllegalStateException("Scene channel unavailable",error);}
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
