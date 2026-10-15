package org.aliveclean;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Flyme Stereo's original four depth bands, then hole repair and scene crop. */
final class StereoRenderer implements WallpaperRenderer {
    final StereoMotion motion;
    private AssetGl split,compose;
    private final int[] textures=new int[3],framebuffer=new int[1],vaos=new int[2],vbo=new int[1];
    private final float[] crop=new float[16],flip=new float[16];
    private final int width,height;
    private float cropX,cropY;

    StereoRenderer(AssetManager assets,int variant,boolean dark,int width,int height,int scene)throws IOException {
        this(assets,variant,dark,width,height,scene,false);
    }
    StereoRenderer(AssetManager assets,int variant,boolean dark,int width,int height,int scene,boolean keepLock)throws IOException {
        this.width=width;this.height=height;
        int version=variant>=501&&variant<=503?variant-497:variant;
        if(version<4||version>6)throw new IOException("Unknown Stereo variant: "+variant);
        motion=new StereoMotion(scene,keepLock);
        try {
            split=new AssetGl(assets,"stereo/vertex_split.glsl","stereo/fragment_mixformanual.glsl");
            compose=new AssetGl(assets,"stereo/vertex.glsl","stereo/fragment_blur.glsl");
            GLES30.glGenTextures(3,textures,0);
            Bitmap main=bitmap(assets,"stereo/mz_stereo_main_v"+version+".webp");
            int imageWidth=main.getWidth(),imageHeight=main.getHeight();
            upload(textures[0],main);
            Bitmap depth=bitmap(assets,"stereo/mz_stereo_depth_v"+version+".png");
            // Original PreviewDrawer enlarges depth to the main image dimensions.
            if(depth.getWidth()<imageWidth){
                Bitmap scaled=Bitmap.createScaledBitmap(depth,imageWidth,imageHeight,true);
                if(scaled!=depth)depth.recycle();depth=scaled;
            }
            upload(textures[1],depth);
            cropX=cropY=.96f;
            if(imageWidth/(float)imageHeight>width/(float)height)cropX*=imageHeight*(float)width/(height*(float)imageWidth);
            else cropY*=imageWidth*(float)height/(width*(float)imageHeight);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures[2]);parameters();
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA,width,height,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,null);
            GLES30.glGenFramebuffers(1,framebuffer,0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer[0]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,textures[2],0);
            if(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)!=GLES30.GL_FRAMEBUFFER_COMPLETE)
                throw new IOException("Stereo framebuffer incomplete");
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
            FloatBuffer vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(new float[]{-1,1,0,0, 1,1,1,0, -1,-1,0,1, 1,-1,1,1}).flip();
            GLES30.glGenBuffers(1,vbo,0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,64,vertices,GLES30.GL_STATIC_DRAW);
            GLES30.glGenVertexArrays(2,vaos,0);
            setup(0,split);setup(1,compose);
            GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
            Matrix.setIdentityM(flip,0);flip[5]=-1;flip[10]=-1;
            split.use();split.matrix("uMVPMatrix",flip);split.f("focus",.6f);
            GLES30.glUniform1i(split.at("texture"),0);GLES30.glUniform1i(split.at("depthtexture"),1);
            compose.use();compose.f("focus",.6f);compose.f("u_dark_strength",dark?1:0);
            GLES30.glUniform1i(compose.at("texture"),0);GLES30.glUniform1i(compose.at("oritexture"),1);
            GLES30.glUniform1i(compose.at("needrepeat"),0);
        } catch(IOException|RuntimeException error){close();throw error;}
    }
    private static Bitmap bitmap(AssetManager assets,String path)throws IOException{
        try(InputStream input=assets.open(path)){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            Bitmap image=BitmapFactory.decodeStream(input,null,options);
            if(image==null)throw new IOException("Invalid Stereo texture: "+path);return image;
        }
    }
    private static void parameters(){
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
    }
    private static void upload(int texture,Bitmap image){
        try{GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);parameters();GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,image,0);}
        finally{image.recycle();}
    }
    private void setup(int index,AssetGl shader)throws IOException{
        GLES30.glBindVertexArray(vaos[index]);
        attribute(shader,"aPosition",0);attribute(shader,"aTextureCoordinate",8);
    }
    private static void attribute(AssetGl shader,String name,int offset)throws IOException{
        int location=GLES30.glGetAttribLocation(shader.id,name);
        if(location<0)throw new IOException("Missing Stereo attribute: "+name);
        GLES30.glEnableVertexAttribArray(location);GLES30.glVertexAttribPointer(location,2,GLES30.GL_FLOAT,false,16,offset);
    }
    private static void bind(int unit,int texture){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+unit);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);}
    public WallpaperMotion motion(){return motion;}
    /** Live settings, on the render looper; defaults are sensitivity 0 and AOD on. */
    void configure(int sensitivity,boolean allowAod){motion.configure(sensitivity,allowAod);}
    public void render(){
        GLES30.glViewport(0,0,width,height);GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,framebuffer[0]);
        split.use();bind(0,textures[0]);bind(1,textures[1]);
        GLES30.glUniform2f(split.at("shift"),motion.shiftX(),motion.shiftY());
        GLES30.glBindVertexArray(vaos[0]);GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
        compose.use();bind(0,textures[2]);bind(1,textures[0]);
        float sx=cropX*motion.scale(),sy=cropY*motion.scale();
        Matrix.setIdentityM(crop,0);crop[0]=sx;crop[5]=sy;crop[12]=(1-sx)*.5f;crop[13]=(1-sy)*.5f;
        compose.matrix("uTexMatrix",crop);
        GLES30.glUniform2f(compose.at("shift"),motion.shiftX(),motion.shiftY());
        GLES30.glBindVertexArray(vaos[1]);GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);
        GLES30.glBindVertexArray(0);GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }
    public void close(){
        if(split!=null)split.close();if(compose!=null)compose.close();
        GLES30.glDeleteTextures(3,textures,0);GLES30.glDeleteFramebuffers(1,framebuffer,0);
        GLES30.glDeleteBuffers(1,vbo,0);GLES30.glDeleteVertexArrays(2,vaos,0);
    }
}
