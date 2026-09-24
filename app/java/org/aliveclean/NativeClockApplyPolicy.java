package org.aliveclean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Removes only the editor's dirty gate; native save/loading gates still apply. */
final class NativeClockApplyPolicy {
    static void install(ClassLoader loader){
        try{
            Class<?> activity=Class.forName("com.oplus.wallpapers.themes.edit.ThemeEditActivity",false,loader);
            Class<?> event=Class.forName("com.oplus.wallpapers.business.themeedit.mvvm.eventstate.i",false,loader);
            Class<?> engine=Class.forName("com.oplus.wallpapers.business.themeedit.edit.videodepth.COEEngineController",false,loader);
            java.lang.reflect.Method status=event.getDeclaredMethod("d"),binding=activity.getDeclaredMethod("v5"),busy=engine.getDeclaredMethod("f");
            Object engineInstance=engine.getField("INSTANCE").get(null);
            status.setAccessible(true);binding.setAccessible(true);busy.setAccessible(true);
            // DEX names, not JADX's names recovered from Kotlin metadata.
            // Resolve once up front so an unsupported version cannot throw on every touch.
            java.lang.reflect.Field override=activity.getDeclaredField("x0"),applying=activity.getDeclaredField("e0");
            override.setAccessible(true);applying.setAccessible(true);
            Class<?> initEvent=Class.forName("com.oplus.wallpapers.business.themeedit.mvvm.eventstate.l",false,loader);
            java.lang.reflect.Method initStatus=initEvent.getDeclaredMethod("d");
            initStatus.setAccessible(true);
            XposedBridge.hookMethod(activity.getDeclaredMethod("T5",activity,initEvent),new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call)throws Throwable{
                    if(call.hasThrowable()||!"EVENT_USER_NOT_EDIT".equals(((Enum<?>)initStatus.invoke(call.args[1])).name()))return;
                    enable(call.args[0],binding,busy,engineInstance,override,applying);
                }
            });
            // Other native collectors (theme hash, smart-layout restoration and
            // initial binding) can disable the button after the dirty event.
            // Reconcile before dispatch, leaving the native click/save path intact.
            XposedBridge.hookMethod(activity.getDeclaredMethod("dispatchTouchEvent",android.view.MotionEvent.class),new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam call){
                    if(((android.view.MotionEvent)call.args[0]).getActionMasked()!=android.view.MotionEvent.ACTION_DOWN)return;
                    try{enable(call.thisObject,binding,busy,engineInstance,override,applying);}
                    catch(Throwable failure){NativeClockLoadState.failure("Native editor apply readiness",failure);}
                }
            });
            XposedBridge.hookMethod(activity.getDeclaredMethod("l4",activity,event),new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call)throws Throwable{
                    if(call.hasThrowable()||!"EVENT_FOLD_VIEW_MODEL_APPLY_ABLE".equals(((Enum<?>)status.invoke(call.args[1])).name()))return;
                    enable(call.args[0],binding,busy,engineInstance,override,applying);
                }
            });
        }catch(Throwable unavailable){NativeClockLoadState.failure("Native editor apply policy",unavailable);}
    }
    private static void enable(Object editor,java.lang.reflect.Method binding,java.lang.reflect.Method busy,Object engine,
            java.lang.reflect.Field override,java.lang.reflect.Field applying)throws Exception{
        if(override.getBoolean(editor))return;
        Object views=binding.invoke(editor);
        android.view.View done=(android.view.View)XposedHelpers.getObjectField(views,"f");
        if(done==null||!done.hasOnClickListeners())return;
        // applyAble belongs to native loading state; never overwrite it to
        // simulate a modified draft. U3 still checks effects/loading on click.
        Object model=XposedHelpers.callMethod(editor,"g5");
        boolean ready=Boolean.TRUE.equals(XposedHelpers.callMethod(model,"Z1"));
        done.setEnabled(ready&&!applying.getBoolean(editor)
                &&!Boolean.TRUE.equals(busy.invoke(engine)));
    }
    private NativeClockApplyPolicy(){}
}
