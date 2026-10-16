package org.aliveclean;

import android.content.*;
import android.os.Bundle;

/** Optional lock screen and panoramic AOD adjustments. Defaults keep ColorOS behaviour. */
final class LockTweaks {
    private static final String PREFS="lock_tweaks";
    /** Panoramic AOD shrink of clock, notifications and bottom area: official 0.9, half, none. */
    static final float[] AOD_SCALES={0.9f,0.95f,1f};

    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_MULTI_PROCESS);}
    static int aodScaleIndex(Context c){int i=prefs(c).getInt("aod_scale",0);return i>=0&&i<AOD_SCALES.length?i:0;}
    static boolean aodShortcuts(Context c){return prefs(c).getBoolean("aod_shortcuts",false);}
    static boolean sinkLargeClock(Context c){return prefs(c).getBoolean("sink_large_clock",false);}
    static void save(Context c,String key,Object value){
        SharedPreferences.Editor e=prefs(c).edit();
        if(value instanceof Boolean)e.putBoolean(key,(Boolean)value);else e.putInt(key,(Integer)value);
        e.commit();SceneProvider.changed(c);
    }
    static void export(Context c,Bundle out){
        out.putFloat("aod_scale",AOD_SCALES[aodScaleIndex(c)]);
        out.putBoolean("aod_shortcuts",aodShortcuts(c));
        out.putBoolean("sink_large_clock",sinkLargeClock(c));
    }
}
