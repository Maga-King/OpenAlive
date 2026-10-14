package org.aliveclean;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.Matrix;
import java.io.IOException;
import java.nio.*;

/** Flyme 9 gradient, retaining the original shaders and 100-column sampling mesh. */
final class GradientRenderer implements WallpaperRenderer {
    private static final int COLUMNS=100;
    final GradientMotion motion;
    private final AssetGl shader;
    private final int[] vao=new int[1],vbo=new int[1];
    private final float[] projection=new float[16];
    private final int[][] coefficients=new int[6][4];
    private final int width,height;

    GradientRenderer(AssetManager assets,int variant,boolean dark,int width,int height,int scene)throws IOException{
        this.width=width;this.height=height;
        motion=new GradientMotion(assets,variant);
        shader=new AssetGl(assets,"shader/gradient/gradient.vert","shader/gradient/gradient.frag");
        try{
            // Curves run in the vertex shader. Four corners lose every interior
            // control point; the original samples 100 vertical columns.
            FloatBuffer vertices=ByteBuffer.allocateDirect(COLUMNS*2*4*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            for(int col=0;col<COLUMNS;col++){
                float u=col/(float)(COLUMNS-1);
                vertices.put(u*2-1).put(-1).put(u).put(1);
                vertices.put(u*2-1).put(1).put(u).put(0);
            }
            vertices.flip();
            GLES30.glGenVertexArrays(1,vao,0);GLES30.glBindVertexArray(vao[0]);
            GLES30.glGenBuffers(1,vbo,0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,vertices.remaining()*4,vertices,GLES30.GL_STATIC_DRAW);
            attribute("a_position",0);attribute("a_texCoord0",8);
            GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
            Matrix.setIdentityM(projection,0);
            // Original landscape camera height is h*h/w, against a mesh of height w.
            if(width>height){float aspect=width/(float)height;projection[5]=aspect*aspect;}
            shader.use();shader.matrix("u_projection_view_matrix",projection);
            shader.f("u_dark_strength",dark?1:0);
            for(int i=0;i<GradientMotion.COLORS.length;i++)shader.color("u_color_"+GradientMotion.COLORS[i],motion.colors(),i*4);
            for(int curve=0;curve<6;curve++)for(int segment=0;segment<4;segment++){
                String name="u_spline"+(curve+1)+".func"+(segment+1);
                shader.f(name+".from",motion.splines[curve].knots[segment]);
                shader.f(name+".to",motion.splines[curve].knots[segment+1]);
                coefficients[curve][segment]=shader.at(name+".coefficients");
            }
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    private void attribute(String name,int offset)throws IOException{
        int location=GLES30.glGetAttribLocation(shader.id,name);
        if(location<0)throw new IOException("Missing gradient attribute: "+name);
        GLES30.glEnableVertexAttribArray(location);
        GLES30.glVertexAttribPointer(location,2,GLES30.GL_FLOAT,false,16,offset);
    }
    public WallpaperMotion motion(){return motion;}
    public void render(){
        GLES30.glViewport(0,0,width,height);GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glClearColor(0,0,0,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        shader.use();shader.f("u_time",motion.time());
        for(int curve=0;curve<6;curve++)for(int segment=0;segment<4;segment++)
            GLES30.glUniform4fv(coefficients[curve][segment],1,motion.splines[curve].coefficients,segment*4);
        GLES30.glBindVertexArray(vao[0]);GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,COLUMNS*2);
        GLES30.glBindVertexArray(0);
    }
    public void close(){shader.close();GLES30.glDeleteBuffers(1,vbo,0);GLES30.glDeleteVertexArrays(1,vao,0);}
}
