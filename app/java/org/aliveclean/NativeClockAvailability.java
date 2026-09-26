package org.aliveclean;

import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

/** Refuse new editor choices while SystemUI still has an older module loaded. */
final class NativeClockAvailability {
    // Version 2 adds the complete independent HyperOS catalog. An older
    // four-style SystemUI factory cannot accept those new persisted IDs.
    static final int API=2;
    private static final Uri URI=Uri.parse("content://org.aliveclean.clock.runtime");
    private static final Binder OWNER=new Binder(){
        @Override protected boolean onTransact(int code,android.os.Parcel data,android.os.Parcel reply,int flags)throws android.os.RemoteException{
            if(code!=NativeClockLoadState.TRANSACTION_SNAPSHOT)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(NativeClockLoadState.DESCRIPTOR);
            reply.writeNoException();reply.writeBundle(NativeClockLoadState.snapshot());return true;
        }
    };
    private static IBinder receipt;
    private static boolean connecting;
    private static int attempts;
    private static boolean missingAuthorityReported;
    private static android.content.BroadcastReceiver unlockReceiver;
    private static boolean retryAfterUnlock;
    private static final java.util.concurrent.ScheduledThreadPoolExecutor RETRIES=
            new java.util.concurrent.ScheduledThreadPoolExecutor(1,task->{
                Thread thread=new Thread(task,"OpenAlive-clock-retry");
                thread.setDaemon(true);
                return thread;
            });
    static{
        RETRIES.setKeepAliveTime(30,java.util.concurrent.TimeUnit.SECONDS);
        RETRIES.allowCoreThreadTimeOut(true);
    }
    private static volatile String unavailableMessage="尚未连接系统时钟模块，请检查模块作用域和加载状态";
    static String unavailableMessage(){return unavailableMessage;}

    private static void watchFirstUnlock(Context app){
        android.os.UserManager users=app.getSystemService(android.os.UserManager.class);
        if(unlockReceiver!=null||users==null||users.isUserUnlocked())return;
        unlockReceiver=new android.content.BroadcastReceiver(){
            @Override public void onReceive(Context context,android.content.Intent intent){
                if(!android.content.Intent.ACTION_USER_UNLOCKED.equals(intent.getAction()))return;
                synchronized(NativeClockAvailability.class){
                    if(unlockReceiver!=this)return;
                    app.unregisterReceiver(this);unlockReceiver=null;attempts=0;
                    // An unlock may arrive while the pre-unlock attempt is
                    // failing. Its finally block must not lose this retry.
                    if(connecting){retryAfterUnlock=true;return;}
                }
                announce(app);
            }
        };
        android.content.IntentFilter filter=new android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED);
        if(android.os.Build.VERSION.SDK_INT>=33)app.registerReceiver(unlockReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(unlockReceiver,filter);
        // Close the check/register race; the initial attempt proceeds below.
        if(users.isUserUnlocked()){app.unregisterReceiver(unlockReceiver);unlockReceiver=null;}
    }

