package org.aliveclean;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.animation.LinearInterpolator;
import de.robv.android.xposed.*;

/** Reuse the native single-tap AOD entry before a black-screen fingerprint wake. */
final class ColorOsFingerprintAod {
    private final ClassLoader loader;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadLocal<Object> showing=new ThreadLocal<>();
    private volatile long requestedAt;
    ColorOsFingerprintAod(ClassLoader loader){this.loader=loader;}
    void install(){
        try{
            Class<?> finger=XposedHelpers.findClass("com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintUiMech",loader);
            XposedHelpers.findAndHookMethod(finger,"onFpTouch",boolean.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(!ColorOsBridge.ownsWallpaper()||!(boolean)p.args[0])return;
                    try{
                        if(XposedHelpers.getBooleanField(p.thisObject,"isTouchDownNow")
                                ||!XposedHelpers.getBooleanField(p.thisObject,"isFingerprintIconShow"))return;
                        Context context=(Context)XposedHelpers.getObjectField(p.thisObject,"context");
                        Object display=instance("com.oplus.systemui.aod.display.AODDisplayUtil",context);
                        if((int)XposedHelpers.callMethod(display,"getDeviceDisplayState")!=1
                                ||!(boolean)XposedHelpers.callMethod(display,"isDreaming"))return;
                        Object monitor=XposedHelpers.getObjectField(p.thisObject,"keyguardUpdateMonitor");
                        main.post(()->show(context,display,monitor));
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
            requestedAt=SystemClock.uptimeMillis();
            XposedHelpers.callMethod(callback,"onClick",new Object[]{null});
        }catch(Throwable error){requestedAt=0;ColorOsBridge.failure("fingerprint AOD show",error);}
    }
}
