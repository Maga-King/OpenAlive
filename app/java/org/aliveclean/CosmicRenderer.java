package org.aliveclean;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.Matrix;
import java.io.*;
import java.nio.*;

/** Original Cosmic shaders/model with a portable GL host. Owns only its GL objects. */
final class CosmicRenderer implements AutoCloseable {
    final CosmicMotion motion;
    private AssetGl sphere,background;
    private final int[] buffers=new int[3],arrays=new int[2];
    private final float[] projection=new float[16],view=new float[16],pv=new float[16],model=new float[16],bgMatrix=new float[16],color=new float[4];
    private final int width,height;
    private int count;
    CosmicRenderer(AssetManager assets,int variant,boolean dark,int width,int height,int scene)throws IOException{
        this.width=width;this.height=height;motion=new CosmicMotion(assets,variant,dark,scene);
        try{
            sphere=new AssetGl(assets,"shader/cosmic/cosmic_vertex.glsl","shader/cosmic/cosmic_frag.glsl");
            background=new AssetGl(assets,"shader/cosmic/gradient_background_vertex.glsl","shader/cosmic/gradient_background_frag.glsl");
            ByteBuffer mesh=ByteBuffer.wrap(AssetGl.bytes(assets,"cosmic/sphere.bin")).order(ByteOrder.LITTLE_ENDIAN);
            int vertices=mesh.getInt();count=mesh.getInt();
            if(vertices<=0||count<=0||mesh.remaining()!=vertices*12+count*2)throw new IOException("Invalid sphere mesh");
            ByteBuffer positions=ByteBuffer.allocateDirect(vertices*12).order(ByteOrder.nativeOrder());
            for(int i=0;i<vertices*3;i++)positions.putFloat(mesh.getFloat());positions.flip();
            ByteBuffer indices=ByteBuffer.allocateDirect(count*2).order(ByteOrder.nativeOrder());
            for(int i=0;i<count;i++)indices.putShort(mesh.getShort());indices.flip();
            GLES30.glGenVertexArrays(2,arrays,0);GLES30.glGenBuffers(3,buffers,0);
            GLES30.glBindVertexArray(arrays[0]);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,buffers[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,positions.remaining(),positions,GLES30.GL_STATIC_DRAW);
            attribute(sphere,"a_position",3,12,0);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,buffers[1]);GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,indices.remaining(),indices,GLES30.GL_STATIC_DRAW);
            float[] quad={-1,1,0,0,0, 1,1,0,1,0, -1,-1,0,0,1, 1,-1,0,1,1};
            FloatBuffer vertices2=ByteBuffer.allocateDirect(quad.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();vertices2.put(quad).flip();
            GLES30.glBindVertexArray(arrays[1]);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,buffers[2]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,quad.length*4,vertices2,GLES30.GL_STATIC_DRAW);
            attribute(background,"a_position",3,20,0);attribute(background,"a_texCoord0",2,20,12);
            GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
            Matrix.orthoM(projection,0,-width/2f,width/2f,-height/2f,height/2f,0,height*8f);
            Matrix.setLookAtM(view,0,height*4f,0,0,0,0,0,0,1,0);Matrix.multiplyMM(pv,0,projection,0,view,0);
            Matrix.setIdentityM(bgMatrix,0);
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    private static void attribute(AssetGl program,String name,int size,int stride,int offset){
        int location=GLES30.glGetAttribLocation(program.id,name);if(location<0)return;
        GLES30.glEnableVertexAttribArray(location);GLES30.glVertexAttribPointer(location,size,GLES30.GL_FLOAT,false,stride,offset);
    }
    void render(){
        float[] v=motion.values;
        GLES30.glViewport(0,0,width,height);GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glClearColor(0,0,0,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);
        background.use();background.matrix("u_projectionViewMatrix",bgMatrix);
        GLES30.glUniform2f(background.at("u_start_pos"),0,0);GLES30.glUniform2f(background.at("u_end_pos"),.5f,1);
        gradient(v,0);background.color("u_start_color",color,0);gradient(v,4);background.color("u_end_color",color,0);
        GLES30.glBindVertexArray(arrays[1]);GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);GLES30.glEnable(GLES30.GL_CULL_FACE);GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        sphere.use();sphere.matrix("u_projectionViewMatrix",pv);
        // Official upload transposes this rotation; explicitly invert the angle
        // because GLES requires the glUniformMatrix transpose argument to be false.
        Matrix.setRotateM(model,0,-motion.angle,0,1,0);sphere.matrix("u_modelMatrix",model);
        float radians=(float)Math.toRadians(motion.angle);
        GLES30.glUniform3f(sphere.at("u_eye_position"),1000*(float)Math.cos(radians),0,-1000*(float)Math.sin(radians));
        GLES30.glUniform3f(sphere.at("u_translation"),v[0]*width,v[1]*height,0);
        sphere.f("u_scale",v[2]*width*.85f);float time=motion.time%7200;sphere.f("u_time",time<3600?time:7200-time);
        sphere.f("u_noise_amplitude",v[4]);sphere.f("u_stripe_divergent",v[5]);sphere.f("u_stripe_thickness",v[6]);
        sphere.f("u_highlight_thickness",v[7]);sphere.f("u_highlight_strength",v[8]);sphere.f("u_alpha",v[9]);
        sphere.color("u_base_color",v,10);sphere.color("u_stripe_color_1",v,14);sphere.color("u_stripe_color_2",v,18);
        sphere.color("u_stripe_color_3",v,22);sphere.color("u_highlight_color",v,26);
        GLES30.glBindVertexArray(arrays[0]);GLES30.glDrawElements(GLES30.GL_TRIANGLES,count,GLES30.GL_UNSIGNED_SHORT,0);
        GLES30.glBindVertexArray(0);GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);
    }
    private void gradient(float[] values,int end){
        int quadrant=(int)(motion.angle/90)%4,next=(quadrant+1)%4;float mix=(motion.angle%90)/90;
        for(int i=0;i<4;i++)color[i]=values[30+quadrant*8+end+i]*(1-mix)+values[30+next*8+end+i]*mix;
    }
    public void close(){
        if(sphere!=null){sphere.close();sphere=null;}if(background!=null){background.close();background=null;}
        GLES30.glDeleteBuffers(3,buffers,0);GLES30.glDeleteVertexArrays(2,arrays,0);
    }
}
