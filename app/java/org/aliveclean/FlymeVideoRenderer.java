package org.aliveclean;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Original Flyme 9 浪流/深潜/涟漪 videos, OES shaders and playback clock. */
final class FlymeVideoRenderer implements WallpaperRenderer {
    private static final String BASE="flyme-video/";
    private final int width,height;
    private final FlymeVideoMotion motion;
    private final Handler handler;
    private final Runnable invalidate;
    private final int[] texture=new int[1],thumbnail=new int[1],vao=new int[2],vbo=new int[1];
    private final float[] projection=new float[16],transform=new float[16];
    private final float[] thumbnailTransform=new float[16];
    private AssetGl shader,thumbnailShader;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private FlymeVideoDecoder decoder;
    private boolean frameAvailable,hasFrame,closed;

    FlymeVideoRenderer(AssetManager assets,int variant,boolean dark,int width,int height,int scene,Runnable invalidate)throws IOException{
        if(width<1||height<1)throw new IOException("Invalid video viewport");
        int index=variant-400;
        if(index<1||index>3)throw new IOException("Unknown Flyme video variant: "+variant);
        if(Looper.myLooper()==null)throw new IOException("Video renderer requires a GL looper");
        this.width=width;this.height=height;this.invalidate=invalidate;
        handler=new Handler(Looper.myLooper());
        motion=new FlymeVideoMotion(index==1?9_983_333L:index==2?8_000_000L:9_950_000L);
        try{
            shader=new AssetGl(assets,BASE+"video_vertex.glsl",BASE+"video_runtime_frag.glsl");
            thumbnailShader=new AssetGl(assets,BASE+"video_vertex.glsl",BASE+"thumbnail_runtime_frag.glsl");
            String slug=index==1?"langliu":index==2?"shenqian":"lianyi";
            try(InputStream in=assets.open(BASE+slug+".png")){
                Bitmap bitmap=BitmapFactory.decodeStream(in);
                if(bitmap==null)throw new IOException("Invalid Flyme video thumbnail: "+slug);
                try{
                    GLES30.glGenTextures(1,thumbnail,0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,thumbnail[0]);
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
                    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,bitmap,0);
                }finally{bitmap.recycle();}
            }
            GLES30.glGenTextures(1,texture,0);GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            surfaceTexture=new SurfaceTexture(texture[0]);surfaceTexture.setDefaultBufferSize(1080,2340);
            surfaceTexture.setOnFrameAvailableListener(ignored->{if(closed)return;frameAvailable=true;requestFrame();},handler);
            surface=new Surface(surfaceTexture);
            decoder=new FlymeVideoDecoder(assets,BASE+"mz_video_wallpaper_v"+index+"_1080.mp4",surface,handler,this::requestFrame);
            // Original top-left UV=(0,1), transformed once by SurfaceTexture.
            float[] quad={-1,1,0,0,1, 1,1,0,1,1, 1,-1,0,1,0,
                          -1,1,0,0,1, 1,-1,0,1,0, -1,-1,0,0,0};
            FloatBuffer vertices=ByteBuffer.allocateDirect(quad.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();vertices.put(quad).flip();
            GLES30.glGenVertexArrays(2,vao,0);GLES30.glBindVertexArray(vao[0]);
            GLES30.glGenBuffers(1,vbo,0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,quad.length*4,vertices,GLES30.GL_STATIC_DRAW);
            attribute(shader,"a_position",3,0);attribute(shader,"a_texCoord0",2,12);
            GLES30.glBindVertexArray(vao[1]);
            attribute(thumbnailShader,"a_position",3,0);attribute(thumbnailShader,"a_texCoord0",2,12);
            GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
            Matrix.setIdentityM(projection,0);Matrix.setIdentityM(transform,0);
            Matrix.setIdentityM(thumbnailTransform,0);thumbnailTransform[5]=-1;thumbnailTransform[13]=1;
            if(width>=height)projection[5]=(int)(width*(double)width/height)/(float)height;
            shader.use();shader.matrix("u_projectionViewMatrix",projection);
            shader.f("u_dark_strength",dark?1:0);GLES30.glUniform1i(shader.at("u_texture"),0);
            thumbnailShader.use();thumbnailShader.matrix("u_projectionViewMatrix",projection);
            thumbnailShader.f("u_dark_strength",dark?1:0);GLES30.glUniform1i(thumbnailShader.at("u_texture"),0);
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    private void requestFrame(){if(!closed&&invalidate!=null)invalidate.run();}
    private void attribute(AssetGl program,String name,int size,int offset)throws IOException{
        int location=GLES30.glGetAttribLocation(program.id,name);
        if(location<0)throw new IOException("Missing video attribute: "+name);
        GLES30.glEnableVertexAttribArray(location);GLES30.glVertexAttribPointer(location,size,GLES30.GL_FLOAT,false,20,offset);
    }
    public WallpaperMotion motion(){return motion;}
    public void visible(boolean value){
        if(closed)return;motion.visible(value);
        try{decoder.visible(value);}catch(IOException error){throw new IllegalStateException("Unable to resume Flyme video",error);}
    }
    public void continuous(boolean value){motion.continuous(value);}
    public void interaction(){motion.interaction();}
    public void render(){
        if(closed)return;decoder.check();
        if(motion.takeTick())decoder.request(motion.sourceTimeUs());
        if(frameAvailable){
            frameAvailable=false;surfaceTexture.updateTexImage();surfaceTexture.getTransformMatrix(transform);hasFrame=true;
            if(thumbnailShader!=null){thumbnailShader.close();thumbnailShader=null;GLES30.glDeleteTextures(1,thumbnail,0);thumbnail[0]=0;}
        }
        GLES30.glViewport(0,0,width,height);GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glClearColor(0,0,0,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);
        AssetGl current=hasFrame?shader:thumbnailShader;
        current.use();current.matrix("u_transform_matrix",hasFrame?transform:thumbnailTransform);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(hasFrame?GLES11Ext.GL_TEXTURE_EXTERNAL_OES:GLES30.GL_TEXTURE_2D,hasFrame?texture[0]:thumbnail[0]);
        GLES30.glBindVertexArray(vao[hasFrame?0:1]);GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,6);GLES30.glBindVertexArray(0);
    }
    public void close(){
        if(closed)return;closed=true;
        if(decoder!=null){decoder.close();decoder=null;}
        if(surface!=null){surface.release();surface=null;}
        if(surfaceTexture!=null){surfaceTexture.setOnFrameAvailableListener(null);surfaceTexture.release();surfaceTexture=null;}
        if(shader!=null){shader.close();shader=null;}
        if(thumbnailShader!=null){thumbnailShader.close();thumbnailShader=null;}
        GLES30.glDeleteBuffers(1,vbo,0);GLES30.glDeleteVertexArrays(2,vao,0);GLES30.glDeleteTextures(1,texture,0);
        GLES30.glDeleteTextures(1,thumbnail,0);
    }
}
