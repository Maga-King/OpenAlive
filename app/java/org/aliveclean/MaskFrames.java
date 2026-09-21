package org.aliveclean;

import android.content.res.AssetManager;
import android.os.*;
import android.util.Log;
import android.opengl.GLES30;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Random-access mask data, at most four decoded frames and one active worker. */
final class MaskFrames implements AutoCloseable {
    static final int SIZE=720,BYTES=SIZE*SIZE*3,START=61,END=62;
    private final AssetManager assets;
    private final HandlerThread thread=new HandlerThread("PhotoMask");
    private final Handler worker,owner;
    private final Runnable ready;
    private final LinkedHashMap<Integer,ByteBuffer> cache=new LinkedHashMap<>(8,.75f,true);
    private final MaskDecoder decoder=new MaskDecoder();
    private final String[] paths;
    private final int size,bytes,start,end,capacity;
    private volatile boolean closed;
    private int desired,uploaded=-1,failures,decodedFrames,presentedFrames;
    private boolean queued;
    private final Runnable decode=this::decode;
    MaskFrames(AssetManager assets,Handler owner,Runnable ready){this(assets,owner,ready,PhotoStyle.get(1));}
    MaskFrames(AssetManager assets,Handler owner,Runnable ready,PhotoStyle style){
        this.assets=assets;this.owner=owner;this.ready=ready;size=style.size;bytes=size*size*3;start=style.frames;end=start+1;desired=start;
        // Keep 1080px styles bounded too: two buffers, rather than four large frames.
        capacity=Math.max(2,Math.min(4,4*BYTES/bytes));paths=new String[end+1];
        for(int i=0;i<paths.length;i++)paths[i]="masks/"+style.folder+"/"+(i==start?"start":i==end?"end":String.format(Locale.ROOT,"%03d",i))+".rgbz";
        thread.start();worker=new Handler(thread.getLooper());
    }
    static int index(long micros){return micros<=0?START:micros>=483667?END:Math.min(60,Math.round(micros*120f/1000000f));}
    synchronized boolean upload(int index,int texture){
        if(closed)return false;
        desired=index;
        if(uploaded==index)return true;
        ByteBuffer value=cache.get(index);
        if(value==null&&!queued&&failures<3){queued=true;worker.post(decode);}
        if(value==null)return false;
        // The cache lock protects a reusable buffer until GL has consumed it.
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);value.position(0);
        int side=index==end?720:size;
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGB,side,side,0,GLES30.GL_RGB,GLES30.GL_UNSIGNED_BYTE,value);
        if(Diagnostics.TRACE){if(index<start)presentedFrames++;if(index==end)Log.i("AliveClean","Frame mask endpoint: decoded="+decodedFrames+" presented="+presentedFrames);}
        uploaded=index;return true;
    }
    synchronized boolean failed(){return failures>=3;}
    private void decode(){
        int target;ByteBuffer pixels;
        synchronized(this){if(closed){queued=false;return;}target=desired;
            if(cache.size()>=capacity){pixels=cache.remove(cache.keySet().iterator().next());pixels.clear();}
            else pixels=ByteBuffer.allocateDirect(bytes);
        }
        try{
            int side=target==end?720:size;
            try(InputStream in=assets.open(paths[target])){decoder.decode(in,pixels,side*side*3);}
            synchronized(this){if(!closed){if(Diagnostics.TRACE)decodedFrames++;cache.put(target,pixels);while(cache.size()>capacity)cache.remove(cache.keySet().iterator().next());}}
            if(!closed)owner.post(ready);
        }catch(IOException|RuntimeException e){synchronized(this){failures++;}Log.e("AliveClean","Mask decode failed",e);}
        synchronized(this){queued=false;if(!closed&&failures<3&&desired!=target&&!cache.containsKey(desired)){queued=true;worker.post(decode);}}
    }
    @Override public synchronized void close(){if(closed)return;closed=true;worker.removeCallbacksAndMessages(null);cache.clear();worker.post(decoder::close);thread.quitSafely();}
}
