package org.aliveclean;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Method;

/** A short, package-specific Hans lease. Called only on our background worker. */
final class ColorOsAnimationLease {
    private final ClassLoader loader;
    private Object service;
    private Method request, cancel;
    private int uid=-1;
    private boolean pending, rejected;
    private volatile String status="idle";
    private volatile long generation;

    ColorOsAnimationLease(ClassLoader loader){this.loader=loader;}
    String status(){return status;}

    void renew(Context context,long durationMs){
        if(rejected)return;
        try{
            if(service==null){
                // Resolve the installed application's UID, including its Android user.
                uid=context.getPackageManager().getApplicationInfo("org.aliveclean",0).uid;
                Class<?> manager=Class.forName("android.os.ServiceManager",false,loader);
                IBinder binder=(IBinder)manager.getMethod("getService",String.class).invoke(null,"oplus_freeze");
                if(binder==null){status="unavailable";rejected=true;return;}
                Class<?> api=Class.forName("com.oplus.app.IOplusHansFreezeManager",false,loader);
                Class<?> stub=Class.forName(api.getName()+"$Stub",false,loader);
                service=stub.getMethod("asInterface",IBinder.class).invoke(null,binder);
                Class<?> callback=Class.forName("com.oplus.app.IOplusProtectConnection",false,loader);
                request=api.getMethod("requestFrozenDelay",int.class,String.class,long.class,String.class,callback);
                cancel=api.getMethod("cancelFrozenDelay",int.class);
            }
            long token=++generation;
            Class<?> callbackStub=Class.forName("com.oplus.app.IOplusProtectConnection$Stub",false,loader);
            Method names=callbackStub.getMethod("getDefaultTransactionName",int.class);
            String descriptor=(String)Class.forName("com.oplus.app.IOplusProtectConnection",false,loader).getField("DESCRIPTOR").get(null);
            Binder connection=new Binder(){
                @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
                    if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(descriptor);return true;}
                    try{
                        String name=(String)names.invoke(null,code);
                        if(name==null)return super.onTransact(code,data,reply,flags);
                        data.enforceInterface(descriptor);
                        String result;
                        if("onSuccess".equals(name))result="granted";
                        else if("onError".equals(name))result="denied:"+data.readInt();
                        else if("onTimeout".equals(name))result="expired";
                        else return super.onTransact(code,data,reply,flags);
                        // Callback threads never make Binder calls or acquire UI locks.
                        if(token==generation)status=result;
                        return true;
                    }catch(ReflectiveOperationException error){return false;}
                }
            };
            Object callback=callbackStub.getMethod("asInterface",IBinder.class).invoke(null,connection);
            // Nonzero duration ensures process death or a stalled worker cannot leave
            // a permanent exemption. Native callback reports permission/config denial.
            if(status.startsWith("denied:")){rejected=true;return;}
            status="requested";pending=true;
            request.invoke(service,uid,"org.aliveclean",durationMs,"OpenAlive continuous AOD",callback);
        }catch(Throwable error){status="unavailable:"+error.getClass().getSimpleName();rejected=true;}
    }

    void release(){
        ++generation;
        if(pending&&service!=null&&uid>=0){
            try{cancel.invoke(service,uid);status="released";}
            catch(Throwable error){status="release-pending-expiry";}
        }
        pending=false;rejected=false;
    }
}
