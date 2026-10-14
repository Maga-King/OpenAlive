package org.aliveclean;

import android.content.Context;
import java.io.IOException;

/** Real imported motion classes and Android interpolators, with deterministic visibility gaps. */
final class WallpaperMotionLifecycleTest {
    static int checks;
    static void run(Context context)throws Exception{
        org.json.JSONArray catalog=new org.json.JSONArray(AssetGl.text(context.getAssets(),"cosmic/catalog.json"));
        for(int i=0;i<catalog.length();i++)for(boolean dark:new boolean[]{false,true})for(boolean keepLock:new boolean[]{false,true})
            checkVisibilityTransitions(context,catalog.getJSONObject(i).getInt("id"),keepLock,dark);
    }
    private static WallpaperMotion motion(Context context,int variant,int scene,boolean keepLock,boolean dark)throws IOException{
        return variant>=201&&variant<=205?new BubbleMotion(context.getAssets(),variant,dark,scene,keepLock):
            new CosmicMotion(context.getAssets(),variant,dark,scene,keepLock);
    }
    private static float[] values(WallpaperMotion motion){
        return motion instanceof BubbleMotion?((BubbleMotion)motion).values:((CosmicMotion)motion).values;
    }
    private static void same(WallpaperMotion actual,WallpaperMotion expected,String message){
        float[] a=values(actual),e=values(expected);
        for(int i=0;i<a.length;i++)require(Math.abs(a[i]-e[i])<.0001f,message+" slot="+i);
    }
    private static void checkVisibilityTransitions(Context context,int variant,boolean keepLock,boolean dark)throws IOException{
        for(int source=0;source<3;source++)for(int target=0;target<3;target++){
            if(source==target)continue;
            WallpaperMotion resumed=motion(context,variant,source,keepLock,dark),reference=motion(context,variant,source,keepLock,dark);
            resumed.change(target,true);reference.change(target,true);
            resumed.advance(1_000_000_000L,0);reference.advance(1_000_000_000L,0);
            resumed.advance(1_200_000_000L,0);reference.advance(1_200_000_000L,0);
            resumed.pause();resumed.advance(11_200_000_000L,0);
            same(resumed,reference,"Hidden interval skipped v"+variant+" "+source+"->"+target);
            require(resumed.active()==reference.active(),"Hidden transition ended prematurely");
            resumed.advance(11_450_000_000L,0);reference.advance(1_450_000_000L,0);
            same(resumed,reference,"Resume changed official curves v"+variant);
            // Fingerprint wake/re-lock can reverse the track before the wallpaper is visible.
            resumed.pause();resumed.change(source,true);reference.change(source,true);
            resumed.advance(21_450_000_000L,0);reference.advance(1_450_000_000L,0);
            same(resumed,reference,"Hidden reversal jumped v"+variant);
            resumed.advance(21_750_000_000L,0);reference.advance(1_750_000_000L,0);
            same(resumed,reference,"Reversed curve changed v"+variant);
            resumed.advance(25_450_000_000L,0);reference.advance(5_450_000_000L,0);
            same(resumed,reference,"Reversal endpoint changed v"+variant);
            require(!resumed.active(),"Reversed animation never settled");
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
