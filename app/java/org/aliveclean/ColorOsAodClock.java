package org.aliveclean;

import android.content.Context;
import android.animation.*;
import android.os.*;
import android.view.*;
import de.robv.android.xposed.*;
import java.lang.ref.WeakReference;

/** Consume the native panoramic minute/display window; no additional alarms or wakelocks. */
final class ColorOsAodClock {
    private final ClassLoader loader;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AodClockHost host=new AodClockHost();
    private WeakReference<Object> controller=new WeakReference<>(null);
    private Context context;
    private volatile boolean enabled;
    private boolean installed,failed,widgetFailure;
    private String installError="";
    private int mode=-1;
    private long wakeSerial=SystemClock.uptimeMillis(),wakeToken;
    private long lastMinute=-1;
    private ValueAnimator maskAnimation;
    private float contentAlpha=1;
    private final Runnable updateNotifications=this::updateNotifications;
    private final Runnable reconcile=this::reconcile;
    ColorOsAodClock(ClassLoader loader){this.loader=loader;host.onBoundsChanged(()->{main.removeCallbacks(updateNotifications);main.post(updateNotifications);});}
    boolean install(){
        try{
            Class<?> clock=XposedHelpers.findClass("com.oplus.systemui.keyguard.clockstyle.KeyguardStyleClockControllerImpl",loader);
            Class<?> data=XposedHelpers.findClass("com.oplus.systemui.aod.aodclock.constant.AodData",loader);
            XposedHelpers.findAndHookMethod(clock,"refreshAodTime",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable()){controller=new WeakReference<>(p.thisObject);onMain(reconcile);}}
            });
            XposedHelpers.findAndHookMethod(data,"setAodIsInShow",boolean.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable())onMain(()->{if((boolean)p.args[0]&&maskAnimation==null)contentAlpha=1;reconcile();});}
            });
            for(String method:new String[]{"getStyleClockBottomPositionForNotification","getSmallClockBottomPositionForNotification"})
                XposedHelpers.findAndHookMethod(clock,method,new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam p){
                        if(!p.hasThrowable()&&enabled&&host.ownsClock()&&p.getResult() instanceof Integer){
                            int padding=host.notificationPadding();if(padding>0)p.setResult(padding);
                        }
                    }
                });
            // onClockBottomChanged requests a padding update but does not rerun
            // the clock algorithm. Supply the current floor while the native
            // method reads its cached result, retaining its drag/bypass logic.
            Class<?> panel=XposedHelpers.findClass("com.android.systemui.shade.NotificationPanelViewController",loader);
            XposedHelpers.findAndHookMethod(panel,"getKeyguardNotificationStaticPadding",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(!enabled||!host.ownsClock())return;
                    int padding=host.notificationPadding();if(padding<=0)return;
                    Object result=XposedHelpers.getObjectField(p.thisObject,"mClockPositionResult");
                    int previous=XposedHelpers.getIntField(result,"stackScrollerPadding");
                    p.setObjectExtra("openalive.padding.result",result);p.setObjectExtra("openalive.padding.previous",previous);
                    XposedHelpers.setIntField(result,"stackScrollerPadding",padding);
                }
                @Override protected void afterHookedMethod(MethodHookParam p){
                    Object result=p.getObjectExtra("openalive.padding.result");
                    if(result!=null)XposedHelpers.setIntField(result,"stackScrollerPadding",(Integer)p.getObjectExtra("openalive.padding.previous"));
                }
            });
            Class<?> mask=XposedHelpers.findClass("com.oplus.systemui.aod.anim.OplusAODMaskAnimController",loader);
            XposedHelpers.findAndHookMethod(mask,"getPanoramicMaskInAnim",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable())observeMask(p.getResult(),false);}
            });
            XposedHelpers.findAndHookMethod(mask,"getPanoramicMaskOutAnim",boolean.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable())observeMask(p.getResult(),true);}
            });
            // This capability belongs to the moving desktop/keyguard clock,
            // independently of the panoramic wallpaper/display transition.
            XC_MethodHook separateClock=new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(enabled&&!p.hasThrowable())p.setResult(false);}
            };
            Class<?> oneShot=XposedHelpers.findClass("com.oplus.systemui.keyguard.oneshot.OneShotAnimController",loader);
            Class<?> oneShotState=XposedHelpers.findClass("com.oplus.systemui.keyguard.oneshot.OneShotAnimState",loader);
            XposedHelpers.findAndHookMethod(oneShotState,"isOneShotEnabled",separateClock);
            for(String method:new String[]{"willSupportOneShotForUnlock","willSupportRapidUnlockOneShot","isUnlockOneShotFromClockReady"})
                XposedHelpers.findAndHookMethod(oneShot,method,separateClock);
            Class<?> mediator=XposedHelpers.findClass("com.oplus.systemui.keyguard.OplusKeyguardViewMediatorExImpl",loader);
            XposedHelpers.findAndHookMethod(mediator,"keyguardGone",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    if(!p.hasThrowable())onMain(()->{if(enabled&&mode==2)host.unlockFinished();});
                }
            });
            // Leave feature initialization and callbacks intact. Native
            // startLockAnim/startUnlockAnim take their normal disabled/fallback
            // branches, including completion callbacks, rather than a skipped call.
            installed=true;
            {if(Diagnostics.TRACE)XposedBridge.log("AliveClean: AOD clock hooks installed; frame-only clock handoff disabled");}
        }catch(Throwable error){installError=error.toString();failure(error);}
        return installed;
    }
    String installError(){return installError;}
    boolean usesIndependentClock(){return enabled;}
    void configure(Context context,boolean selected,int style){
        this.context=context;enabled=installed&&selected&&style==1;if(!enabled)wakeToken=0;reconcile();
    }
    void scene(int value,boolean animate){
        if(value!=mode){
            wakeToken=enabled&&mode==0&&value==1&&animate&&host.shown()?++wakeSerial:0;
            maskAnimation=null;contentAlpha=1;
        }
        mode=value;main.removeCallbacks(reconcile);reconcile();if(enabled&&mode==0){main.postDelayed(reconcile,80);main.postDelayed(reconcile,350);}
    }
    long wakeToken(){return wakeToken;}
    void frameReady(long token,boolean submitted){
        if(!enabled||mode!=1||token==0||token!=wakeToken)return;
        if(submitted){host.frameReady();{if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Lock clock reveal after submitted frame token="+token);}}
        else rendererLost();
    }
    void rendererLost(){wakeToken=0;if(mode!=0)host.leave();}
    private void onMain(Runnable action){if(Looper.myLooper()==main.getLooper())action.run();else main.post(action);}
    private void observeMask(Object result,boolean opening){
        if(!(result instanceof ValueAnimator)){
            if(!opening&&enabled&&mode==0){contentAlpha=0;host.contentAlpha(0);}return;
        }
        ValueAnimator animation=(ValueAnimator)result;
        // Keep the platform spring/duration and its existing screen-state request.
        // This view lives above the stock mask, so mirror only the resulting alpha.
        animation.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationStart(Animator a){if(enabled&&mode==0){maskAnimation=animation;contentAlpha=opening?0:1;reconcile();host.contentAlpha(contentAlpha);}}
            @Override public void onAnimationEnd(Animator a){if(maskAnimation==animation){maskAnimation=null;contentAlpha=opening?1:0;if(enabled&&mode==0)host.contentAlpha(contentAlpha);}}
        });
        animation.addUpdateListener(a->{if(maskAnimation==animation&&enabled&&mode==0){contentAlpha=Math.max(0,Math.min(1,1-(float)a.getAnimatedValue()));host.contentAlpha(contentAlpha);}});
    }
    private void updateNotifications(){
        Object current=controller.get();if(current==null)return;
        try{Object panel=XposedHelpers.callMethod(XposedHelpers.getObjectField(current,"notificationPanelViewControllerEx"),"get");XposedHelpers.callMethod(panel,"onClockBottomChanged");}
        catch(Throwable error){failure(error);}
    }
    private void reconcile(){
        if(!enabled||context==null){host.hide();return;}
        if(mode!=0){if(mode==2)host.leaveUnlocked();else host.leave(wakeToken!=0);return;}
        try{
            Object data=XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.oplus.systemui.aod.aodclock.constant.AodData",loader),"getInstance",context);
            if(!(boolean)XposedHelpers.callMethod(data,"isPanoramicAod")){host.hide();return;}
            // mAodIsInShow becomes false BEFORE the 700 ms AOD-to-OFF mask.
            // The accepted scene remains AOD during OFF; retain stock suppression.
            boolean showing=XposedHelpers.getBooleanField(data,"mAodIsInShow");
            Object current=controller.get();
            if(current==null){
                Class<?> dependency=XposedHelpers.findClass("com.android.systemui.DependencyEx",loader);
                Object keyguard=XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(dependency,"sDependency"),"getDependency",XposedHelpers.findClass("com.android.keyguard.OplusKeyguardDependencyEx",loader));
                current=XposedHelpers.callMethod(keyguard,"getKeyguardStyleClockController");controller=new WeakReference<>(current);
            }
            if(current==null){host.hide();return;}
            Object plugin=XposedHelpers.getObjectField(current,"clockPlugin");
            if(plugin==null){host.hide();return;}
            Object time=XposedHelpers.callMethod(plugin,"getView",8),date=XposedHelpers.callMethod(plugin,"getView",20);
            // IDs 8/20 in digital/base and text clocks refer to different view classes.
            if(!(time instanceof View)||!(date instanceof View)){host.hide();return;}
            String timeType=time.getClass().getSimpleName(),dateType=date.getClass().getSimpleName();
            boolean digital=timeType.equals("ClockTimeView")&&dateType.equals("DateMessageView");
            boolean text=timeType.equals("TextTimeTextView")&&dateType.equals("TextDateInformationView");
            if(!digital&&!text){host.hide();return;}
            if(text){date=XposedHelpers.callMethod(date,"getLocalDate");if(!(date instanceof View)){host.hide();return;}}
            View t=(View)time,d=(View)date;
            Object container=XposedHelpers.getObjectField(current,"keyguardStyleClock");
            Object scope=XposedHelpers.callMethod(plugin,"getView",1);
            if(!(container instanceof View)||!(scope instanceof View)){host.hide();return;}
            if(text&&!scope.getClass().getName().equals("com.oplus.keyguard.clock.text.ui.view.ClockViewRoot")){host.hide();return;}
            View root=((View)container).getRootView();
            if(!(root instanceof ViewGroup)||!root.isAttachedToWindow())return;
            boolean fresh=!host.same((ViewGroup)root,t,d);
            // Mask from sleep-start, even before the native AOD show flag. The
            // custom face becomes visible only in the native display window.
            if(fresh&&!showing)contentAlpha=0;
            host.show((ViewGroup)root,t,d,(View)scope);host.contentAlpha(contentAlpha);host.tick(System.currentTimeMillis());
            bindWidgets(current);
            if(fresh&&host.shown()){if(Diagnostics.TRACE)android.util.Log.i("AliveClean","AOD clock attached; system lock clock retained");}
            if(Diagnostics.TRACE){long minute=System.currentTimeMillis()/60000;if(host.shown()&&minute!=lastMinute){lastMinute=minute;android.util.Log.i("AliveClean","AOD clock minute="+minute+" updated in native display window");}}
        }catch(Throwable error){host.hide();failure(error);}
    }
    private void bindWidgets(Object current){
        try{
            Object keyguard=XposedHelpers.getObjectField(current,"keyguardPlugin");
            Object plugin=XposedHelpers.callMethod(keyguard,"getWidgetPlugin");
            Object view=XposedHelpers.callMethod(plugin,"getView",0);
            host.widgets(view instanceof View?(View)view:null,view instanceof View?new ColorOsWidgetBounds((View)view):null);
        }catch(Throwable error){host.widgets(null,null);widgetFailure(error);}
    }
    private void widgetFailure(Throwable error){if(!widgetFailure){widgetFailure=true;android.util.Log.w("AliveClean","AOD widget spacing unavailable",error);}}
    private void failure(Throwable error){if(!failed){failed=true;XposedBridge.log("AliveClean: AOD clock unavailable: "+error);android.util.Log.w("AliveClean","AOD clock unavailable",error);}}
}
