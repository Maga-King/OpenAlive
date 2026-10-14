package org.aliveclean;

import android.graphics.*;
import android.opengl.GLES20;
import android.util.Size;
import java.lang.reflect.*;
import java.util.*;

/** Implements Vivo's existing host texture protocol; the engine owns its rendering. */
final class VivoTextureLayer implements InvocationHandler,AutoCloseable {
    final Object proxy;
    private final ArrayList<Integer> images=new ArrayList<>();
    private final ArrayList<Size> sizes=new ArrayList<>();
    private final ArrayList<Rect> crops=new ArrayList<>();
    private final ArrayList<Integer> owned=new ArrayList<>();
    private final HashMap<Integer,Object> values=new HashMap<>();
    private final Size screen;
    private final boolean flipY;
    private final int subject,doodle;
    private final Size subjectSize,doodleSize;
    private VivoCalls videoApi;
    private Object videoParams;
    VivoTextureLayer(VivoCalls api,Object render,int width,int height,List<Bitmap> photos,
            Bitmap mask,Bitmap painted,PhotoViewport viewport,boolean background,boolean flipY,boolean displayP3)throws Exception{
        screen=new Size(width,height);this.flipY=flipY;
        try{
            for(Bitmap photo:photos){
                Bitmap upload=VivoColorSpace.convert(photo,displayP3);
                try{images.add(texture(api,render,upload));}finally{if(upload!=photo)upload.recycle();}
                sizes.add(new Size(photo.getWidth(),photo.getHeight()));
                PhotoViewport crop=new PhotoViewport();crop.dimensions(photo.getWidth(),photo.getHeight(),width,height);
                if(viewport!=null)crop.restore(viewport.centerX(),viewport.centerY(),viewport.zoom());
                crops.add(new Rect(Math.round(crop.left()*photo.getWidth()),Math.round(crop.top()*photo.getHeight()),
                    Math.round((crop.left()+crop.width())*photo.getWidth()),Math.round((crop.top()+crop.height())*photo.getHeight())));
            }
            int rawMask=mask==null?0:texture(api,render,mask);
            if(background&&mask!=null){
                Bitmap inverse=mask.copy(Bitmap.Config.ARGB_8888,true);
                try{
                    int w=inverse.getWidth(),h=inverse.getHeight();int[] pixels=new int[w*h];inverse.getPixels(pixels,0,w,0,0,w,h);
                    for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|(~pixels[i]&0xffffff);
                    inverse.setPixels(pixels,0,w,0,0,w,h);subject=texture(api,render,inverse);
                }finally{inverse.recycle();}
            }else subject=rawMask;
            doodle=painted==null?rawMask:texture(api,render,painted);
            subjectSize=mask==null?screen:new Size(mask.getWidth(),mask.getHeight());
            doodleSize=painted==null?subjectSize:new Size(painted.getWidth(),painted.getHeight());
            values.put(10,0);values.put(11,1001);values.put(12,0);
            Class<?> contract=api.type("com.android.systemui.plugins.IVAGLayer");
            proxy=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},this);
        }catch(Exception e){close();throw e;}
    }
    private int texture(VivoCalls api,Object render,Bitmap image)throws Exception{
        // Vivo receives FBO textures from its photo editor (bottom-left origin),
        // while Android bitmap uploads start at the top. Match that host contract.
        Matrix flip=new Matrix();flip.setScale(1,-1);
        Bitmap upload=flipY?Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),flip,true):image;
        Object texture;try{texture=api.call(render,"loadTexture",new Class<?>[]{Bitmap.class},upload);}finally{if(upload!=image)upload.recycle();}
        int id=(Integer)api.get(texture,"a");if(id<=0)throw new IllegalStateException("Vivo texture upload failed");
        owned.add(id);return id;
    }
    void crop(PhotoViewport viewport){
        crops.clear();for(Size size:sizes){PhotoViewport p=new PhotoViewport();p.dimensions(size.getWidth(),size.getHeight(),screen.getWidth(),screen.getHeight());
            p.restore(viewport.centerX(),viewport.centerY(),viewport.zoom());
            crops.add(new Rect(Math.round(p.left()*size.getWidth()),Math.round(p.top()*size.getHeight()),Math.round((p.left()+p.width())*size.getWidth()),Math.round((p.top()+p.height())*size.getHeight())));}
    }
    void video(VivoCalls api,java.io.File directory,VivoOptions options)throws Exception{
        videoApi=api;
        videoParams=api.make(VivoEngineScene.BASE+"plugin.raster.RasterVideoPreRenderParamBean",new Class<?>[0]);
        api.set(videoParams,"videoPath",new java.io.File(directory,options.video).getPath());
        api.set(videoParams,"videoFirstFrame",new java.io.File(directory,options.videoFirstFrame).getPath());
        api.set(videoParams,"videoFrameNum",options.videoFrames);api.set(videoParams,"videoFrameRate",options.videoRate);
        api.set(videoParams,"videoTime",options.videoDuration);api.set(videoParams,"videoResFrom","gallery");
    }
    private String renderParams()throws Exception{
        if(videoParams==null)return "{}";
        videoApi.set(videoParams,"texIds",images.get(0));videoApi.set(videoParams,"texImageSize",sizes.get(0));
        videoApi.set(videoParams,"inImageRect",crops.get(0));videoApi.set(videoParams,"outImageRect",crops.get(0));
        videoApi.set(videoParams,"inScreenSize",screen);videoApi.set(videoParams,"outScreenSize",screen);
        Object codec=videoApi.type("e3.l").getField("a").get(null);
        return (String)videoApi.call(codec,"e",new Class<?>[]{Object.class},videoParams);
    }
    void bindLiveFlashMask(VivoCalls api,Object render,Object controller,int area)throws Exception{
        // Live FlashCard consumes its mask as image B; the editor protocol instead
        // stores it on image A's auxiliary texture. Keep the original live shaders.
        int id=area==1?doodle:subject;
        if(id==0)return;
        List<?> entries=(List<?>)api.get(api.get(controller,"b"),"c");
        Object texture=api.call(api.get(entries.get(1),"a"),"getTexture");
        // Retain the original g texture wrapper: reLoadSource updates its auxiliary
        // mask in place when a crop changes. Replacing it with i breaks that contract.
        api.set(texture,"a",id);
    }
    @Override public Object invoke(Object ignored,Method method,Object[] args)throws Throwable{
        switch(method.getName()){
            case "toString":return "OpenAlive texture host";
            case "hashCode":return System.identityHashCode(this);
            case "equals":return ignored==args[0];
            case "getInTexId":return images.get(0);
            case "getInTexIds":return images;
            case "getInTexSize":case "getOriginImageSize":return sizes.get(0);
            case "getInTexSizes":return sizes;
            case "getFromRect":return crops.get(0);
            case "getInScreenTexFromRects":case "getOutScreenTexFromRects":return crops;
            case "getInScreenSize":case "getOutScreenSize":case "getOutFboSize":return screen;
            case "getMaskTexId":return subject;
            case "getMaskTexIds":return Collections.nCopies(images.size(),subject);
            case "getMaskTexSize":return subjectSize;
            case "getMaskTexFromRect":return new Rect(0,0,subjectSize.getWidth(),subjectSize.getHeight());
            case "getDoodleMaskTexId":return doodle;
            case "getDoodleMaskTexSize":return doodleSize;
            case "getDoodleMaskTexFromRect":return new Rect(0,0,doodleSize.getWidth(),doodleSize.getHeight());
            case "getExpandStatus":return Collections.nCopies(images.size(),0);
            case "getExpandTexs":return Collections.nCopies(images.size(),0);
            case "getExpandTexSizes":return sizes;
            case "getInScreenExpandTexFromRects":case "getOutScreenExpandTexFromRects":return crops;
            case "getExpandTexSize":return sizes.get(0);
            case "getExpandTexFromRect":return crops.get(0);
            case "getExpandTexId":case "getOutFboId":case "getViewType":return 0;
            case "getRenderParams":return renderParams();
            case "getVertMat":float[] matrix=new float[16];android.opengl.Matrix.setIdentityM(matrix,0);return matrix;
            case "getValue":return values.get((Integer)args[0]);
            case "setValue":values.put((Integer)args[0],args[1]);return null;
            case "isKeySupport":return values.containsKey((Integer)args[0]);
            default:throw new UnsupportedOperationException("Unsupported Vivo host operation: "+method.getName());
        }
    }
    @Override public void close(){int[] ids=new int[owned.size()];for(int i=0;i<ids.length;i++)ids[i]=owned.get(i);GLES20.glDeleteTextures(ids.length,ids,0);owned.clear();}
}
