package org.aliveclean;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.*;

/** Worker-owned zlib state and scratch buffers; output bytes are unchanged. */
final class MaskDecoder implements AutoCloseable {
    private final Inflater inflater=new Inflater();
    private final byte[] input=new byte[32768],output=new byte[65536];
    void decode(InputStream source,ByteBuffer pixels,int expected)throws IOException {
        inflater.reset();pixels.clear();int total=0;
        try{
            while(!inflater.finished()){
                if(inflater.needsInput()){
                    int n=source.read(input);if(n<0)throw new EOFException("Incomplete mask stream");
                    if(n==0)continue;inflater.setInput(input,0,n);
                }
                int n=inflater.inflate(output);
                if(total+n>expected)throw new IOException("Mask size overflow");
                pixels.put(output,0,n);total+=n;
                if(n==0&&!inflater.finished()&&!inflater.needsInput())throw new IOException("Invalid mask stream");
            }
            if(total!=expected)throw new IOException("Incomplete mask");pixels.flip();
        }catch(DataFormatException error){throw new IOException("Invalid mask data",error);}
        finally{inflater.reset();}
    }
    @Override public void close(){inflater.end();}
}
