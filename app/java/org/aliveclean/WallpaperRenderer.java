package org.aliveclean;

import android.content.res.AssetManager;
import java.io.IOException;

interface WallpaperRenderer extends AutoCloseable {
    WallpaperMotion motion();
    void render();
    void close();
    default void visible(boolean value){}
    default void continuous(boolean value){}
    default void interaction(){}
    static WallpaperRenderer create(AssetManager assets,int variant,boolean dark,int width,int height,int scene,boolean keepLock)throws IOException {
        return create(assets,variant,dark,width,height,scene,keepLock,()->{});
    }
    static WallpaperRenderer create(AssetManager assets,int variant,boolean dark,int width,int height,int scene,boolean keepLock,Runnable invalidate)throws IOException {
        if(variant>=401&&variant<=403)return new FlymeVideoRenderer(assets,variant,dark,width,height,scene,invalidate);
        if(variant>=501&&variant<=503)return new StereoRenderer(assets,variant,dark,width,height,scene,keepLock);
        if(variant>=201&&variant<=205)return new BubbleRenderer(assets,variant,dark,width,height,scene,keepLock);
        if(variant>=301&&variant<=303)return new GradientRenderer(assets,variant,dark,width,height,scene);
        return new CosmicRenderer(assets,variant,dark,width,height,scene,keepLock);
    }
}
