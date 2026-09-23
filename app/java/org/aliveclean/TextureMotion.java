package org.aliveclean;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Texture lifetime is one wake session, independent of the wallpaper's visibility. */
final class TextureMotion {
    private final SceneMotion lock,home;
    private final boolean shared;
    private int mode;
    private final ByteBuffer packet=ByteBuffer.allocateDirect(20*4).order(ByteOrder.nativeOrder());

    TextureMotion(int width,int height,int initialMode,int lockStyle,int homeStyle){
        shared=lockStyle==homeStyle-6;mode=initialMode;
        lock=track(width,height,initialMode==1||shared&&initialMode==2,lockStyle);
        home=shared?lock:track(width,height,initialMode==2,homeStyle-6);
    }
    private static SceneMotion track(int w,int h,boolean awake,int style){
        return style>=1&&style<=3?new SceneMotion(w,h,awake?1:0,-1,style,6):null;
    }
    void change(int mode,boolean animate){
        if(shared&&this.mode==1&&mode==2){if(lock!=null)lock.preserveMode(2);this.mode=mode;return;}
        if(mode==0){change(lock,0,animate);if(home!=lock)change(home,0,animate);}
        else change(mode==1?lock:home,1,animate);
        this.mode=mode;
    }
    private static void change(SceneMotion track,int mode,boolean animate){
        if(track==null)return;
        track.change(mode);
        if(!animate)track.finish();
    }
    boolean active(){return lock!=null&&lock.transitionActive()||home!=null&&home!=lock&&home.transitionActive();}
    private void write(SceneMotion track){
        for(int i=0;i<8;i++)packet.putFloat(track==null?0:track.values[SceneMotion.GLASS+i]);
        for(int i=0;i<2;i++)packet.putFloat(track==null?0:track.values[SceneMotion.MASK+i]);
    }
    ByteBuffer packet(){packet.clear();write(lock);write(home);packet.flip();return packet;}
    void close(){if(lock!=null)lock.close();if(home!=null&&home!=lock)home.close();}
}
