package org.aliveclean;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import de.robv.android.xposed.*;

/** Opt-in refresh through ColorOS's panoramic show client; OFF votes stay untouched. */
final class ColorOsContinuousAod {
    private final ClassLoader loader;
    private Class<?> displayClass;
    private volatile boolean enabled;
    private volatile Object display;
    private volatile Handler worker;
    private Handler leaseWorker;
    private ColorOsAnimationLease animationLease;
    private volatile boolean wantsLease, cpuLeaseHeld;
    private volatile Context leaseContext;
    private volatile int screenRequest;
    private boolean installed;
    private PowerManager.WakeLock lease;
    private volatile long animatedVotes;
    private final Runnable renew=this::updateLease;

    ColorOsContinuousAod(ClassLoader loader){this.loader=loader;}
    void install(){
        try{
            displayClass=XposedHelpers.findClass("com.oplus.systemui.aod.display.AODDisplayUtil",loader);
            Class<?> client=XposedHelpers.findClass(displayClass.getName()+"$AODVirtualDozeClient",loader);
            XposedHelpers.findAndHookMethod(client,"getVoteState",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    if(enabled&&!p.hasThrowable()&&p.getResult() instanceof Integer)try{
                        int original=(Integer)p.getResult();
                        int vote=ContinuousAodPolicy.vote(enabled,(String)XposedHelpers.getObjectField(p.thisObject,"mReason"),
                                XposedHelpers.getIntField(p.thisObject,"mRequestState"),original);
                        if(vote!=original){p.setResult(vote);animatedVotes++;}
                    }catch(Throwable error){enabled=false;stopLease();ColorOsBridge.failure("continuous AOD vote",error);}
                }
            });
            XposedHelpers.findAndHookMethod(displayClass,"updateDisplayState",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){observe(p.thisObject);}
            });
            XposedHelpers.findAndHookMethod(displayClass,"onScreenStateChanged",int.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){observe(p.thisObject);}
            });
            // OFF can bypass the client policy (schedule, proximity, power saving).
            Class<?> base=XposedHelpers.findClass("com.oplus.systemui.aod.display.BaseDisplayUtil",loader);
            XposedHelpers.findAndHookMethod(base,"setScreenState",int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(displayClass.isInstance(p.thisObject)){
                        screenRequest=(Integer)p.args[0];
                        if(screenRequest!=3)stopLease();
                    }
                }
            });
            HandlerThread thread=new HandlerThread("OpenAliveAodLease");
            thread.start();leaseWorker=new Handler(thread.getLooper());
            animationLease=new ColorOsAnimationLease(loader);
            installed=true;
        }catch(Throwable error){enabled=false;ColorOsBridge.failure("continuous AOD hooks",error);}
        try{
            // Only printed when explicitly requested through the system dump;
            // no per-frame log or persistent recording.
            XposedHelpers.findAndHookMethod(displayClass,"dump",java.io.PrintWriter.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    ((java.io.PrintWriter)p.args[0]).println("OpenAlive continuous AOD: installed="+installed+" enabled="+enabled+" panoramicVotes="+animatedVotes+" cpuLease="+cpuLeaseHeld+" freezerLease="+(animationLease==null?"unavailable":animationLease.status()));
                }
            });
        }catch(Throwable error){ColorOsBridge.failure("continuous AOD dump",error);}
    }
    void configure(Context context,boolean active){
        if(!installed)return;
        boolean next=installed&&active;
        if(enabled==next)return;
        enabled=next;
        if(!next)stopLease();
        try{
            if(context==null)return;
            Object target=XposedHelpers.callStaticMethod(displayClass,"getInstance",context);
            display=target;worker=(Handler)XposedHelpers.getObjectField(target,"mWorkerHandler");
            // Follow the same worker ordering as ColorOS's own requestScreenState.
            worker.post(()->{
                try{
                    if((Boolean)XposedHelpers.callMethod(target,"isDreaming"))XposedHelpers.callMethod(target,"updateDisplayState");
                    observe(target);
                }catch(Throwable error){stopLease();ColorOsBridge.failure("continuous AOD state",error);}
            });
        }catch(Throwable error){enabled=false;stopLease();ColorOsBridge.failure("continuous AOD configuration",error);}
    }
    private void observe(Object target){
        if(!enabled)return;
        try{
            display=target;worker=(Handler)XposedHelpers.getObjectField(target,"mWorkerHandler");
            leaseContext=(Context)XposedHelpers.getObjectField(target,"mContext");
            wantsLease=(screenRequest==0||screenRequest==3)&&ContinuousAodPolicy.keepCpu(enabled,
                    (Boolean)XposedHelpers.callMethod(target,"isDreaming"),
                    XposedHelpers.getIntField(target,"mRequestedDisplayState"),XposedHelpers.getIntField(target,"mDeviceDisplayState"));
            scheduleLease();
        }catch(Throwable error){stopLease();ColorOsBridge.failure("continuous AOD display",error);}
    }
    private void scheduleLease(){
        Handler queue=leaseWorker;
        if(queue!=null){queue.removeCallbacks(renew);queue.post(renew);}
    }
    /** No Binder IPC or contended monitor on ColorOS's display or main thread. */
    private void updateLease(){
        try{
            Handler queue=leaseWorker;
            if(queue==null)return;
            queue.removeCallbacks(renew);
            Context context=leaseContext;
            if(!enabled||!wantsLease||context==null){releaseOnWorker();return;}
            if(lease==null){
                lease=context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OpenAlive:ContinuousAod");
                lease.setReferenceCounted(false);
            }
            // A bounded lease also expires if SystemUI's worker stops servicing callbacks.
            lease.acquire(20_000L);cpuLeaseHeld=true;
            animationLease.renew(context,20_000L);
            // An OFF/ON event may arrive while the native service is answering.
            if(!enabled||!wantsLease){releaseOnWorker();return;}
            queue.removeCallbacks(renew);queue.postDelayed(renew,10_000L);
        }catch(Throwable error){releaseOnWorker();ColorOsBridge.failure("continuous AOD lease",error);}
    }
    private void stopLease(){
        wantsLease=false;scheduleLease();
    }
    private void releaseOnWorker(){
        if(leaseWorker!=null)leaseWorker.removeCallbacks(renew);
        try{if(lease!=null&&lease.isHeld())lease.release();}
        finally{cpuLeaseHeld=false;if(animationLease!=null)animationLease.release();}
    }
}
