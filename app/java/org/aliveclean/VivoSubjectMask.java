package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import ai.onnxruntime.*;
import java.io.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.CancellationException;

/** Offline salient-object segmentation with the original U2NetP model. */
final class VivoSubjectMask {
    private static final Object INFERENCE=new Object();
    private static final String HASH="309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8";
    static final class Job {
        private boolean cancelled;
        private OrtSession.RunOptions running;
        synchronized void cancel(){cancelled=true;if(running!=null)try{running.setTerminate(true);}catch(OrtException ignored){}}
        synchronized void check(){if(cancelled)throw new CancellationException();}
        private synchronized void start(OrtSession.RunOptions options){check();running=options;}
        private synchronized void end(){running=null;}
        Bitmap generate(Context context,Bitmap source,RectF selection)throws Exception{
            synchronized(INFERENCE){
                check();File model=model(context);check();
                int l=Math.max(0,Math.min(source.getWidth()-1,Math.round(selection.left*source.getWidth())));
                int t=Math.max(0,Math.min(source.getHeight()-1,Math.round(selection.top*source.getHeight())));
                int r=Math.max(l+1,Math.min(source.getWidth(),Math.round(selection.right*source.getWidth())));
                int b=Math.max(t+1,Math.min(source.getHeight(),Math.round(selection.bottom*source.getHeight())));
                Bitmap cropped=Bitmap.createBitmap(source,l,t,r-l,b-t),resized=Bitmap.createScaledBitmap(cropped,320,320,true);
                int[] pixels=new int[320*320];resized.getPixels(pixels,0,320,0,0,320,320);
                if(resized!=cropped)resized.recycle();if(cropped!=source)cropped.recycle();
                FloatBuffer input=ByteBuffer.allocateDirect(3*320*320*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                float maximum=1;for(int pixel:pixels)maximum=Math.max(maximum,Math.max((pixel>>16)&255,Math.max((pixel>>8)&255,pixel&255)));
                float[] mean={.485f,.456f,.406f},std={.229f,.224f,.225f};
                for(int channel=0;channel<3;channel++)for(int pixel:pixels)input.put((((pixel>>(16-8*channel))&255)/maximum-mean[channel])/std[channel]);input.rewind();
                OrtEnvironment environment=OrtEnvironment.getEnvironment();
                try(OrtSession.SessionOptions config=new OrtSession.SessionOptions()){
                    config.setIntraOpNumThreads(2);config.setInterOpNumThreads(1);
                    config.setCPUArenaAllocator(false);config.setMemoryPatternOptimization(false);
                    config.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
                    check();
                    try(OrtSession session=environment.createSession(model.getPath(),config);
                        OnnxTensor tensor=OnnxTensor.createTensor(environment,input,new long[]{1,3,320,320});
                        OrtSession.RunOptions run=new OrtSession.RunOptions()){
                        start(run);
                        try(OrtSession.Result result=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),tensor),Collections.singleton(session.getOutputNames().iterator().next()),run)){
                            check();FloatBuffer values=((OnnxTensor)result.get(0)).getFloatBuffer();
                            float[] scores=new float[320*320];values.get(scores);float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
                            for(float value:scores){lo=Math.min(lo,value);hi=Math.max(hi,value);}
                            for(int i=0;i<scores.length;i++){int value=Math.round(Math.max(0,Math.min(1,(scores[i]-lo)/Math.max(1e-6f,hi-lo)))*255);pixels[i]=Color.rgb(value,value,value);}
                            Bitmap small=Bitmap.createBitmap(pixels,320,320,Bitmap.Config.ARGB_8888);
                            Bitmap mask=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);mask.eraseColor(Color.BLACK);
                            try{new Canvas(mask).drawBitmap(small,null,new Rect(l,t,r,b),new Paint(Paint.FILTER_BITMAP_FLAG));}finally{small.recycle();}
                            return mask;
                        }finally{end();}
                    }
                }
            }
        }
    }
    private static File model(Context context)throws Exception{
        File target=new File(context.getCodeCacheDir(),"u2netp-"+HASH+".onnx");
        if(target.isFile()&&HASH.equals(hash(target)))return target;
        File temporary=new File(context.getCodeCacheDir(),"u2netp-import.tmp");
        try(InputStream in=context.getAssets().open("vivo/models/u2netp.onnx");FileOutputStream out=new FileOutputStream(temporary)){
            byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);out.getFD().sync();
        }
        if(!HASH.equals(hash(temporary))){temporary.delete();throw new IOException("主体模型校验失败");}
        if(target.exists()&&!target.delete())throw new IOException("无法更新主体模型");
        if(!temporary.renameTo(target))throw new IOException("无法保存主体模型");return target;
    }
    private static String hash(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){byte[] bytes=new byte[65536];for(int n;(n=input.read(bytes))!=-1;)digest.update(bytes,0,n);}
        StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return hex.toString();
    }
}
