package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * One frame of the applied wallpaper's lock composition. ColorOS samples the
 * system's static wallpaper file (WallpaperManager.getWallpaperFile) for clock
 * coloring even while a live wallpaper is shown; the file left from an older
 * static wallpaper makes it pick the wrong (dark) glass blend mode.
 */
final class StaticSnapshot {
    private static final int WIDTH=360,HEIGHT=792;

    /** Called off the main thread. Returns a world-readable PNG, or null. */
    static File capture(Context c){
        try{
            Bitmap frame=xiaomi(c);
            if(frame==null)frame=render(c);
            if(frame==null)return null;
            File out=new File(c.getCacheDir(),"static-sync.png");
            try(FileOutputStream stream=new FileOutputStream(out)){frame.compress(Bitmap.CompressFormat.PNG,100,stream);}
            out.setReadable(true,false);c.getCacheDir().setExecutable(true,false);
            return out;
        }catch(Exception|LinkageError error){android.util.Log.w("AliveClean","Static wallpaper snapshot unavailable",error);return null;}
    }
    private static Bitmap xiaomi(Context c)throws Exception {
        if(XiaomiState.selected(c,1)==null)return null;
        XiaomiHomePreview.Card[] cards=XiaomiHomePreview.load(c);
        return cards[1]==null?null:cards[1].image;
    }
    /** Renders the lock scene off screen with the same renderer as the wallpaper. */
    private static Bitmap render(Context c)throws Exception {
        ImageReader reader=ImageReader.newInstance(WIDTH,HEIGHT,PixelFormat.RGBA_8888,2);
        RenderLoop loop=new RenderLoop(c);
        try{
            loop.mode(1,false);loop.attach(reader.getSurface(),WIDTH,HEIGHT);loop.visible(true);
            Image image=null;
            // Give scene loading and the first lock frames time to settle.
            for(int i=0;i<40;i++){
                SystemClock.sleep(100);
                Image next=reader.acquireLatestImage();
                if(next!=null){if(image!=null)image.close();image=next;if(i>=15)break;}
            }
            if(image==null)return null;
            try{return toBitmap(image);}finally{image.close();}
        }finally{loop.visible(false);SystemClock.sleep(150);loop.close();SystemClock.sleep(150);reader.close();}
    }
    private static Bitmap toBitmap(Image image){
        Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();
        int pixel=plane.getPixelStride(),row=plane.getRowStride(),padding=row-pixel*image.getWidth();
        Bitmap wide=Bitmap.createBitmap(image.getWidth()+padding/pixel,image.getHeight(),Bitmap.Config.ARGB_8888);
        wide.copyPixelsFromBuffer(buffer);
        return Bitmap.createBitmap(wide,0,0,image.getWidth(),image.getHeight());
    }
}
