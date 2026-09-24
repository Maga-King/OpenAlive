package org.aliveclean;

import android.content.Context;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Adds independent providers; native clocks continue through their original factory. */
final class ColorOsNativeClockStyles {
    private static final String FACTORY="com.oplus.keyguard.OplusStyleClockPluginProvider";
    private static final String FIELD="org.aliveclean.nativeClockProvider";
    private static final boolean DEBUG=debugEnabled();
    private static boolean panelFailureReported;
    private static boolean loadFailureReported;
    private static synchronized void reportLoadFailure(String stage,Throwable failure){
        NativeClockLoadState.failure(stage,failure);
        if(loadFailureReported)return;
        loadFailureReported=true;
        android.util.Log.w("OpenAliveClock",stage,failure);
    }
    private static boolean debugEnabled(){
        try{return (Boolean)XposedHelpers.callStaticMethod(Class.forName("android.os.SystemProperties"),"getBoolean","debug.openalive.clock",false);}
        catch(Throwable unavailable){return false;}
    }

    static void attach(ClassLoader loader){
        List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
        try{
            Class<?> provider=Class.forName(FACTORY,false,loader);
            Method apply=provider.getMethod("apply",String.class);
            Method transportApply=provider.getMethod("apply",Object.class);
            hooks.add(XposedBridge.hookMethod(provider.getConstructor(Context.class,Context.class),new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call){
                    if(call.hasThrowable())return;
                    try{
                        Context host=(Context)call.args[0];
                        Context module=host.createPackageContext("org.aliveclean",0);
                        NativeClockProvider factory=new NativeClockProvider(host,module.getAssets(),null);
                        XposedHelpers.setAdditionalInstanceField(call.thisObject,FIELD,factory);
                        NativeClockLoadState.provider();
                        NativeClockAvailability.announce(host);
                    }catch(Exception unavailable){
                        reportLoadFailure("Original clock provider initialization failed",unavailable);
                    }
                }
            }));
            XC_MethodHook createClock=new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam call){
                    if(!(call.args[0] instanceof String))return;
                    boolean custom=NativeClockProvider.contains((String)call.args[0]);
                    NativeClockLoadState.request((String)call.args[0],call.method.toString(),custom);
                    if(!custom)return;
                    Object factory=XposedHelpers.getAdditionalInstanceField(call.thisObject,FIELD);
                    if(!(factory instanceof NativeClockProvider)){
                        reportLoadFailure("Original clock provider missing for "+call.args[0],null);
                        return;
                    }
                    try{
                        Object clock=((NativeClockProvider)factory).apply((String)call.args[0]);
                        call.setResult(clock);if(clock!=null)NativeClockLoadState.created();
                    }
                    catch(Throwable unavailable){
                        call.setResult(null);
                        // A failed factory leaves an empty native clock container.
                        // Record the first cause only, rather than silently hiding
                        // it or enabling continuous SystemUI diagnostics.
                        reportLoadFailure("Original clock load failed: "+call.args[0],unavailable);
                    }
                }
            };
            // The native ProviderProxy calls Function.apply(Object). Hook that
            // actual transport boundary as well as the typed overload: ART may
            // inline apply(String) into the compiler-generated bridge, so a
            // typed-only hook can work in the editor yet miss SystemUI.
            hooks.add(XposedBridge.hookMethod(transportApply,createClock));
            if(!apply.equals(transportApply))hooks.add(XposedBridge.hookMethod(apply,createClock));
            Class<?> editor=Class.forName("com.oplus.keyguard.clock.digital.ui.view.EditPanelView",false,loader);
            Class<?> state=Class.forName("com.oplus.keyguard.clock.digital.domain.state.ClockViewRootState",false,loader);
            Method createPanel=editor.getDeclaredMethod("createClockStylePanel",Context.class,Context.class,state);
            hooks.add(XposedBridge.hookMethod(createPanel,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call){
                    if(call.hasThrowable()||!(call.getResult() instanceof android.widget.LinearLayout))return;
                    try{NativeClockEditor.extendStockPanel(call.thisObject,(android.widget.LinearLayout)call.getResult());}
                    catch(Throwable unsupported){
                        if(DEBUG&&!panelFailureReported){panelFailureReported=true;android.util.Log.w("OpenAliveClock","native selector unavailable",unsupported);}
                    }
                }
            }));
            installMaterialBootstrap(loader);
            if(DEBUG)android.util.Log.i("OpenAliveClock","independent factory and selector hooks attached");
        }catch(Throwable unsupported){
            for(XC_MethodHook.Unhook hook:hooks)hook.unhook();
            reportLoadFailure("Original clock provider hook unavailable",unsupported);
        }
    }
    private static void installMaterialBootstrap(ClassLoader loader){
        try{
            Class<?> colors=Class.forName("com.oplus.keyguard.clock.digital.ui.controller.ColorController",false,loader);
            XposedBridge.hookMethod(colors.getMethod("setScreenShotEx",android.graphics.Bitmap.class,int[].class,boolean.class,Long.class),new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam call){
                    if(!ColorOsBridge.ownsWallpaper())return;
                    NativeClockLoadState.materialInput(call.thisObject,Boolean.TRUE.equals(call.args[2]));
                    if(!Boolean.TRUE.equals(call.args[2]))return;
                    try{
                        if(NativeClockMaterialBootstrap.seed(call.thisObject,(android.graphics.Bitmap)call.args[0],(int[])call.args[1],(Long)call.args[3]))
                            NativeClockLoadState.materialSeeded();
                    }catch(Exception failure){reportLoadFailure("Native clock material initialization",failure);}
                }
            });
        }catch(Throwable unsupported){
            // Optional material compatibility must not unregister clock providers.
            reportLoadFailure("Native material contract unavailable",unsupported);
        }
    }
    private ColorOsNativeClockStyles(){}
}
