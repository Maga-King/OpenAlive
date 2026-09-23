package org.aliveclean;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

/** Exercises lease callbacks/cancellation with a local Binder, never the device freezer. */
public final class HansLeaseInstrumentation extends Instrumentation {
    public static final class Service {
        int requests,cancels,uid;long duration;String pkg;IBinder callback;
        public void requestFrozenDelay(int u,String p,long d,String reason,IBinder c){requests++;uid=u;pkg=p;duration=d;callback=c;}
        public void cancelFrozenDelay(int u){if(u!=uid)throw new AssertionError("different UID cancelled");cancels++;}
    }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            ColorOsAnimationLease lease=new ColorOsAnimationLease(getClassLoader());
            Service service=new Service();
            field(lease,"service",service);field(lease,"uid",10595);
            field(lease,"request",Service.class.getMethod("requestFrozenDelay",int.class,String.class,long.class,String.class,IBinder.class));
            field(lease,"cancel",Service.class.getMethod("cancelFrozenDelay",int.class));
            lease.renew(null,20_000L);
            check(service.requests==1&&service.uid==10595&&"org.aliveclean".equals(service.pkg)&&service.duration==20_000L,"lease target or duration");
            IBinder old=service.callback;callback(old,41,0);check("granted".equals(lease.status()),"grant callback");
            lease.renew(null,20_000L);callback(old,42,1);check("requested".equals(lease.status()),"stale denial changed new lease");
            callback(service.callback,43,0);check("expired".equals(lease.status()),"timeout callback");
            lease.renew(null,20_000L);callback(service.callback,42,1);check("denied:1".equals(lease.status()),"denial callback");
            int count=service.requests;lease.renew(null,20_000L);lease.renew(null,20_000L);
            check(service.requests==count,"denied request retried without release");
            old=service.callback;lease.release();check(service.cancels==1,"release missing");
            callback(old,41,0);check("released".equals(lease.status()),"late grant revived released lease");
            lease.release();check(service.cancels==1,"release not idempotent");
            lease.renew(null,20_000L);check(service.requests==count+1,"next AOD cycle did not retry");lease.release();
            result.putString("stream","HANS_LEASE_OK target duration grant timeout denial stale-callback release retry");finish(-1,result);
        }catch(Throwable error){result.putString("stream",error.toString());finish(1,result);}
    }
    private ClassLoader getClassLoader(){return getTargetContext().getClassLoader();}
    private static void field(Object target,String name,Object value)throws Exception{
        java.lang.reflect.Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private static void callback(IBinder binder,int code,int error)throws Exception{
        Parcel data=Parcel.obtain();try{data.writeInterfaceToken("com.oplus.app.IOplusProtectConnection");if(code==42)data.writeInt(error);binder.transact(code,data,null,0);}finally{data.recycle();}
    }
}
