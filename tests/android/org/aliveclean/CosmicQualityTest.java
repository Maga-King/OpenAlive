package org.aliveclean;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.Arrays;
import java.util.Locale;

/** Same stopped pose with and without MSAA, plus full-resolution GPU completion cost. */
final class CosmicQualityTest {
    static String run(Context context)throws Exception{
        StringBuilder report=new StringBuilder();
        for(boolean aa:new boolean[]{false,true}){
            EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            EGL14.eglInitialize(display,new int[2],0,new int[2],0);
            EGLConfig config;
            if(aa)config=CosmicEgl.choose(display,EGL14.EGL_PBUFFER_BIT);
            else{
                EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
                int[] attrs={EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_SAMPLES,0,EGL14.EGL_SAMPLE_BUFFERS,0,EGL14.EGL_NONE};
                if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]==0)throw new AssertionError("Missing baseline EGL config");
                config=configs[0];
            }
            EGLContext egl=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
            int width=1440,height=3168;
            EGLSurface surface=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,width,EGL14.EGL_HEIGHT,height,EGL14.EGL_NONE},0);
            try{
                if(!EGL14.eglMakeCurrent(display,surface,surface,egl))throw new AssertionError("Quality surface unavailable");
                int[] actual=new int[1];GLES30.glGetIntegerv(GLES30.GL_SAMPLES,actual,0);
                if(!aa&&actual[0]!=0)throw new AssertionError("Baseline is not single-sampled");
                report.append("\nquality_samples=").append(actual[0]);
                try(CosmicRenderer renderer=new CosmicRenderer(context.getAssets(),9,false,width,height,0)){
                    renderer.motion.time=3.5f;renderer.motion.angle=65;
                    for(int scene:new int[]{0,1}){
                        renderer.motion.change(scene,false);
                        long[] costs=new long[12];
                        for(int n=-3;n<costs.length;n++){
                            long start=System.nanoTime();renderer.render();GLES30.glFinish();
                            if(n>=0)costs[n]=System.nanoTime()-start;
                        }
                        if(GLES30.glGetError()!=GLES30.GL_NO_ERROR)throw new AssertionError("Quality GL failure");
                        Arrays.sort(costs);
                        report.append(String.format(Locale.ROOT," scene%d_ms(p50/max)=%.2f/%.2f",scene,costs[6]/1e6,costs[11]/1e6));
                        File metricsFolder=new File(context.getExternalFilesDir(null),"cosmic-render");metricsFolder.mkdirs();
                        try(Writer out=new FileWriter(new File(metricsFolder,"quality.txt"))){out.write(report.toString());}
                        if(scene==0){
                            ByteBuffer pixels=ByteBuffer.allocateDirect(width*height*4);
                            GLES30.glReadPixels(0,0,width,height,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
                            int[] colors=new int[width*height];
                            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                                int at=((height-y-1)*width+x)*4;
                                colors[y*width+x]=0xff000000|((pixels.get(at)&255)<<16)|((pixels.get(at+1)&255)<<8)|(pixels.get(at+2)&255);
                            }
                            File folder=new File(context.getExternalFilesDir(null),"cosmic-render");folder.mkdirs();
                            Bitmap image=Bitmap.createBitmap(colors,width,height,Bitmap.Config.ARGB_8888);
                            try(OutputStream out=new FileOutputStream(new File(folder,aa?"stopped-aa.png":"stopped-before.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
                        }
                    }
                }
            }finally{
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,egl);EGL14.eglTerminate(display);EGL14.eglReleaseThread();
            }
        }
        return report.toString();
    }
}
