package org.aliveclean;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/** Immutable, digest-addressed original runtimes in the current host's private code cache. */
final class NativeClockRuntime {
    static synchronized File unpack(Context host,String name)throws Exception{
        if(!"hyperos".equals(name))throw new IllegalArgumentException("Unknown original clock runtime");
        String path="native-clock/runtime/"+name+"/";
        String digest;
        try(InputStream in=host.getAssets().open(path+"runtime.sha256")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[128];
            for(int n;(n=in.read(chunk))!=-1;){
                bytes.write(chunk,0,n);
                if(bytes.size()>128)throw new IOException("Invalid clock runtime digest");
            }
            digest=bytes.toString("US-ASCII").trim();
        }
        return unpackFile(host,path+"runtime.apk",digest,"original-clock-"+name,".apk");
    }
    static synchronized File globalFont(Context host)throws Exception{
        return unpackFile(host,"native-clock/runtime/hyperos/fonts/MiSansVF.ttf",
                "0ddef90648998900175cfdca9a6f087a2544c182f130b0ad4f7e94a03a115e79",
                "original-clock-hyperos",".ttf");
    }
    private static File unpackFile(Context host,String asset,String digest,String directory,String suffix)throws Exception{
        if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid clock runtime digest");
        File folder=new File(host.getCodeCacheDir(),directory);
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Clock runtime cache unavailable");
        File target=new File(folder,digest+suffix);
        if(target.isFile()&&!target.canWrite())return target;
        File temporary=File.createTempFile("runtime-",".tmp",folder);
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            try(InputStream in=host.getAssets().open(asset);FileOutputStream out=new FileOutputStream(temporary)){
                // Android 14+ requires code to be read-only before writing
                // through this already-open descriptor.
                if(!temporary.setReadOnly())throw new IOException("Cannot protect original clock code");
                byte[] chunk=new byte[65536];
                for(int n;(n=in.read(chunk))!=-1;){out.write(chunk,0,n);hash.update(chunk,0,n);}
                out.getFD().sync();
            }
            StringBuilder actual=new StringBuilder();
            for(byte b:hash.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!digest.contentEquals(actual))throw new IOException("Clock runtime checksum mismatch");
            if(!temporary.renameTo(target))throw new IOException("Cannot store original clock runtime");
            return target;
        }finally{if(temporary.exists())temporary.delete();}
    }
    private NativeClockRuntime(){}
}
