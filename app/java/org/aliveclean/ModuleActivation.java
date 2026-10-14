package org.aliveclean;

import android.content.*;
import android.net.Uri;
import android.os.*;

/** A live SystemUI module receipt, separate from optional clock capabilities. */
final class ModuleActivation {
    static final String QUERY="org.aliveclean.QUERY_MODULE_ACTIVATION";
    private static final Uri STATUS=Uri.parse("content://org.aliveclean.clock.runtime");
    private static final Binder OWNER=new Binder();
    private static final java.util.concurrent.atomic.AtomicBoolean announcing=new java.util.concurrent.atomic.AtomicBoolean();
    static void announce(Context context){
        if(!announcing.compareAndSet(false,true))return;
        new Thread(()->{
            try{
                Bundle data=new Bundle();data.putBinder("owner",OWNER);
                context.getContentResolver().call(STATUS,"hookAnnounce",null,data);
            }catch(RuntimeException unavailable){
                // The app may not be allowed to start during boot. The visible
                // home page requests a fresh receipt; no persisted ready flag.
            }finally{announcing.set(false);}
        },"OpenAliveModuleReceipt").start();
    }

    static void request(Context context){
        try{context.sendBroadcast(new Intent(QUERY).setPackage("com.android.systemui"));}
        catch(RuntimeException unavailable){android.util.Log.w("OpenAliveModule","SystemUI activation query unavailable",unavailable);}
    }
    static boolean active(Context context){
        try{
            Bundle state=context.getContentResolver().call(STATUS,"hookStatus",null,null);
            return state!=null&&state.getBoolean("active");
        }catch(RuntimeException unavailable){return false;}
    }
    private ModuleActivation(){}
}
