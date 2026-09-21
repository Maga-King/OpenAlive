package org.aliveclean;

import android.app.ActivityManager;
import android.content.AttributionSource;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** External provider lease for the short-lived system-UID adapter (Android 12+).
 * An app_process main is not an AMS-registered application: using its ordinary
 * ContentResolver would send an unregistered IApplicationThread. The platform's
 * content command instead acquires and releases an external Binder-token lease.
 */
final class PlatformProvider implements AutoCloseable {
    private final String authority;
    private final int user;
    private final IBinder token=new Binder();
    private final Object manager,provider;
    private final Method release,call;
    private final AttributionSource source;
    private boolean closed;

    PlatformProvider(String authority,int user)throws Exception {
        if(android.os.Process.myUid()!=1000)throw new SecurityException("Requires system UID");
        this.authority=authority;this.user=user;
        Class<?> activityManager=Class.forName("android.app.IActivityManager");
        manager=invoke(ActivityManager.class.getDeclaredMethod("getService"),null);
        release=activityManager.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class);
        call=Class.forName("android.content.IContentProvider").getMethod("call",AttributionSource.class,String.class,String.class,String.class,Bundle.class);
        source=new AttributionSource.Builder(1000).setPackageName("android").build();
        Object holder=invoke(activityManager.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class),manager,authority,user,token,"AliveClean");
        if(holder==null)throw new IllegalStateException("Provider unavailable: "+authority);
        try{
            provider=holder.getClass().getField("provider").get(holder);
            if(provider==null)throw new IllegalStateException("Provider binder unavailable: "+authority);
        }catch(Exception error){
            try{invoke(release,manager,authority,token,user);}catch(Exception cleanup){error.addSuppressed(cleanup);}
            throw error;
        }
    }
    Bundle call(String method,String argument,Bundle extras)throws Exception {
        if(closed)throw new IllegalStateException("Provider lease is closed");
        return (Bundle)invoke(call,provider,source,authority,method,argument,extras);
    }
    int secureInt(String key,int fallback)throws Exception {
        if(!"settings".equals(authority))throw new IllegalStateException("Not the settings provider");
        Bundle extras=new Bundle();extras.putInt("_user",user);
        Bundle result=call("GET_secure",key,extras);
        if(result==null)throw new IllegalStateException("Empty settings response: "+key);
        String value=result.getString("value");
        if(value==null)return fallback;
        try{return Integer.parseInt(value);}catch(NumberFormatException error){throw new IllegalStateException("Invalid integer setting: "+key,error);}
    }
    @Override public void close()throws Exception {
        if(!closed){closed=true;invoke(release,manager,authority,token,user);}
    }
    private static Object invoke(Method method,Object receiver,Object...args)throws Exception {
        try{return method.invoke(receiver,args);}
        catch(InvocationTargetException wrapped){
            Throwable cause=wrapped.getCause();
            if(cause instanceof Exception)throw (Exception)cause;
            if(cause instanceof Error)throw (Error)cause;
            throw wrapped;
        }
    }
}
