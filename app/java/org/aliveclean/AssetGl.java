package org.aliveclean;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.regex.*;

/** Asset shader loader. Locations are cached; no lookups or allocation per frame. */
final class AssetGl implements AutoCloseable {
    final int id;
    private final HashMap<String,Integer> uniforms=new HashMap<>();
    AssetGl(AssetManager assets,String vertex,String fragment)throws IOException{
        int v=compile(GLES30.GL_VERTEX_SHADER,source(assets,vertex,new HashSet<>())),f=0,p=0;
        try{
            f=compile(GLES30.GL_FRAGMENT_SHADER,source(assets,fragment,new HashSet<>()));
            p=GLES30.glCreateProgram();GLES30.glAttachShader(p,v);GLES30.glAttachShader(p,f);GLES30.glLinkProgram(p);
            int[] result=new int[1];GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,result,0);
            if(result[0]==0)throw new IOException(GLES30.glGetProgramInfoLog(p));
            id=p;
        }catch(IOException|RuntimeException error){if(p!=0)GLES30.glDeleteProgram(p);throw error;}
        finally{GLES30.glDeleteShader(v);if(f!=0)GLES30.glDeleteShader(f);}
    }
    static byte[] bytes(AssetManager assets,String path)throws IOException{
        try(InputStream in=assets.open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[16384];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();
        }
    }
    static String text(AssetManager assets,String path)throws IOException{return new String(bytes(assets,path),java.nio.charset.StandardCharsets.UTF_8);}
    private static String source(AssetManager assets,String path,Set<String> stack)throws IOException{
        if(!stack.add(path))throw new IOException("Recursive shader include: "+path);
        String input=text(assets,path);
        Matcher m=Pattern.compile("#include\\s+<([^>]+)>").matcher(input);StringBuffer out=new StringBuffer();
        while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(source(assets,m.group(1),stack)));
        m.appendTail(out);stack.remove(path);return out.toString();
    }
    private static int compile(int type,String source)throws IOException{
        int shader=GLES30.glCreateShader(type);GLES30.glShaderSource(shader,source);GLES30.glCompileShader(shader);
        int[] result=new int[1];GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,result,0);
        if(result[0]==0){String message=GLES30.glGetShaderInfoLog(shader);GLES30.glDeleteShader(shader);throw new IOException(message);}return shader;
    }
    int at(String name){Integer value=uniforms.get(name);if(value==null){value=GLES30.glGetUniformLocation(id,name);uniforms.put(name,value);}return value;}
    void use(){GLES30.glUseProgram(id);}
    void f(String name,float value){GLES30.glUniform1f(at(name),value);}
    void matrix(String name,float[] value){GLES30.glUniformMatrix4fv(at(name),1,false,value,0);}
    void color(String name,float[] value,int offset){GLES30.glUniform4fv(at(name),1,value,offset);}
    public void close(){GLES30.glDeleteProgram(id);}
}
