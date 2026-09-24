package org.aliveclean;

import android.app.Instrumentation;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated boot-order fault injection; never changes credentials or the live clock. */
public final class NativeClockBootInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context real=getTargetContext();
            AtomicBoolean unlocked=new AtomicBoolean(false);
            AtomicInteger calls=new AtomicInteger();
            CountDownLatch exhausted=new CountDownLatch(3),recovered=new CountDownLatch(1);
            Binder lifetime=new Binder();
            NativeClockLoadState.request("test.original.clock","test bridge",true);
            NativeClockLoadState.failure("test retained failure",new IllegalStateException("test cause"));
            ContentResolver resolver=ContentResolver.wrap(new ContentProvider(){
                public boolean onCreate(){return true;}
                public Bundle call(String method,String arg,Bundle extras){
                    if("announce".equals(method)){
                        Parcel request=Parcel.obtain(),response=Parcel.obtain();
                        try{
                            request.writeInterfaceToken(NativeClockLoadState.DESCRIPTOR);
                            IBinder owner=extras.getBinder("owner");
                            if(!owner.transact(NativeClockLoadState.TRANSACTION_SNAPSHOT,request,response,0))throw new AssertionError("No load snapshot transport");
                            response.readException();Bundle state=response.readBundle();
                            if(state.getInt("customRequests")!=1||!state.getString("firstFailure").contains("test cause"))throw new AssertionError("Lost process load state");
                        }catch(RemoteException failure){throw new AssertionError(failure);}
                        finally{request.recycle();response.recycle();}
                        calls.incrementAndGet();
                        if(!unlocked.get()){exhausted.countDown();throw new IllegalStateException("Simulated early-boot service unavailable");}
                        if(extras.getInt("api")!=NativeClockAvailability.API||!extras.getBinder("owner").isBinderAlive())
                            throw new AssertionError("Not a live versioned renderer");
                        Bundle reply=new Bundle();reply.putBinder("lifetime",lifetime);recovered.countDown();return reply;
                    }
                    Bundle reply=new Bundle();reply.putInt("api",recovered.getCount()==0?NativeClockAvailability.API:0);return reply;
                }
                public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
                public String getType(Uri u){return null;}
                public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
                public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
                public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
            });
            Class<?> service=Class.forName("android.os.IUserManager");
            Object users=Proxy.newProxyInstance(service.getClassLoader(),new Class<?>[]{service},(proxy,method,args)->{
                if(method.getName().equals("isUserUnlocked"))return unlocked.get();
                if(method.getName().equals("asBinder"))return new Binder();
                throw new AssertionError("Unexpected user-service operation "+method);
            });
            UserManager userManager=(UserManager)UserManager.class.getConstructor(Context.class,service).newInstance(real,users);
            // The real system invalidates this cache when it unlocks the user.
            // This test swaps a local service result without unlocking a device,
            // so disable only this disposable process's cache.
            Class.forName("android.app.PropertyInvalidatedCache").getMethod("disableForTestMode").invoke(null);
            BroadcastReceiver[] listener={null};
            Context host=new ContextWrapper(real){
                @Override public String getPackageName(){return "com.android.systemui";}
                @Override public Context getApplicationContext(){return this;}
                @Override public Context createPackageContext(String name,int flags){return real;}
                @Override public Object getSystemService(String name){return USER_SERVICE.equals(name)?userManager:super.getSystemService(name);}
                @Override public ContentResolver getContentResolver(){return resolver;}
                @Override public java.io.File getCodeCacheDir(){throw new AssertionError("Credential-encrypted cache accessed before unlock");}
                @Override public boolean isDeviceProtectedStorage(){return false;}
                @Override public Context createDeviceProtectedStorageContext(){return real.createDeviceProtectedStorageContext();}
                @Override public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){return registerReceiver(r,f,0);}
                @Override public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){
                    if(!f.hasAction(Intent.ACTION_USER_UNLOCKED)||listener[0]!=null)throw new AssertionError("Duplicate/missing unlock subscription");
                    listener[0]=r;return null;
                }
                @Override public void unregisterReceiver(BroadcastReceiver r){if(listener[0]!=r)throw new AssertionError("Wrong receiver");listener[0]=null;}
            };
            Context resources=NativeClockProvider.resourceContext(host,real.getAssets());
            String cache=NativeClockRuntime.unpack(resources,"hyperos").getCanonicalPath();
            String deviceCache=real.createDeviceProtectedStorageContext().getCodeCacheDir().getCanonicalPath();
            if(!cache.startsWith(deviceCache+java.io.File.separator))throw new AssertionError("Runtime escaped DE cache: "+cache);
            try(OfficialUi flyme=new OfficialUi(resources)){
                if(flyme.id("layout","layout_distinctive_self")==0)throw new AssertionError("Flyme resource missing");
            }
            NativeClockAvailability.announce(host);
            if(!exhausted.await(12,TimeUnit.SECONDS))throw new AssertionError("Early-boot failure sequence not exercised");
            if(listener[0]==null||NativeClockAvailability.ready(host))throw new AssertionError("Lost unlock callback or advertised stale readiness");
            unlocked.set(true);
            BroadcastReceiver unlock=listener[0];
            runOnMainSync(()->unlock.onReceive(host,new Intent(Intent.ACTION_USER_UNLOCKED)));
            if(!recovered.await(8,TimeUnit.SECONDS))throw new AssertionError("No recovery after unlock");
            // Wait until announce's finally block publishes completion.
            long deadline=SystemClock.uptimeMillis()+2000;
            while(SystemClock.uptimeMillis()<deadline){
                synchronized(NativeClockAvailability.class){
                    java.lang.reflect.Field running=NativeClockAvailability.class.getDeclaredField("connecting");running.setAccessible(true);
                    if(!running.getBoolean(null))break;
                }
                SystemClock.sleep(10);
            }
            int completed=calls.get();NativeClockAvailability.announce(host);
            if(listener[0]!=null||!NativeClockAvailability.ready(host)||calls.get()!=completed)
                throw new AssertionError("Recovery mismatch: listener="+(listener[0]!=null)+" ready="+NativeClockAvailability.ready(host)+" calls="+calls.get()+" expected="+completed+" unlocked="+userManager.isUserUnlocked());
            result.putString("stream","NATIVE_CLOCK_BOOT_OK de_cache=true flyme_resources=true early_failures=3 unlock_recovered=true live_receipt=true\n");
            finish(-1,result);
        }catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));finish(0,result);}
    }
}
