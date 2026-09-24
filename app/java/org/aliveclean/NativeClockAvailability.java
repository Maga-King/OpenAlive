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
    private static android.content.BroadcastReceiver unlockReceiver;
    private static boolean retryAfterUnlock;

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
        if(!"com.android.systemui".equals(host.getPackageName())||connecting||(receipt!=null&&receipt.isBinderAlive()))return;
        Context application=host.getApplicationContext();
        Context app=application==null?host:application;
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
                token.linkToDeath(()->{
                    synchronized(NativeClockAvailability.class){if(receipt!=token)return;receipt=null;}
                    // Death can arrive before this connection attempt leaves
                    // its finally block. Queue a retry instead of dropping it
                    // while connecting is still true.
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->announce(app),1000);
                },0);
            }catch(Exception unavailable){
                synchronized(NativeClockAvailability.class){receipt=null;retry=++attempts<3;}
                if(!retry)android.util.Log.w("OpenAliveClock","Clock runtime registration failed",unavailable);
            }
            finally{synchronized(NativeClockAvailability.class){
                connecting=false;
                if(retryAfterUnlock){retryAfterUnlock=false;attempts=0;retry=true;}
            }}
            if(retry)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->announce(app),1000);
        },"OpenAlive-clock-runtime");
        worker.start();
    }
    static boolean ready(Context host){
        // Standalone instrumentation hosts have no connection to persisted clocks.
        String pkg=host.getPackageName();
        if(!"com.oplus.wallpapers".equals(pkg)&&!"com.android.systemui".equals(pkg))return true;
        try{Bundle reply=host.getContentResolver().call(URI,"status",null,null);return reply!=null&&reply.getInt("api")==API;}
        catch(Exception unavailable){return false;}
    }
    private NativeClockAvailability(){}
}
