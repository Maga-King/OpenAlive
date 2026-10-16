package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import java.io.InputStream;
import java.util.function.Consumer;

/** Cover art for the wallpaper sources, decoded off the main thread. */
final class Covers {
    static final String XIAOMI="xiaomi/earth.banner",DYNAMIC="cosmic/v4.png",ALIVE="examples/sakura-color.jpg";

    static Bitmap asset(Context c,String name,int maxWidth){
        try(InputStream probe=c.getAssets().open(name)){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeStream(probe,null,o);
            o.inJustDecodeBounds=false;o.inSampleSize=1;while(o.outWidth/(o.inSampleSize*2)>=maxWidth)o.inSampleSize*=2;
            try(InputStream in=c.getAssets().open(name)){return BitmapFactory.decodeStream(in,null,o);}
        }catch(Exception error){android.util.Log.w("AliveClean","Cover "+name+" unavailable",error);return null;}
    }
    /** Vivo artwork lives inside its engine runtime, which may need preparing first. */
    static void vivo(Context c,String name,Consumer<Bitmap> done){
        new Thread(()->{
            Bitmap result=null;
            try{result=VivoImages.asset(VivoEngineRuntime.prepare(c).get(),name);}
            catch(Exception error){android.util.Log.w("AliveClean","Vivo cover unavailable",error);}
            done.accept(result);
        },"VivoCover").start();
    }
}