    static synchronized void announce(Context host){
        Context application=host.getApplicationContext();
        Context app=application==null?host:application;
        // Native clock containers can pass a package/themed wrapper. The owner
        // of the process, not that wrapper's package, performs registration.
        if(!"com.android.systemui".equals(app.getPackageName()))return;
        if(connecting){NativeClockLoadState.registration("Registration already running");return;}
        if(receipt!=null&&receipt.isBinderAlive())return;
        watchFirstUnlock(app);
        connecting=true;
        Thread worker=new Thread(()->{
            boolean retry=false;
            try{
                Context assets=app.createPackageContext("org.aliveclean",0);
                // Copy and verify original code away from SystemUI's main thread,
                // before advertising that the new renderer can be selected.
                NativeClockRuntime.unpack(NativeClockProvider.resourceContext(app,assets.getAssets()),"hyperos");
                NativeClockRuntime.globalFont(NativeClockProvider.resourceContext(app,assets.getAssets()));
                Bundle request=new Bundle();request.putInt("api",API);request.putBinder("owner",OWNER);
                Bundle reply=app.getContentResolver().call(URI,"announce",null,request);
                IBinder token=reply==null?null:reply.getBinder("lifetime");
                if(token==null)throw new IllegalStateException("No clock capability receipt");
                synchronized(NativeClockAvailability.class){receipt=token;attempts=0;}
                NativeClockLoadState.registration("API "+API+" registered");
                NativeClockLoadState.startup("Clock runtime registered, API "+API);
                token.linkToDeath(()->{
                    synchronized(NativeClockAvailability.class){if(receipt!=token)return;receipt=null;}
                    // Death can arrive before this connection attempt leaves
                    // its finally block. Queue a retry instead of dropping it
                    // while connecting is still true.
                    RETRIES.schedule(()->announce(app),1000,java.util.concurrent.TimeUnit.MILLISECONDS);
                },0);
            }catch(Exception unavailable){
                NativeClockLoadState.failure("Clock runtime registration",unavailable);
                NativeClockLoadState.registration(unavailable.getClass().getName()+": "+unavailable.getMessage());
                boolean missingAuthority=unavailable instanceof IllegalArgumentException
                        &&unavailable.getMessage()!=null
                        &&unavailable.getMessage().startsWith("Unknown authority ");
                synchronized(NativeClockAvailability.class){
                    receipt=null;
                    attempts=Math.min(1000,attempts+1);
                    // Package providers can appear after SystemUI starts. Keep
                    // retrying only this transient lookup failure; other errors
                    // retain the three-attempt limit and explicit failure state.
                    retry=attempts<3||missingAuthority;
                    if(missingAuthority&&attempts>=3&&!missingAuthorityReported){
                        missingAuthorityReported=true;
                        android.util.Log.w("OpenAliveClock","Clock provider unavailable during startup; retrying",unavailable);
                    }else if(!retry){
                        android.util.Log.w("OpenAliveClock","Clock runtime registration failed",unavailable);
                    }
                    NativeClockLoadState.registration(unavailable.getClass().getSimpleName()+": "+unavailable.getMessage()
                            +" attempts="+attempts+" retry="+retry);
                }
            }
            finally{synchronized(NativeClockAvailability.class){
                connecting=false;
                if(retryAfterUnlock){retryAfterUnlock=false;attempts=0;retry=true;}
            }}
            if(retry){
                int failed;
                synchronized(NativeClockAvailability.class){failed=attempts;}
                long delay=failed<3?1000:Math.min(30000L,1000L<<(Math.min(5,failed-2)));
                NativeClockLoadState.registration("Retry scheduled after "+delay+" ms, attempts="+failed);
                RETRIES.schedule(()->{
                    NativeClockLoadState.registration("Retry fired, attempts="+failed);
                    announce(app);
                },delay,java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        },"OpenAlive-clock-runtime");
        worker.start();
    }
    static boolean ready(Context host){
        // Standalone instrumentation hosts have no connection to persisted clocks.
        String pkg=host.getPackageName();
        if(!"com.oplus.wallpapers".equals(pkg)&&!"com.android.systemui".equals(pkg))return true;
        Context application=host.getApplicationContext();
        Context caller=application==null?host:application;
        try{
            Bundle reply=caller.getContentResolver().call(URI,"status",null,null);
            int api=reply==null?0:reply.getInt("api");
            unavailableMessage=api>0&&api!=API?"系统仍在使用旧版时钟模块，请正常重启手机后再选择":
                    "系统时钟模块尚未完成初始化，请检查模块加载状态";
            return api==API;
        }catch(Exception unavailable){
            unavailableMessage="无法连接 OpenAlive 时钟服务，请检查应用与模块的运行状态";
            return false;
        }
    }
    private NativeClockAvailability(){}
}
