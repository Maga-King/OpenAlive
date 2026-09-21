package org.aliveclean;

import android.content.Context;
import de.robv.android.xposed.*;
import java.util.function.BooleanSupplier;

/** Select the native panoramic sleep path before its window and display work is scheduled. */
final class ColorOsOccludedTransition {
    private final ClassLoader loader;
    private final BooleanSupplier selected;
    private final ThreadLocal<Boolean> sleep=new ThreadLocal<>();
    private boolean failed;
    ColorOsOccludedTransition(ClassLoader loader,BooleanSupplier selected){this.loader=loader;this.selected=selected;}
    void install(){
        try{
            XposedHelpers.findAndHookMethod(type("com.oplus.systemui.keyguard.OplusKeyguardViewMediatorExImpl"),"onStartedGoingToSleep",int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){if(selected.getAsBoolean())sleep.set(true);}
                @Override protected void afterHookedMethod(MethodHookParam p){sleep.remove();}
            });
            XC_MethodHook selectRoute=new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(!Boolean.TRUE.equals(sleep.get())||!selected.getAsBoolean())return;
                    Object display=p.thisObject;
                    try{
                        if(integer(display,"mAODProcessType")!=4002||integer(display,"mPerformAodType")!=3
                                ||bool(display,"mIsAodProcessTypeInit")||!bool(display,"mIsAodPerformTypeInit"))return;
                        // Both queries run after selection. The off-path query precedes native
                        // shadeFadingIn setup, which must also run for an unlocked expanded shade.
                        if(!eligible(display))return;
                        boolean shade=bool(display,"mWindowAlreadyShown")&&!bool(display,"mKgShowingWhileGoingToSleep");
                        if(shade)XposedHelpers.setBooleanField(display,"mWindowAlreadyShown",false);
                        try{
                            XposedHelpers.setIntField(display,"mAODProcessType",4001);
                        }catch(Throwable error){
                            if(shade)XposedHelpers.setBooleanField(display,"mWindowAlreadyShown",true);
                            throw error;
                        }
                        // Clearing only the sleep snapshot gives the native remote runner ownership;
                        // leaving it true would make AppearAnimRunnerWrapper immediately finish.
                        // Actual panel visibility and all keyguard/security fields are unchanged.
                        {if(Diagnostics.TRACE)android.util.Log.i("AliveClean",(shade?"Unlocked shade":"Covered keyguard")+" uses native panoramic sleep 4002->4001");}
                    }catch(Throwable error){failure(error);}
                }
            };
            Class<?> display=type("com.oplus.systemui.aod.display.AODDisplayUtil");
            XposedHelpers.findAndHookMethod(display,"isOffWhileDreaming",selectRoute);
            XposedHelpers.findAndHookMethod(display,"isPanoramicProcessType",selectRoute);
        }catch(Throwable error){failure(error);}
    }
    private boolean eligible(Object display){
        Context context=(Context)XposedHelpers.getObjectField(display,"mContext");
        Object state=dependency("com.android.systemui.statusbar.policy.KeyguardStateController",false);
        boolean covered=bool(state,"mShowing")&&bool(state,"mOccluded");
        boolean unlockedShade=!bool(state,"mShowing")&&!bool(display,"mKgShowingWhileGoingToSleep")&&bool(display,"mWindowAlreadyShown");
        if((!covered&&!unlockedShade)||bool(state,"mKeyguardGoingAway"))return reject("keyguard state");
        String feature="com.oplusos.systemui.common.feature.KeyguardFeatureOption";
        // Native 4001 is the !non-local-optical route, also used by ultrasonic sensors.
        if(staticBool(feature,"isNonLocalHbmSupportOpticalFp"))return reject("non-local optical fingerprint");
        if(staticBool("com.oplusos.systemui.common.feature.FeatureOption","isLightOs")){
            Object shutdown=dependency("com.android.systemui.shutdown.ShutDownDependencyEx",true);
            if(callBool(XposedHelpers.callMethod(shutdown,"getGlobalActionsDialog"),"isGlobalActionsShowing"))return reject("power menu");
        }
        if(!bool(display,"mUserUnlocked")||!callBool(display,"hasBeenDreamingAfterFolded")
                ||integer(display,"mCarConnectionType")==2)return reject("user/fold/car state");
        int reason=integer(display,"mOriginalSleepReason");
        if(reason==13||reason==2&&bool(display,"mKgShowingWhileGoingToSleep"))return reject("sleep reason");
        if(XposedHelpers.getStaticBooleanField(type("com.oplus.systemui.aod.utils.AodCustomizeKeyguardSwitchObserver"),"isCustomizeKgdSwitchOFF"))return reject("custom keyguard disabled");
        Object sub=companionInstance("com.oplus.systemui.subdisplay.OplusSubDisplayUtils",context);
        if(callBool(sub,"getSubDisplayShow"))return reject("secondary display");
        Object finger=companionInstance("com.oplus.systemui.biometrics.finger.FingerprintStateHelper",context);
        if(bool(finger,"isLockLater"))return reject("delayed lock");
        Object manager=XposedHelpers.callStaticMethod(type("com.oplus.systemui.aod.common.AodManager"),"getInstance",context);
        if(callBool(manager,"isInCall")||!callBool(manager,"isPortraitWhilePowerOff"))return reject("call or rotation");
        Object monitor=dependency("com.android.keyguard.KeyguardUpdateMonitor",false);
        if(callBool(monitor,"getScreenPinningActive"))return reject("screen pinning");
        Object helper=dependency("com.android.keyguard.OplusKeyguardDependencyEx",true);
        Object mediator=XposedHelpers.callMethod(XposedHelpers.callMethod(helper,"getOplusKeyguardViewMediatorEx"),"getKeyguardViewMediator");
        if(callBool(mediator,"isAnimatingBetweenKeyguardAndSurfaceBehindOrWillBe"))return reject("unlock running");
        if(callBool(XposedHelpers.callMethod(helper,"getOplusBiometricAuthController"),"isBiometricPromptShowing"))return reject("biometric prompt");
        Object prevent=XposedHelpers.callMethod(helper,"getOplusPreventMode");
        if(callBool(prevent,"isPreventModeViewShown")&&callBool(prevent,"isPreventModeTrigger"))return reject("prevent mode");
        Object dark=XposedHelpers.getStaticObjectField(type("com.oplus.systemui.aod.utils.AodDarkModeWaitScreenOffObserver"),"Companion");
        return !callBool(dark,"isNeedChangeDarkMode")||reject("dark mode change");
    }
    private boolean reject(String reason){{if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Covered keyguard keeps native fallback: "+reason);}return false;}
    private Class<?> type(String name){return XposedHelpers.findClass(name,loader);}
    private Object dependency(String name,boolean extended){
        Object owner=XposedHelpers.getStaticObjectField(type(extended?"com.android.systemui.DependencyEx":"com.android.systemui.Dependency"),"sDependency");
        return XposedHelpers.callMethod(owner,extended?"getDependency":"getDependencyInner",type(name));
    }
    private Object companionInstance(String name,Context context){return XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(type(name),"Companion"),"getInstance",context);}
    private boolean staticBool(String name,String method){return (boolean)XposedHelpers.callStaticMethod(type(name),method);}
    private static boolean callBool(Object owner,String method){return (boolean)XposedHelpers.callMethod(owner,method);}
    private static boolean bool(Object owner,String field){return XposedHelpers.getBooleanField(owner,field);}
    private static int integer(Object owner,String field){return XposedHelpers.getIntField(owner,field);}
    private void failure(Throwable error){if(!failed){failed=true;XposedBridge.log("AliveClean: covered keyguard transition unavailable: "+error);android.util.Log.w("AliveClean","Covered keyguard transition unavailable",error);}}
}
