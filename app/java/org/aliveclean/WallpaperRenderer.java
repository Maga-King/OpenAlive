package org.aliveclean;

import android.content.res.AssetManager;
import java.io.IOException;

interface WallpaperRenderer extends AutoCloseable {
    WallpaperMotion motion();
    void render();
    void close();
    static WallpaperRenderer create(AssetManager assets,int variant,boolean dark,int width,int height,int scene,boolean keepLock)throws IOException {
        if(variant>=201&&variant<=205)return new BubbleRenderer(assets,variant,dark,width,height,scene,keepLock);
        return new CosmicRenderer(assets,variant,dark,width,height,scene,keepLock);
    }
}
