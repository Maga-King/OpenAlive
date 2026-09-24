package org.aliveclean;

import android.graphics.Bitmap;
import java.util.WeakHashMap;

/** Initialize native static color sampling before its first live wallpaper frame. */
final class NativeClockMaterialBootstrap {
    private static final WeakHashMap<Object,Boolean> pending=new WeakHashMap<>();
    static boolean seed(Object controller,Bitmap bitmap,int[] screen,Long key)throws Exception{
        if(bitmap==null||bitmap.isRecycled())return false;
        Class<?> type=controller.getClass();
        if(Boolean.TRUE.equals(type.getMethod("isColoringPrepared").invoke(controller))){pending.remove(controller);return false;}
        int mode=((Number)type.getMethod("getColoringMode$KeyguardPersonalityClocks_release").invoke(controller)).intValue();
        if(mode!=1&&mode!=2)return false;
        // Before the native clock has attached its sampling regions, seeding
        // can only fail. Leave that instance eligible when it becomes active.
        java.util.List<?> listeners=(java.util.List<?>)type.getMethod("getActiveColoringListeners$KeyguardPersonalityClocks_release").invoke(controller);
        if(listeners.isEmpty())return false;
        if(pending.containsKey(controller))return false;
        pending.put(controller,Boolean.TRUE);
        try{
            // setScreenShotEx deliberately drops live frames before prepareColoringData.
            // Seed the original sampler with this actual frame; do not forge readiness
            // or replace the native color/shader implementation.
            type.getMethod("setScreenShot",Bitmap.class,int[].class,boolean.class,boolean.class,Long.class)
                    .invoke(controller,bitmap,screen,false,false,key);
            return true;
        }catch(Exception failure){pending.remove(controller);throw failure;}
    }
    private NativeClockMaterialBootstrap(){}
}
