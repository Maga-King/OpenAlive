package org.aliveclean;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

/** Process-lifetime capability receipt; never persists readiness across a reboot. */
public final class NativeClockStatusProvider extends ContentProvider {
    private IBinder renderer;
    private int api;
    private final Binder lifetime=new Binder();
    @Override public boolean onCreate(){return true;}
    @Override public synchronized void dump(java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        // Explicit dumpsys only; readiness stays in memory and cannot survive
        // a dead SystemUI process or a reboot as a stale persisted flag.
        out.println("OpenAliveClock api="+(renderer!=null&&renderer.isBinderAlive()?api:0));
        if(renderer==null||!renderer.isBinderAlive())return;
        android.os.Parcel data=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();
        try{
            data.writeInterfaceToken(NativeClockLoadState.DESCRIPTOR);
            if(!renderer.transact(NativeClockLoadState.TRANSACTION_SNAPSHOT,data,reply,0))return;
            reply.readException();Bundle state=reply.readBundle();
            if(state!=null){
                out.println("providers="+state.getInt("providers")+" requests="+state.getInt("requests")
                        +" custom="+state.getInt("customRequests")+" created="+state.getInt("created"));
                out.println("lastRequest="+state.getString("lastRequest"));
                out.println("lastEntry="+state.getString("lastEntry"));
                out.println("materialSeeds="+state.getInt("materialSeeds"));
                out.println("nativeMaterial="+state.getString("nativeMaterial"));
                out.println("firstFailure="+state.getString("firstFailure"));
            }
        }catch(Exception unavailable){out.println("loadSnapshot="+unavailable.getClass().getSimpleName());}
        finally{data.recycle();reply.recycle();}
    }
    private boolean caller(String name){
        String[] packages=getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if(packages!=null)for(String value:packages)if(name.equals(value))return true;
        return false;
    }
    @Override public synchronized Bundle call(String method,String argument,Bundle extras){
        if("announce".equals(method)){
            if(!caller("com.android.systemui"))throw new SecurityException("SystemUI renderer required");
            IBinder owner=extras==null?null:extras.getBinder("owner");
            int version=extras==null?0:extras.getInt("api");
            if(owner==null||!owner.isBinderAlive()||version!=NativeClockAvailability.API)throw new IllegalArgumentException("Invalid clock runtime");
            try{
                if(owner!=renderer)owner.linkToDeath(()->{synchronized(NativeClockStatusProvider.this){if(renderer==owner){renderer=null;api=0;}}},0);
            }catch(android.os.RemoteException dead){throw new IllegalStateException("Clock runtime exited",dead);}
            renderer=owner;api=version;
            Bundle reply=new Bundle();reply.putBinder("lifetime",lifetime);return reply;
        }
        if(!"status".equals(method))throw new IllegalArgumentException("Unknown clock runtime operation");
        if(Binder.getCallingUid()!=android.os.Process.myUid()&&!caller("com.oplus.wallpapers")&&!caller("com.android.systemui"))
            throw new SecurityException("Clock editor required");
        Bundle reply=new Bundle();reply.putInt("api",renderer!=null&&renderer.isBinderAlive()?api:0);return reply;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
