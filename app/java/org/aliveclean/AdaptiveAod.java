package org.aliveclean;

import android.content.*;
import android.provider.Settings;

/**
 * 自适应 AOD: under ColorOS panoramic (全景沉浸) AOD every wallpaper keeps its
 * lock composition and the stock ColorOS AOD clock stays. Local AOD is untouched.
 */
final class AdaptiveAod {
    static final String PREFS="adaptive_aod";
    /** Play-after-screen-off choices in seconds; 0 is off, -1 keeps playing. */
    static final int[] PLAY_CHOICES={0,3,5,10,30,-1};
    // Long enough for a Desk->Lock move to finish under the AOD fade-in.
    static final long SETTLE_MS=2000;

    private static SharedPreferences prefs(Context c){
        // Renderers live in several processes; re-read the file on each lookup.
        return c.getSharedPreferences(PREFS,Context.MODE_MULTI_PROCESS);
    }
    static boolean enabled(Context c){return prefs(c).getBoolean("enabled",true);}
    static int playSeconds(Context c){int s=prefs(c).getInt("play_seconds",0);for(int v:PLAY_CHOICES)if(v==s)return s;return 0;}
    static void save(Context c,boolean enabled,int playSeconds){
        prefs(c).edit().putBoolean("enabled",enabled).putInt("play_seconds",playSeconds).commit();
        SceneProvider.changed(c);
    }
    static boolean panoramic(Context c){
        try{
            ContentResolver r=c.getContentResolver();
            return Settings.Secure.getInt(r,"Setting_AodSwitchEnable",0)==1&&Settings.Secure.getInt(r,"Setting_AodState",-1)==2;
        }catch(RuntimeException e){return false;}
    }
    static boolean active(Context c){return enabled(c)&&panoramic(c);}
    /** Milliseconds a wallpaper keeps rendering after AOD entry; -1 is unbounded. */
    static long renderMillis(Context c){int s=playSeconds(c);return s<0?-1:Math.max(SETTLE_MS,s*1000L);}
    static String playLabel(int s){return s==0?"关闭":s<0?"一直播放":s+" 秒";}
}
