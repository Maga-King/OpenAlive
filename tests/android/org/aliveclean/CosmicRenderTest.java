package org.aliveclean;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import java.nio.*;
import java.io.*;

/** Compile and render all imported materials on the device GPU, not a mock GL. */
final class CosmicRenderTest {
    static int checks;
    static int actualSamples;
    static void run(Context context)throws Exception{
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGL14.eglInitialize(display,new int[2],0,new int[2],0);
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        int[] attributes={EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_NONE};
        configs[0]=CosmicEgl.choose(display,EGL14.EGL_PBUFFER_BIT);
        int[] samples=new int[1];EGL14.eglGetConfigAttrib(display,configs[0],EGL14.EGL_SAMPLES,samples,0);
        actualSamples=samples[0];
        EGLContext egl=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        int width=360,height=792;
        EGLSurface surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,width,EGL14.EGL_HEIGHT,height,EGL14.EGL_NONE},0);
        require(EGL14.eglMakeCurrent(display,surface,surface,egl),"Pbuffer bind failed");
        try{
            CosmicMotion handoff=new CosmicMotion(context.getAssets(),9,false,1);
            handoff.change(2,true);handoff.advance(1_000_000_000L,0);handoff.advance(1_300_000_000L,0);
            require(handoff.values[2]==1.181f,"Lock-to-home scale started before official delay");
            handoff.advance(1_800_000_000L,0);require(Math.abs(handoff.values[2]-1.55f)<.0001f,"Lock-to-home scale did not finish");
            handoff.change(0,true);handoff.advance(2_000_000_000L,0);
            for(float value:handoff.values)require(Float.isFinite(value),"Immediate background produced nonfinite values");
            handoff.advance(4_000_000_000L,0);handoff.follow(-.2f,.1f);
            require(handoff.values[0]==-.2f&&handoff.values[1]==.1f,"Settled Cosmic ignored live clock coordinates");
            CosmicMotion reference=new CosmicMotion(context.getAssets(),9,false,1);
            CosmicMotion held=new CosmicMotion(context.getAssets(),9,false,2,true);
            for(int j=0;j<CosmicMotion.SIZE;j++)require(held.values[j]==reference.values[j],"Restored home ignored keep-lock setting");
            held.change(1,true);require(!held.active(),"Held home-to-lock unexpectedly restarted transition");
            held.change(0,true);held.advance(5_000_000_000L,0);held.advance(7_000_000_000L,0);
            require(held.values[2]==.22f,"Keep-lock setting changed AOD geometry");
            held.change(2,true);held.advance(8_000_000_000L,0);held.advance(10_000_000_000L,0);
            for(int j=0;j<CosmicMotion.SIZE;j++)require(held.values[j]==reference.values[j],"Direct AOD-to-home did not use lock parameters");
            held.change(1,true);held.change(2,true);require(!held.active(),"Locked appearance replayed on lock-to-home handoff");
            org.json.JSONArray entries=new org.json.JSONArray(AssetGl.text(context.getAssets(),"cosmic/catalog.json"));
            File directory=new File(context.getExternalFilesDir(null),"cosmic-render");directory.mkdirs();
            ByteBuffer pixels=ByteBuffer.allocateDirect(width*height*4);
            java.util.Map<String,Long> bubblePalettes=new java.util.HashMap<>();
            for(int i=0;i<entries.length();i++)for(boolean dark:new boolean[]{false,true}){
                int variant=entries.getJSONObject(i).getInt("id");
                try(WallpaperRenderer renderer=WallpaperRenderer.create(context.getAssets(),variant,dark,width,height,0,false)){
                    long time=1_000_000_000L;
                    for(int scene:new int[]{0,1,2,0,2,1}){
                        renderer.motion().change(scene,true);renderer.motion().advance(time,1);
                        renderer.motion().advance(time+2_000_000_000L,1);time+=3_000_000_000L;
                        renderer.render();require(GLES30.glGetError()==GLES30.GL_NO_ERROR,"GL error v"+variant+" scene"+scene);
                        pixels.clear();GLES30.glReadPixels(0,0,width,height,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
                        int lit=0;for(int p=0;p<width*height;p++){int at=p*4;if((pixels.get(at)&255)>20||(pixels.get(at+1)&255)>20||(pixels.get(at+2)&255)>20)lit++;}
                        require(lit>300,"Empty Cosmic render v"+variant+" scene"+scene);
                        if(variant>=201&&variant<=205&&scene==2){
                            java.util.zip.CRC32 checksum=new java.util.zip.CRC32();
                            for(int p=0;p<pixels.capacity();p++)checksum.update(pixels.get(p));
                            for(int prior=201;prior<variant;prior++){
                                Long other=bubblePalettes.get(dark+":"+prior);
                                require(other!=null&&other.longValue()!=checksum.getValue(),"Bubble palettes duplicated in theme="+dark+": "+prior+" and "+variant);
                            }
                            bubblePalettes.put(dark+":"+variant,checksum.getValue());
                        }
                        if((variant==9||variant>=101)&&(!dark||variant>=201)){
                            int[] colors=new int[width*height];
                            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                                int at=((height-1-y)*width+x)*4;
                                colors[y*width+x]=0xff000000|((pixels.get(at)&255)<<16)|((pixels.get(at+1)&255)<<8)|(pixels.get(at+2)&255);
                            }
                            Bitmap bitmap=Bitmap.createBitmap(colors,width,height,Bitmap.Config.ARGB_8888);
                            try(OutputStream out=new FileOutputStream(new File(directory,"variant-"+variant+"-"+scene+(dark?"-dark":"")+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
                        }
                    }
                }
            }
        }finally{
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,egl);EGL14.eglTerminate(display);EGL14.eglReleaseThread();
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
