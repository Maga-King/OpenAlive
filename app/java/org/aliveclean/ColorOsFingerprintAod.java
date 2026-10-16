package org.aliveclean;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.animation.LinearInterpolator;
import de.robv.android.xposed.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Reuse the native single-tap AOD entry before a black-screen fingerprint wake. */
final class ColorOsFingerprintAod {
    private final ClassLoader loader;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadLocal<Object> showing=new ThreadLocal<>();
    private final Consumer<Boolean> clockSuppression;
    private final FingerprintClockAttempt clockAttempt=new FingerprintClockAttempt();
    private final Set<Class<?>> gestureHooks=new HashSet<>();
    private boolean clockHooksReady;
    private volatile long clockAttempts;
    private Runnable clockTimeout,clockLift;
    private volatile long requestedAt;
    ColorOsFingerprintAod(ClassLoader loader,Consumer<Boolean> clockSuppression){this.loader=loader;this.clockSuppression=clockSuppression;}
    void install(){
        try{
            installClockLifetime();
            Class<?> finger=XposedHelpers.findClass("com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintUiMech",loader);
            XposedHelpers.findAndHookMethod(finger,"onFpTouch",boolean.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(!(boolean)p.args[0]){onMain(ColorOsFingerprintAod.this::fingerLifted);return;}
                    if(!ColorOsBridge.ownsWallpaper())return;
                    try{
                        if(XposedHelpers.getBooleanField(p.thisObject,"isTouchDownNow")
                                ||!XposedHelpers.getBooleanField(p.thisObject,"isFingerprintIconShow"))return;
                        Context context=(Context)XposedHelpers.getObjectField(p.thisObject,"context");
                        Object display=instance("com.oplus.systemui.aod.display.AODDisplayUtil",context);
                        if((int)XposedHelpers.callMethod(display,"getDeviceDisplayState")!=1
                                ||!(boolean)XposedHelpers.callMethod(display,"isDreaming"))return;
                        Object data=instance("com.oplus.systemui.aod.aodclock.constant.AodData",context);
                        boolean clockWasHidden=!XposedHelpers.getBooleanField(data,"mAodIsInShow");
                        Object monitor=XposedHelpers.getObjectField(p.thisObject,"keyguardUpdateMonitor");
                        onMain(()->{
                            // Snapshot OFF before the native touch handler can wake AOD.
                            // A proximity-triggered AOD show can win the race with our callback.
                            if(clockWasHidden&&clockHooksReady&&ColorOsBridge.ownsWallpaper()
                                    &&XposedHelpers.getBooleanField(monitor,"mKeyguardShowing")
                                    &&!XposedHelpers.getBooleanField(monitor,"mKeyguardGoingAway"))beginClockAttempt();
                            show(context,display,monitor);
                        });
                    }catch(Throwable error){ColorOsBridge.failure("fingerprint AOD prepare",error);}
                }
            });
            Class<?> panoramic=XposedHelpers.findClass("com.oplus.systemui.aod.controller.PanoramicAodController",loader);
            XposedHelpers.findAndHookMethod(panoramic,"startPanoramicAodShowAnim",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    long age=SystemClock.uptimeMillis()-requestedAt;
                    if(ColorOsBridge.ownsWallpaper()&&requestedAt!=0&&age>=0&&age<600)showing.set(p.thisObject);
                }
                @Override protected void afterHookedMethod(MethodHookParam p){showing.remove();}
            });
            // Only the exact native mask animator created in the scoped call.
            // Keep its listeners, screen votes and cancellation-on-wake behavior.
            XposedHelpers.findAndHookMethod(ValueAnimator.class,"start",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    Object controller=showing.get();if(controller==null)return;
                    try{
                        if(XposedHelpers.getObjectField(controller,"runningAppearAnimation")!=p.thisObject)return;
                        ValueAnimator animator=(ValueAnimator)p.thisObject;
                        animator.setStartDelay(0);animator.setDuration(160);
                        animator.setInterpolator(new LinearInterpolator());requestedAt=0;
                    }catch(Throwable error){ColorOsBridge.failure("fingerprint AOD fade",error);}
                }
            });
        }catch(Throwable error){ColorOsBridge.failure("fingerprint AOD hooks",error);}
    }
    private void installClockLifetime(){
        try{
            Class<?> auth=XposedHelpers.findClass("com.oplus.keyguard.OplusKeyguardUpdateMonitorExImpl",loader);
            XC_MethodHook failed=new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){onMain(()->cancelClockBeforeUnlock(clockAttempt.token()));}
            };
            XposedHelpers.findAndHookMethod(auth,"onFingerprintFingerAuthenticationFailed",failed);
            XposedHelpers.findAndHookMethod(auth,"onFingerprintAuthenticationError",failed);
            XposedHelpers.findAndHookMethod("com.android.keyguard.KeyguardUpdateMonitor",loader,"setKeyguardGoingAway",boolean.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    if(!p.hasThrowable()&&(boolean)p.args[0])onMain(()->{
                        if(!clockAttempt.active())return;
                        clockAttempt.accepted();armClockTimeout(2000);
                    });
                }
            });
            Class<?> mediator=XposedHelpers.findClass("com.oplus.systemui.keyguard.OplusKeyguardViewMediatorExImpl",loader);
            XC_MethodHook clear=new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){onMain(ColorOsFingerprintAod.this::clearClockAttempt);}
            };
            XposedHelpers.findAndHookMethod(mediator,"onStartedGoingToSleep",int.class,clear);
            // Clear after the native completion, once the lockscreen is gone.
            XposedHelpers.findAndHookMethod(mediator,"keyguardGone",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable())onMain(ColorOsFingerprintAod.this::clearClockAttempt);}
            });
            Class<?> core=XposedHelpers.findClass("com.android.systemui.keyguard.KeyguardViewMediator",loader);
            XposedHelpers.findAndHookMethod(core,"handleBeforeUserSwitching",int.class,Runnable.class,clear);
            XposedHelpers.findAndHookMethod(core,"dump",java.io.PrintWriter.class,String[].class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    ((java.io.PrintWriter)p.args[0]).println("OpenAlive fingerprint clock: installed="+clockHooksReady
                            +" attempts="+clockAttempts+" suppressed="+clockAttempt.active());
                }
            });
            // Install before any touch: native proximity can show AOD before show()
            // gets to inspect its callback. Both installed-ROM tap implementations
            // receive a real event, unlike our null-event prewake call.
            for(String type:new String[]{"com.oplus.systemui.aod.display.OplusWakeUpController$AodSingleClickWakeUpCallback",
                    "com.oplus.systemui.aod.scene.AodViewSingleClickWakeUpHolder$AodSingleClickWakeUpCallback"}){
                Class<?> callback=XposedHelpers.findClassIfExists(type,loader);
                if(callback!=null)observeRealTap(callback);
            }
            clockHooksReady=true;
        }catch(Throwable error){ColorOsBridge.failure("fingerprint clock lifecycle",error);}
    }
    private void onMain(Runnable action){
        Runnable guarded=()->{try{action.run();}catch(Throwable error){clearClockAttempt();ColorOsBridge.failure("fingerprint clock event",error);}};
        if(Looper.myLooper()==main.getLooper())guarded.run();else main.post(guarded);
    }
    private void setClockSuppressed(boolean value){
        try{clockSuppression.accept(value);}catch(Throwable error){ColorOsBridge.failure("fingerprint clock visual",error);}
    }
    private void beginClockAttempt(){
        clearClockAttempt();clockAttempt.begin();clockAttempts++;setClockSuppressed(true);armClockTimeout(1200);
    }
    private void armClockTimeout(long delay){
        if(clockTimeout!=null)main.removeCallbacks(clockTimeout);
        long token=clockAttempt.token();
        clockTimeout=()->{if(clockAttempt.expire(token))clearClockAttempt();};
        if(!main.postDelayed(clockTimeout,delay))clearClockAttempt();
    }
    private void fingerLifted(){
        if(!clockAttempt.active())return;
        if(clockLift!=null)main.removeCallbacks(clockLift);
        long token=clockAttempt.token();
        clockLift=()->cancelClockBeforeUnlock(token);
        if(!main.postDelayed(clockLift,300))cancelClockBeforeUnlock(token);
    }
    private void cancelClockBeforeUnlock(long token){if(clockAttempt.cancelBeforeUnlock(token))clearClockAttempt();}
    private void clearClockAttempt(){
        clockAttempt.clear();
        if(clockTimeout!=null)main.removeCallbacks(clockTimeout);
        if(clockLift!=null)main.removeCallbacks(clockLift);
        clockTimeout=null;clockLift=null;setClockSuppressed(false);
    }
    private void observeRealTap(Class<?> type){
        if(gestureHooks.contains(type))return;
        XposedBridge.hookAllMethods(type,"onClick",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                // Our synthetic prewake passes null; a real AOD tap must show its clock.
                if(p.args.length==1&&p.args[0]!=null)onMain(ColorOsFingerprintAod.this::clearClockAttempt);
            }
        });
        gestureHooks.add(type);
    }
    private Object instance(String name,Context context){
        return XposedHelpers.callStaticMethod(XposedHelpers.findClass(name,loader),"getInstance",context);
    }
    private void show(Context context,Object display,Object monitor){
        try{
            if(!ColorOsBridge.ownsWallpaper()||!(boolean)XposedHelpers.callMethod(display,"isDreaming")
                    ||!XposedHelpers.getBooleanField(monitor,"mKeyguardShowing")
                    ||XposedHelpers.getBooleanField(monitor,"mKeyguardGoingAway"))return;
            Object data=instance("com.oplus.systemui.aod.aodclock.constant.AodData",context);
            if(XposedHelpers.getBooleanField(data,"mAodIsInShow"))return;
            Object options=XposedHelpers.getObjectField(data,"mAodOptionsMgr");
            if(!(boolean)XposedHelpers.callMethod(options,"isCurrentAodSwitchEnable"))return;
            // Use the registered native callback, including its enable gate. If
            // unavailable on this ROM, leave the original fingerprint path alone.
            Class<?> wakeType=XposedHelpers.findClass("com.oplus.systemui.aod.display.OplusWakeUpController",loader);
            Object wake=XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(wakeType,"Companion"),"getInstance",context);
            if((boolean)XposedHelpers.callMethod(wake,"getProxNear")
                    ||XposedHelpers.getBooleanField(wake,"isUpsideDown"))return;
            Object callback=XposedHelpers.getObjectField(wake,"aodBlackClickWakeUpGestureCallback");
            if(callback==null){
                Class<?> holder=XposedHelpers.findClass("com.oplus.systemui.aod.scene.AodViewSingleClickWakeUpHolder",loader);
                callback=XposedHelpers.getStaticObjectField(holder,"aodBlackClickWakeUpGestureCallback");
            }
            if(callback==null||!(boolean)XposedHelpers.callMethod(callback,"isEnable"))return;
            observeRealTap(callback.getClass());
            requestedAt=SystemClock.uptimeMillis();
            XposedHelpers.callMethod(callback,"onClick",new Object[]{null});
        }catch(Throwable error){requestedAt=0;ColorOsBridge.failure("fingerprint AOD show",error);}
    }
}
