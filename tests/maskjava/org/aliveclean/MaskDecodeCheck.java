package org.aliveclean;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;
import android.os.Debug;
import org.json.JSONObject;

/** Byte parity and paired decode timings on ART; no wallpaper/settings changes. */
public final class MaskDecodeCheck {
    static final int BYTES=720*720*3;
    static void baseline(InputStream source,ByteBuffer pixels,int expected)throws IOException {
        pixels.clear();byte[] block=new byte[65536];int total=0;
        try(InputStream in=new InflaterInputStream(source)){
            for(int n;(n=in.read(block))!=-1;){if(total+n>expected)throw new IOException("overflow");pixels.put(block,0,n);total+=n;}
        }
        if(total!=expected)throw new IOException("incomplete");pixels.flip();
    }
    static double percentile(long[] values,double percent){long[] copy=values.clone();Arrays.sort(copy);return copy[(int)((copy.length-1)*percent)]/1e6;}
    static byte[] zip(byte[] raw)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();try(DeflaterOutputStream zip=new DeflaterOutputStream(out)){zip.write(raw);}return out.toByteArray();}
    public static void main(String[] args)throws Exception {
        File[] files=new File(args[0]).listFiles((d,n)->n.endsWith(".rgbz"));Arrays.sort(files);
        JSONObject manifest=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(args[0],"manifest.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
        int width=manifest.getInt("width"),maxBytes=width*width*3;
        if(files.length!=manifest.getInt("frame_count")+2)throw new AssertionError("mask count "+files.length);
        ByteBuffer old=ByteBuffer.allocateDirect(maxBytes),fresh=ByteBuffer.allocateDirect(maxBytes);int checks=0;
        try(MaskDecoder decoder=new MaskDecoder()){
            for(File file:files){
                int side=file.getName().equals("end.rgbz")?720:width;int expected=side*side*3;
                try(InputStream in=new FileInputStream(file)){baseline(in,old,expected);}
                try(InputStream in=new FileInputStream(file)){decoder.decode(in,fresh,expected);}
                if(!old.equals(fresh))throw new AssertionError("RGB bytes differ: "+file.getName());checks++;
            }
            byte[] small=new byte[21];new Random(1).nextBytes(small);byte[] compressed=zip(small);
            InputStream split=new ByteArrayInputStream(compressed){@Override public synchronized int read(byte[] b,int off,int length){return super.read(b,off,Math.min(1,length));}};
            decoder.decode(split,fresh,small.length);byte[] actual=new byte[small.length];fresh.get(actual);
            if(!Arrays.equals(actual,small))throw new AssertionError("split stream");checks++;
            for(byte[] broken:new byte[][]{Arrays.copyOf(compressed,compressed.length/2),zip(new byte[20]),zip(new byte[22]),new byte[]{1,2,3,4}}){
                try{decoder.decode(new ByteArrayInputStream(broken),fresh,21);throw new AssertionError("bad stream accepted");}catch(IOException expected){checks++;}
                decoder.decode(new ByteArrayInputStream(compressed),fresh,21);checks++;
            }
            int rounds=8,n=files.length*rounds;long[] before=new long[n],after=new long[n];long oldCpu=0,newCpu=0;
            for(int round=0;round<rounds;round++)for(int f=0;f<files.length;f++)for(int order=0;order<2;order++){
                int side=files[f].getName().equals("end.rgbz")?720:width;int expected=side*side*3;
                boolean legacy=((round+order)&1)==0;long cpu=Debug.threadCpuTimeNanos(),start=System.nanoTime();
                try(InputStream in=new FileInputStream(files[f])){if(legacy)baseline(in,old,expected);else decoder.decode(in,fresh,expected);}
                long wall=System.nanoTime()-start,used=Debug.threadCpuTimeNanos()-cpu;
                if(legacy){before[round*files.length+f]=wall;oldCpu+=used;}else{after[round*files.length+f]=wall;newCpu+=used;}
            }
            System.out.println("MASK_RESULT "+new JSONObject().put("style",new File(args[0]).getName()).put("buffer_bytes",maxBytes).put("checks",checks).put("decodes_per_variant",n)
                .put("before_p50_ms",percentile(before,.5)).put("after_p50_ms",percentile(after,.5))
                .put("before_p95_ms",percentile(before,.95)).put("after_p95_ms",percentile(after,.95))
                .put("before_cpu_ms",oldCpu/1e6).put("after_cpu_ms",newCpu/1e6));
        }
        System.exit(0);
    }
}
