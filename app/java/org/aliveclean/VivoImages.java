package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.util.*;

final class VivoImages implements AutoCloseable {
    final ArrayList<Bitmap> photos=new ArrayList<>();
    Bitmap subject,paint;
    static VivoImages load(Context host,VivoEngineRuntime runtime,VivoOptions options)throws IOException{
        VivoImages images=new VivoImages();
        try{
            if(options.photos.isEmpty()){
                if(options.family==VivoOptions.RASTER)for(int i=0;i<3;i++)images.photos.add(asset(runtime,"vivo/default/raster-"+i+".jpg"));
                else {String category=options.family==VivoOptions.LIQUID?"liquid":"flash";
                    images.photos.add(asset(runtime,"vivo/default/"+category+".jpg"));images.subject=asset(runtime,"vivo/default/"+category+"-mask.png");}
                if(options.family==VivoOptions.FLASH&&options.paint.isEmpty()){
                    // This particular factory template stores its 1080x2400 photo at
                    // (0,1200) on a 1080x3600 drawing canvas. Do not stretch the canvas
                    // onto the photo, or substitute the unrelated subject mask.
                    Bitmap canvas=asset(runtime,"vivo/default/flash-doodle.png");
                    try{
                        if(canvas.getWidth()!=1080||canvas.getHeight()!=3600)throw new IOException("Unexpected Vivo factory drawing canvas");
                        images.paint=Bitmap.createBitmap(canvas,0,1200,1080,2400);
                    }finally{canvas.recycle();}
                }
            }else for(String name:options.photos)images.photos.add(file(host,name));
            if(options.family==VivoOptions.RASTER)for(int i=0;i<images.photos.size();i++){
                Bitmap source=images.photos.get(i),rotated=rotate(source,options.slots[i].rotation);
                if(rotated!=source){images.photos.set(i,rotated);source.recycle();}
            }
            if(!options.subject.isEmpty()){if(images.subject!=null)images.subject.recycle();images.subject=file(host,options.subject);}
            if(!options.paint.isEmpty())images.paint=file(host,options.paint);
            return images;
        }catch(IOException|RuntimeException e){images.close();throw e;}
    }
    static Bitmap asset(VivoEngineRuntime runtime,String name)throws IOException{
        try(InputStream in=runtime.getAssets().open(name)){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IOException("Invalid Vivo image: "+name);return b;}
    }
    static Bitmap rotate(Bitmap source,int turns){
        if(Math.floorMod(turns,4)==0)return source;
        Matrix matrix=new Matrix();matrix.postRotate(90*Math.floorMod(turns,4));
        return Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),matrix,true);
    }
    static Bitmap slot(Context host,VivoEngineRuntime runtime,VivoOptions options,int index)throws IOException{
        return index<options.photos.size()?file(host,options.photos.get(index)):asset(runtime,"vivo/default/raster-"+index+".jpg");
    }
    private static Bitmap file(Context host,String name)throws IOException{
        File f=new File(host.getFilesDir(),name);
        if(!name.matches("vivo-[A-Za-z0-9._-]+"))throw new IOException("Invalid image path");
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(f),(decoder,info,s)->{
            float scale=Math.min(1,2560f/Math.max(info.getSize().getWidth(),info.getSize().getHeight()));
            decoder.setTargetSize(Math.max(1,Math.round(info.getSize().getWidth()*scale)),Math.max(1,Math.round(info.getSize().getHeight()*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }
    @Override public void close(){for(Bitmap b:photos)b.recycle();photos.clear();if(subject!=null)subject.recycle();if(paint!=null)paint.recycle();subject=paint=null;}
}
