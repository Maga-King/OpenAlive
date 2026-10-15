package org.aliveclean;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Timestamp-selected all-intra video decode; every method/callback uses AliveGL. */
final class FlymeVideoDecoder implements AutoCloseable {
    private final Surface surface;
    private final Handler handler;
    private final Runnable invalidate;
    private final ArrayDeque<Integer> input=new ArrayDeque<>();
    private final MediaExtractor extractor=new MediaExtractor();
    private MediaFormat format;
    private MediaCodec codec;
    private long requestedUs,lastQueuedUs=-1;
    private int frameIntervalUs=16666,outstanding,recoveries;
    private boolean visible,closed,pending=true;
    private RuntimeException failure;

    FlymeVideoDecoder(AssetManager assets,String path,Surface surface,Handler handler,Runnable invalidate)throws IOException{
        this.surface=surface;this.handler=handler;this.invalidate=invalidate;
        try(AssetFileDescriptor descriptor=assets.openFd(path)){
            extractor.setDataSource(descriptor.getFileDescriptor(),descriptor.getStartOffset(),descriptor.getLength());
            for(int track=0;track<extractor.getTrackCount();track++){
                MediaFormat candidate=extractor.getTrackFormat(track);
                String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("video/")){
                    format=candidate;extractor.selectTrack(track);break;
                }
            }
            if(format==null)throw new IOException("No video track: "+path);
            if(format.containsKey(MediaFormat.KEY_FRAME_RATE))frameIntervalUs=1_000_000/Math.max(1,format.getInteger(MediaFormat.KEY_FRAME_RATE));
        }catch(IOException|RuntimeException error){extractor.release();throw error;}
    }
    void visible(boolean value)throws IOException{
        if(closed||visible==value)return;
        visible=value;
        if(value){pending=true;start();}else stop();
    }
    void request(long timeUs){
        check();requestedUs=Math.max(0,timeUs);pending=true;feed();
    }
    void check(){if(failure!=null)throw failure;}
    private void start()throws IOException{
        MediaCodec created=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec=created;
        try{
            created.setCallback(new MediaCodec.Callback(){
                public void onInputBufferAvailable(MediaCodec owner,int index){
                    if(owner!=codec||!visible||closed)return;
                    input.add(index);feedSafely();
                }
                public void onOutputBufferAvailable(MediaCodec owner,int index,MediaCodec.BufferInfo info){
                    if(owner!=codec||!visible||closed)return;
                    try{
                        outstanding=Math.max(0,outstanding-1);
                        // A Surface decoder can report size=0 for a valid
                        // image. Like the original, present every image buffer.
                        owner.releaseOutputBuffer(index,(info.flags&(MediaCodec.BUFFER_FLAG_CODEC_CONFIG|MediaCodec.BUFFER_FLAG_END_OF_STREAM))==0);
                        feed();
                    }catch(RuntimeException error){fail("Flyme video output failed",error);}
                }
                public void onOutputFormatChanged(MediaCodec owner,MediaFormat output){}
                public void onError(MediaCodec owner,MediaCodec.CodecException error){
                    if(owner!=codec||closed)return;
                    // Like the original, recreate the codec after a codec
                    // failure while retaining the source clock and extractor.
                    stop();
                    if(visible&&recoveries++<2){
                        try{pending=true;start();return;}catch(IOException|RuntimeException retry){fail("Flyme video decoder recovery failed",retry);}
                    }else fail("Flyme video decoder failed",error);
                }
            },handler);
            created.configure(format,surface,null,0);created.start();
        }catch(RuntimeException error){stop();throw error;}
    }
    private void feedSafely(){
        try{feed();}catch(RuntimeException error){fail("Flyme video frame failed",error);}
    }
    private void fail(String message,Exception error){
        failure=new IllegalStateException(message,error);stop();
        // Let RenderLoop observe/check the failure and perform normal cleanup,
        // including when the original finite animation window has just ended.
        if(invalidate!=null)invalidate.run();
    }
    private void feed(){
        if(codec==null||!visible||closed||!pending||input.isEmpty()||outstanding>=2)return;
        select(requestedUs);
        long sampleUs=extractor.getSampleTime();
        if(sampleUs<0)throw new IllegalStateException("Video sample unavailable at "+requestedUs);
        pending=false;
        if(sampleUs==lastQueuedUs)return;
        int index=input.removeFirst();
        ByteBuffer buffer=codec.getInputBuffer(index);
        if(buffer==null)throw new IllegalStateException("Video input buffer unavailable");
        buffer.clear();int size=extractor.readSampleData(buffer,0);
        if(size<=0)throw new IllegalStateException("Empty video sample at "+sampleUs);
        codec.queueInputBuffer(index,0,size,sampleUs,0);lastQueuedUs=sampleUs;outstanding++;
    }
    private void select(long timeUs){
        // These original MP4s contain only sync frames. Preserve g.smali's
        // nearest-frame selection, including half-frame rounding and rewind.
        long current=extractor.getSampleTime(),delta=timeUs-current;
        if(current<0||delta<0||delta>frameIntervalUs*2.5){extractor.seekTo(timeUs,MediaExtractor.SEEK_TO_CLOSEST_SYNC);return;}
        if(delta>frameIntervalUs*.5&&!extractor.advance())extractor.seekTo(timeUs,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        if(delta>frameIntervalUs*1.5&&!extractor.advance())extractor.seekTo(timeUs,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
    }
    private void stop(){
        MediaCodec old=codec;codec=null;input.clear();outstanding=0;lastQueuedUs=-1;
        if(old!=null){
            try{old.stop();}catch(RuntimeException error){Log.w("AliveClean","Flyme video decoder stop failed",error);}
            try{old.release();}catch(RuntimeException error){Log.w("AliveClean","Flyme video decoder release failed",error);}
        }
    }
    public void close(){
        if(closed)return;closed=true;visible=false;stop();
        try{extractor.release();}catch(RuntimeException error){Log.w("AliveClean","Flyme video extractor release failed",error);}
    }
}
