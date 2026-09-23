package org.aliveclean;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.Matrix;
import java.io.IOException;
import java.nio.*;

/** Original Bubble lighting and disk background shaders on a portable GL host. */
final class BubbleRenderer implements WallpaperRenderer {
    final BubbleMotion motion;
    private AssetGl bubble,background;
    private final int[] arrays=new int[2],buffers=new int[4];
    private final float[] projection=new float[16],view=new float[16],pv=new float[16],bgProjection=new float[16],transform=new float[16];
    private final int width,height;
    private int sphereCount,diskCount;
    private static final String[] BG={"u_color_a_inner","u_color_a_cp1","u_color_a_cp2","u_color_a_cp3","u_color_a_cp4","u_color_a_outer","u_color_b_inner","u_color_b_cp1","u_color_b_cp2","u_color_b_cp3","u_color_b_cp4","u_color_b_outer","u_vignette_color"};
    private static final String[] LIGHT_COLOR={"u_lightColor1","u_lightColor2","u_lightColor3"},LIGHT_POSITION={"u_lightLocation1","u_lightLocation2","u_lightLocation3"},LIGHT_RADIUS={"u_lightRadius1","u_lightRadius2","u_lightRadius3"};
    private static final int[] ORDER={2,0,1};
    private static final float[] OSCILLATION={.5767f,.2976f,.3787f};
    BubbleRenderer(AssetManager assets,int variant,boolean dark,int width,int height,int scene,boolean keepLock)throws IOException {
        this.width=width;this.height=height;motion=new BubbleMotion(assets,variant,dark,scene,keepLock);
        try {
            bubble=new AssetGl(assets,"shader/bubble/bubble_vertex.glsl","shader/bubble/bubble_frag.glsl");
            background=new AssetGl(assets,"soundviz/disk.vert","soundviz/disk.frag");
            GLES30.glGenVertexArrays(2,arrays,0);GLES30.glGenBuffers(4,buffers,0);
            float[] sphere=new float[49*49*8];short[] indices=new short[48*48*6];int at=0;
            for(int y=0;y<=48;y++)for(int x=0;x<=48;x++){
                double latitude=Math.PI*y/48,longitude=2*Math.PI*x/48;
                float px=(float)(Math.sin(latitude)*Math.cos(longitude)),py=(float)Math.cos(latitude),pz=(float)(Math.sin(latitude)*Math.sin(longitude));
                sphere[at++]=px;sphere[at++]=py;sphere[at++]=pz;sphere[at++]=px;sphere[at++]=py;sphere[at++]=pz;sphere[at++]=1-x/48f;sphere[at++]=y/48f;
            }
            at=0;for(int y=0;y<48;y++)for(int x=0;x<48;x++){int a=y*49+x,b=a+49;indices[at++]=(short)a;indices[at++]=(short)(a+1);indices[at++]=(short)b;indices[at++]=(short)(a+1);indices[at++]=(short)(b+1);indices[at++]=(short)b;}
            sphereCount=indices.length;upload(0,sphere,indices);attribute(bubble,"a_position",3,32,0);attribute(bubble,"a_normal",3,32,12);attribute(bubble,"a_texCoord0",2,32,24);
            float[] disk=new float[100*2*5];short[] strip=new short[99*6];at=0;
            for(int x=0;x<100;x++)for(int y=0;y<2;y++){disk[at++]=7*x/99f-3.5f;disk[at++]=y*4-2;disk[at++]=0;disk[at++]=x/99f;disk[at++]=1-y;}
            at=0;for(int x=0;x<99;x++){int a=x*2,b=a+2;strip[at++]=(short)a;strip[at++]=(short)(b+1);strip[at++]=(short)(a+1);strip[at++]=(short)(b+1);strip[at++]=(short)a;strip[at++]=(short)b;}
            diskCount=strip.length;upload(1,disk,strip);attribute(background,"a_position",3,20,0);attribute(background,"a_texCoord0",2,20,12);
            GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
            float worldHeight=1080f*height/width;
            Matrix.orthoM(projection,0,-540,540,-worldHeight/2,worldHeight/2,0,2160);
            Matrix.setLookAtM(view,0,0,0,1080,0,0,0,0,1,0);Matrix.multiplyMM(pv,0,projection,0,view,0);
            float aspect=(float)width/height,bgHeight=19.6f/(float)Math.sqrt(1+aspect*aspect)*(width<height?.937f:.7f);
            Matrix.orthoM(bgProjection,0,-bgHeight*aspect/2,bgHeight*aspect/2,-bgHeight/2,bgHeight/2,-1,1);
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    private void upload(int slot,float[] vertices,short[] indices){
        FloatBuffer v=ByteBuffer.allocateDirect(vertices.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();v.put(vertices).flip();
        ShortBuffer i=ByteBuffer.allocateDirect(indices.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();i.put(indices).flip();
        GLES30.glBindVertexArray(arrays[slot]);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,buffers[slot*2]);GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,vertices.length*4,v,GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,buffers[slot*2+1]);GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,indices.length*2,i,GLES30.GL_STATIC_DRAW);
    }
    private static void attribute(AssetGl p,String name,int size,int stride,int offset){int a=GLES30.glGetAttribLocation(p.id,name);if(a>=0){GLES30.glEnableVertexAttribArray(a);GLES30.glVertexAttribPointer(a,size,GLES30.GL_FLOAT,false,stride,offset);}}
    public WallpaperMotion motion(){return motion;}
    public void render(){
        float[] v=motion.values;float t=106+motion.time;
        GLES30.glViewport(0,0,width,height);GLES30.glDisable(GLES30.GL_SCISSOR_TEST);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glClearColor(0,0,0,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);
        background.use();background.matrix("u_projTrans",bgProjection);
        float rotation=(float)(25*.5*(Math.cos(t*.025*2*Math.PI)+1)*Math.signum(Math.cos(t*.025*Math.PI)));
        Matrix.setRotateM(transform,0,rotation,0,0,1);Matrix.scaleM(transform,0,3.5f,4.9f,1);background.matrix("u_transform",transform);
        background.f("u_time",t);background.f("u_seed",40);background.f("u_phase",.65f-(motion.time*.01f%1)*(float)(2*Math.PI));
        GLES30.glUniform2f(background.at("u_resolution"),width,height);for(int i=0;i<BG.length;i++)background.color(BG[i],v,108+i*4);
        GLES30.glBindVertexArray(arrays[1]);GLES30.glDrawElements(GLES30.GL_TRIANGLES,diskCount,GLES30.GL_UNSIGNED_SHORT,0);
        bubble.use();bubble.matrix("u_projectionViewMatrix",pv);GLES30.glEnable(GLES30.GL_CULL_FACE);GLES30.glEnable(GLES30.GL_BLEND);GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glBindVertexArray(arrays[0]);
        for(int layer:ORDER){
            int offset=layer*BubbleMotion.STRIDE;if(v[offset+4]<=0)continue;
            float phase=motion.time*OSCILLATION[layer]*3;
            GLES30.glUniform4f(bubble.at("u_location"),v[offset]+(float)Math.sin(phase+4.2345)*56,v[offset+1]+(float)Math.cos(phase+35.7567)*56,v[offset+2],v[offset+3]);
            bubble.f("u_alpha",v[offset+4]);bubble.color("u_materialColor",v,offset+5);
            for(int light=0;light<3;light++){int p=offset+9+light*9;bubble.color(LIGHT_COLOR[light],v,p);GLES30.glUniform4f(bubble.at(LIGHT_POSITION[light]),v[p+4],v[p+5],v[p+6],v[p+7]);bubble.f(LIGHT_RADIUS[light],v[p+8]);}
            GLES30.glDrawElements(GLES30.GL_TRIANGLES,sphereCount,GLES30.GL_UNSIGNED_SHORT,0);
        }
        GLES30.glBindVertexArray(0);GLES30.glDisable(GLES30.GL_CULL_FACE);GLES30.glDisable(GLES30.GL_BLEND);
    }
    public void close(){if(bubble!=null){bubble.close();bubble=null;}if(background!=null){background.close();background=null;}GLES30.glDeleteBuffers(4,buffers,0);GLES30.glDeleteVertexArrays(2,arrays,0);}
}
