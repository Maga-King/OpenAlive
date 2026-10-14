package org.aliveclean;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.opengl.*;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import org.json.*;

/** Exercise actual GPU output, not just shader compilation or mocked GL calls. */
public final class GradientSurfaceInstrumentation extends Instrumentation {
    private int W=360,H=780;
    private int checks;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void require(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private void near(double a,double b,double tolerance,String label){require(Math.abs(a-b)<=tolerance,label+": "+a+" vs "+b);}
    public void onStart(){
        Bundle result=new Bundle();StringBuilder log=new StringBuilder();boolean ok=false;
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;
        try{
            int[] version=new int[2],count=new int[1];EGLConfig[] configs=new EGLConfig[1];
            require(EGL14.eglInitialize(display,version,0,version,1),"EGL initialize");
            int[] attrs={EGL14.EGL_RENDERABLE_TYPE,64,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
            require(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)&&count[0]>0,"EGL config");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,780,EGL14.EGL_HEIGHT,780,EGL14.EGL_NONE},0);
            require(EGL14.eglMakeCurrent(display,surface,surface,context),"EGL current");
            log.append("GPU: ").append(GLES30.glGetString(GLES30.GL_RENDERER)).append('\n');
            for(int orientation=0;orientation<2;orientation++){
            W=orientation==0?360:780;H=orientation==0?780:360;
            ByteBuffer pixels=ByteBuffer.allocateDirect(W*H*4);
            for(int id=301;id<=303;id++)for(boolean dark:new boolean[]{false,true}){
                GradientRenderer renderer=new GradientRenderer(getTargetContext().getAssets(),id,dark,W,H,1);
                try{
                    GradientMotion motion=renderer.motion;long base=9_876_543_210_000L;
                    motion.advance(base,1);near(motion.time(),0,0,"First frame absolute epoch");
                    float[][] first=new float[6][];
                    for(int c=0;c<6;c++)first[c]=motion.splines[c].coefficients.clone();
                    for(int frame=0;frame<=1801;frame++){
                        motion.advance(base+frame*16_666_667L,1);
                        if(frame%60==0)checkSplines(motion);
                        if(frame==1800)for(int c=0;c<6;c++)for(int j=0;j<16;j++)near(motion.splines[c].coefficients[j],first[c][j],.00001,"Thirty-second shape return");
                        if(frame!=0&&frame!=480&&frame!=1500&&frame!=1800&&frame!=1801)continue;
                        // Deliberately leave hostile GL state, as switching renderers can do.
                        GLES30.glEnable(GLES30.GL_BLEND);GLES30.glEnable(GLES30.GL_CULL_FACE);
                        GLES30.glEnable(GLES30.GL_SCISSOR_TEST);GLES30.glScissor(0,0,1,1);
                        renderer.render();pixels.clear();GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
                        require(GLES30.glGetError()==GLES30.GL_NO_ERROR,"GL output");
                        verifyPixels(pixels,motion,dark);
                        save(pixels,"gradient-"+id+"-"+(dark?"dark":"light")+"-"+frame+(orientation==0?"":"-landscape")+".png");
                    }
                    near(motion.time(),1801/60d,.0001,"Elapsed visible frames");
                    float before=motion.time();float[] shape=motion.splines[2].coefficients.clone();
                    motion.pause();motion.change(0,true);motion.advance(base+600_000_000_000L,1);
                    near(motion.time(),before,0,"Pause must exclude hidden time");
                    for(int i=0;i<shape.length;i++)near(shape[i],motion.splines[2].coefficients[i],0,"Resume preserves curve");
                    motion.advance(base+600_016_666_667L,1);require(motion.time()>before,"Resume must animate");
                    before=motion.time();shape=motion.splines[2].coefficients.clone();
                    motion.advance(base+600_033_333_334L,0);require(motion.time()>before,"Color clock remains independent of shape speed");
                    for(int i=0;i<shape.length;i++)near(shape[i],motion.splines[2].coefficients[i],0,"Finite shape freeze");
                    require(!motion.active(),"Finite motion must permit RenderLoop to stop");
                    log.append(id).append(dark?" dark":" light").append(orientation==0?" portrait":" landscape").append(" pixels/cycle/pause/resume OK\n");
                }finally{renderer.close();}
            }
            }
            ok=true;log.append("GRADIENT_RENDER_OK checks=").append(checks);
        }catch(Throwable error){log.append(android.util.Log.getStackTraceString(error));}
        finally{
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);
        }
        result.putString("stream",log.toString());finish(ok?-1:1,result);
    }
    private void checkSplines(GradientMotion motion){
        for(GradientSpline spline:motion.splines){
            float[] c=spline.coefficients;
            near(c[2],0,.00001,"Natural left boundary");
            near(2*c[14]+6*c[15]*(spline.knots[4]-spline.knots[3]),0,.00001,"Natural right boundary");
            for(int s=0;s<3;s++){
                float h=spline.knots[s+1]-spline.knots[s];int o=s*4;
                near(eval(spline,s,spline.knots[s+1]),c[o+4],.00001,"C0 continuity");
                near(c[o+1]+2*c[o+2]*h+3*c[o+3]*h*h,c[o+5],.00001,"C1 continuity");
                near(2*c[o+2]+6*c[o+3]*h,2*c[o+6],.00001,"C2 continuity");
            }
        }
    }
    private static double eval(GradientSpline curve,int s,double u){
        double x=u-curve.knots[s];int o=s*4;float[] c=curve.coefficients;
        return c[o]+x*(c[o+1]+x*(c[o+2]+x*c[o+3]));
    }
    private static double curve(GradientSpline spline,double u){return eval(spline,Math.min(3,(int)(u*4)),u);}
    private static double smooth(double lo,double hi,double y){double t=Math.max(0,Math.min(1,(y-lo)/(hi-lo)));return t*t*(3-2*t);}
    private static final double[] FREQUENCY={.25,.15,.20,.12,.20,.17,.05,.15};
    private static final double[] PHASE={334.34,2344.3,242.3,64.3,234.3,3634.3,2.3,334.3};
    private static double vertexColor(GradientMotion motion,int band,int channel,double u){
        float[] p=motion.colors();
        // Match float shader phase arithmetic before evaluating sin; the large
        // original phase constants otherwise amplify float rounding differences.
        float time=motion.time()*-.08f+53.89f;
        float phase=(float)((float)(u*(float)FREQUENCY[band])+time)+(float)PHASE[band];
        float radians=phase*(2f*(float)Math.PI);
        double t=.5+.5*Math.sin(radians);
        return p[band*8+channel]*(1-t)+p[band*8+4+channel]*t;
    }
    private void verifyPixels(ByteBuffer pixels,GradientMotion motion,boolean dark){
        for(int p=3;p<pixels.capacity();p+=4)require((pixels.get(p)&255)==255,"Opaque full surface");
        double totalError=0;int samples=0;
        for(int x=13;x<W;x+=41)for(int y=17;y<H;y+=57){
            double u=(x+.5)/W,v=1-(y+.5)/H,column=u*99;
            if(W>H)v=.5+(v-.5)/Math.pow(W/(double)H,2);
            int left=(int)column;double mix=column-left,u0=left/99d,u1=(left+1)/99d;
            double[] curves=new double[8];curves[7]=1;
            for(int i=0;i<6;i++)curves[i+1]=curve(motion.splines[i],u0)*(1-mix)+curve(motion.splines[i],u1)*mix;
            for(int ch=0;ch<3;ch++){
                double expected=vertexColor(motion,7,ch,u0)*(1-mix)+vertexColor(motion,7,ch,u1)*mix;
                for(int band=6;band>=0;band--){
                    double c=vertexColor(motion,band,ch,u0)*(1-mix)+vertexColor(motion,band,ch,u1)*mix;
                    double t=smooth(curves[band],curves[band+1],v);expected=c*(1-t)+expected*t;
                }
                if(dark)expected=(expected*.7-.5)*.9+.5;
                double error=Math.abs(expected*255-(pixels.get((y*W+x)*4+ch)&255));
                require(error<7,"Palette/curve pixel error="+error+" at "+x+","+y);
                totalError+=error;samples++;
            }
        }
        require(totalError/samples<2,"Mean GPU/reference color difference");
    }
    private void save(ByteBuffer rgba,String name)throws Exception{
        int[] colors=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int p=((H-1-y)*W+x)*4;colors[y*W+x]=0xff000000|((rgba.get(p)&255)<<16)|((rgba.get(p+1)&255)<<8)|(rgba.get(p+2)&255);
        }
        Bitmap bitmap=Bitmap.createBitmap(colors,W,H,Bitmap.Config.ARGB_8888);
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        finally{bitmap.recycle();}
    }
}
