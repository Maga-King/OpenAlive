package org.aliveclean;

import android.app.Instrumentation;
import android.os.Bundle;

/** Runs offscreen; does not change wallpaper, display state, or SystemUI. */
public final class WallpaperMotionInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            WallpaperMotionLifecycleTest.run(getTargetContext());
            result.putString("stream","WALLPAPER_MOTION_OK checks="+WallpaperMotionLifecycleTest.checks);
            finish(-1,result);
        }catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(0,result);}
    }
}
