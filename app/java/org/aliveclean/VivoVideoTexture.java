package org.aliveclean;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.view.Surface;
import java.io.File;

/** Decodes one independently selected clip into an existing Vivo image texture. */
final class VivoVideoTexture implements AutoCloseable {
    private final MediaPlayer player=new MediaPlayer();
    private SurfaceTexture stream;
    private Surface surface;
    private int external,fbo,program,vao;
    private final int width,height;
    private final boolean flipY,p3;
    private final int rotation;
    private volatile boolean pending,closed,playing;
    private final float[] transform=new float[16];
    private static final int[] CAPS={GLES30.GL_BLEND,GLES30.GL_DEPTH_TEST,GLES30.GL_STENCIL_TEST,GLES30.GL_SCISSOR_TEST,GLES30.GL_CULL_FACE};
    VivoVideoTexture(File file,int target,int width,int height,boolean flipY,boolean p3,int rotation,Runnable invalidate)throws Exception{
        this.width=width;this.height=height;this.flipY=flipY;this.p3=p3;this.rotation=rotation;
        int[] prior=new int[3];GLES30.glGetIntegerv(GLES30.GL_DRAW_FRAMEBUFFER_BINDING,prior,0);
        GLES30.glGetIntegerv(GLES11Ext.GL_TEXTURE_BINDING_EXTERNAL_OES,prior,1);
        try{
            int[] id=new int[1];GLES30.glGenTextures(1,id,0);external=id[0];GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glGenFramebuffers(1,id,0);fbo=id[0];GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,fbo);
            GLES30.glFramebufferTexture2D(GLES30.GL_DRAW_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_TEXTURE_2D,target,0);
            if(GLES30.glCheckFramebufferStatus(GLES30.GL_DRAW_FRAMEBUFFER)!=GLES30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("视频纹理不可渲染");
            createProgram();GLES30.glGenVertexArrays(1,id,0);vao=id[0];
            stream=new SurfaceTexture(external);stream.setDefaultBufferSize(width,height);
            stream.setOnFrameAvailableListener(s->{if(!closed){pending=true;invalidate.run();}});
            surface=new Surface(stream);player.setSurface(surface);player.setVolume(0,0);player.setLooping(true);
            player.setDataSource(file.getPath());player.prepare();
            player.setOnErrorListener((p,what,extra)->{android.util.Log.e("AliveClean","Vivo clip failed: "+what+"/"+extra);return true;});
        }catch(Exception error){close();throw error;}
        finally{GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,prior[0]);GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,prior[1]);}
    }
    synchronized void visible(boolean visible){
        if(closed||playing==visible)return;
        try{if(visible)player.start();else player.pause();playing=visible;}
        catch(IllegalStateException error){android.util.Log.w("AliveClean","Vivo video lifecycle",error);}
    }
    void update(){
        if(closed||!pending)return;pending=false;
        int[] state=new int[6],viewport=new int[4];boolean[] enabled=new boolean[CAPS.length],mask=new boolean[4];
        GLES30.glGetIntegerv(GLES30.GL_CURRENT_PROGRAM,state,0);GLES30.glGetIntegerv(GLES30.GL_VERTEX_ARRAY_BINDING,state,1);
        GLES30.glGetIntegerv(GLES30.GL_DRAW_FRAMEBUFFER_BINDING,state,2);GLES30.glGetIntegerv(GLES30.GL_ACTIVE_TEXTURE,state,3);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glGetIntegerv(GLES11Ext.GL_TEXTURE_BINDING_EXTERNAL_OES,state,4);
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT,viewport,0);GLES30.glGetBooleanv(GLES30.GL_COLOR_WRITEMASK,mask,0);
        for(int i=0;i<CAPS.length;i++){enabled[i]=GLES30.glIsEnabled(CAPS[i]);GLES30.glDisable(CAPS[i]);}
        try{
            stream.updateTexImage();stream.getTransformMatrix(transform);
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,fbo);GLES30.glViewport(0,0,width,height);GLES30.glColorMask(true,true,true,true);
            GLES30.glUseProgram(program);GLES30.glBindVertexArray(vao);GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"frame"),0);
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"flip"),flipY?1:0);
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"wide"),p3?1:0);
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program,"rotation"),rotation);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program,"transform"),1,false,transform,0);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,3);
        }finally{
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,state[4]);GLES30.glActiveTexture(state[3]);
            GLES30.glUseProgram(state[0]);GLES30.glBindVertexArray(state[1]);GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,state[2]);
            GLES30.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GLES30.glColorMask(mask[0],mask[1],mask[2],mask[3]);
            for(int i=0;i<CAPS.length;i++)if(enabled[i])GLES30.glEnable(CAPS[i]);else GLES30.glDisable(CAPS[i]);
        }
    }
    private void createProgram(){
        int vertex=shader(GLES30.GL_VERTEX_SHADER,"#version 300 es\nout vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.0-1.0,0.0,1.0);}");
        int fragment=0;
        try{
            fragment=shader(GLES30.GL_FRAGMENT_SHADER,"#version 300 es\n#extension GL_OES_EGL_image_external_essl3 : require\nprecision highp float;uniform samplerExternalOES frame;uniform mat4 transform;uniform bool flip,wide;in vec2 uv;out vec4 color;\n"
                +"vec3 lin(vec3 c){return mix(c/12.92,pow((c+0.055)/1.055,vec3(2.4)),step(vec3(0.04045),c));}vec3 enc(vec3 c){return mix(c*12.92,1.055*pow(max(c,vec3(0.0)),vec3(1.0/2.4))-0.055,step(vec3(0.0031308),c));}"
                +"uniform int rotation;void main(){vec2 p=vec2(uv.x,flip?uv.y:1.0-uv.y);if(rotation==1)p=vec2(1.0-p.y,p.x);else if(rotation==2)p=1.0-p;else if(rotation==3)p=vec2(p.y,1.0-p.x);vec3 c=texture(frame,(transform*vec4(p,0.0,1.0)).xy).rgb;if(wide)c=enc(mat3(0.8225929,0.0331995,0.0170853,0.1775340,0.9667835,0.0723957,0.0,0.0,0.9103015)*lin(c));color=vec4(c,1.0);}");
            program=GLES30.glCreateProgram();GLES30.glAttachShader(program,vertex);GLES30.glAttachShader(program,fragment);GLES30.glLinkProgram(program);
            int[] ok=new int[1];GLES30.glGetProgramiv(program,GLES30.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES30.glGetProgramInfoLog(program));
        }finally{GLES30.glDeleteShader(vertex);if(fragment!=0)GLES30.glDeleteShader(fragment);}
    }
    private static int shader(int type,String source){int s=GLES30.glCreateShader(type);GLES30.glShaderSource(s,source);GLES30.glCompileShader(s);int[] ok=new int[1];GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String log=GLES30.glGetShaderInfoLog(s);GLES30.glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    @Override public synchronized void close(){
        if(closed)return;closed=true;player.release();if(surface!=null)surface.release();if(stream!=null)stream.release();
        if(external!=0)GLES30.glDeleteTextures(1,new int[]{external},0);if(fbo!=0)GLES30.glDeleteFramebuffers(1,new int[]{fbo},0);
        if(vao!=0)GLES30.glDeleteVertexArrays(1,new int[]{vao},0);if(program!=0)GLES30.glDeleteProgram(program);
    }
}
