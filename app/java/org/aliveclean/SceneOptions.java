package org.aliveclean;

import android.content.SharedPreferences;

/** A complete snapshot; preview edits never change the applied snapshot. */
final class SceneOptions {
    static final String APPLIED="scene", DRAFT="scene.preview";
    final int aod,lock,home,color,cosmic;
    final String photo,homePhoto,framePhoto;
    final boolean followLock,framePair,cosmicKeepLock;
    final boolean cosmicContinuousAod,cosmicContinuousHome,sailContinuousAod;
    final float frameX,frameY,frameSize,frameAngle;

    SceneOptions(SharedPreferences prefs) {
        int a=prefs.getInt("aod",0),l=prefs.getInt("lock",2),h=prefs.getInt("home",6);
        int c=prefs.getInt("cosmic",0);cosmic=c==1||c==3||c==4||(c>=6&&c<=15)||(c>=101&&c<=105)||(c>=201&&c<=205)?c:0;
        cosmicKeepLock=prefs.getBoolean("cosmic_keep_lock",false);
        cosmicContinuousAod=prefs.getBoolean("cosmic_continuous_aod",true);
        cosmicContinuousHome=prefs.getBoolean("cosmic_continuous_home",true);
        sailContinuousAod=prefs.getBoolean("sail_continuous_aod",true);
        aod=cosmic!=0?0:a==-1||a==0||PhotoStyle.supported(a)||a==101?a:0;
        lock=l>=0&&l<=5?l:2;
        home=h>=6&&h<=9?h:6;
        color=prefs.getInt("color",0xff264552);
        String name=prefs.getString("photo","photo");
        photo=name!=null&&name.matches("[A-Za-z0-9._-]+")&&!name.equals(".")&&!name.equals("..")?name:"photo";
        name=prefs.getString("home_photo",photo);
        homePhoto=name!=null&&name.matches("[A-Za-z0-9._-]+")&&!name.equals(".")&&!name.equals("..")?name:photo;
        followLock=prefs.getBoolean("home_follow_lock",true);
        framePair=prefs.getBoolean("frame_pair",true);
        name=prefs.getString("frame_photo",photo);
        framePhoto=name!=null&&name.matches("[A-Za-z0-9._-]+")&&!name.equals(".")&&!name.equals("..")?name:photo;
        frameX=finite(prefs.getFloat("frame_x",.5f),0,1,.5f);
        frameY=finite(prefs.getFloat("frame_y",.5f),0,1,.5f);
        frameSize=finite(prefs.getFloat("frame_size",1),.05f,1,1);
        frameAngle=finite(prefs.getFloat("frame_angle",0),-45,45,0);
    }

    private static float finite(float v,float min,float max,float fallback){return Float.isNaN(v)||Float.isInfinite(v)?fallback:Math.max(min,Math.min(max,v));}
    boolean pairedFrame(){return aod==1&&framePair;}

    boolean save(SharedPreferences target) {
        return target.edit().putInt("aod",aod).putInt("cosmic",cosmic).putInt("lock",lock)
            .putBoolean("cosmic_keep_lock",cosmicKeepLock)
            .putBoolean("cosmic_continuous_aod",cosmicContinuousAod)
            .putBoolean("cosmic_continuous_home",cosmicContinuousHome)
            .putBoolean("sail_continuous_aod",sailContinuousAod)
            .putInt("home",home).putInt("color",color).putString("photo",photo)
            .putString("home_photo",homePhoto).putBoolean("home_follow_lock",followLock)
            .putBoolean("frame_pair",framePair).putString("frame_photo",framePhoto)
            .putFloat("frame_x",frameX).putFloat("frame_y",frameY).putFloat("frame_size",frameSize).putFloat("frame_angle",frameAngle).commit();
    }
}
