package org.aliveclean;

import android.app.AlarmManager;
import android.app.Notification;
import android.content.Context;
import android.os.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService.Ranking;
import android.service.notification.StatusBarNotification;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Point;
import de.robv.android.xposed.*;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Opt-in notification handling through the existing ColorOS panoramic controller. */
final class ColorOsNotificationEffects {
    private static final String ROOT="com.oplus.systemui.aod.";
    private final ClassLoader loader;
    private final BooleanSupplier selected;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final NotificationPulseWindow window=new NotificationPulseWindow();
    private final LinkedHashMap<String,Long> seen=new LinkedHashMap<String,Long>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Long> e){return size()>128;}
    };
    private Class<?> panoramic,managerClass,listenerClass,curvedClass;
    private Object controller,manager,listener,curved;
    private Context context;
    private AlarmManager alarm;
    private int mode,seconds=10;
    private String color="blue";
    private int ringColor=NotificationOptions.RING_BLUE;
    private boolean installed,internalShow;
    private long dreamStarted,pulses,lights;
    private final AlarmManager.OnAlarmListener timeout=this::finishPulse;
    private final AlarmManager.OnAlarmListener lightTimeout=()->run(()->clearFlymeLight());
    private FlymeNotificationLight flymeLight;
    private ViewGroup lightHost;
    private int lightGeneration;

    ColorOsNotificationEffects(ClassLoader loader,BooleanSupplier selected){this.loader=loader;this.selected=selected;}
    void install(){
        try{
            panoramic=type("controller.PanoramicAodController");
            Class<?> base=type("controller.BaseAodController");
            managerClass=type("common.NotificationManager");listenerClass=type("common.NotificationManager$NotificationListener");
            curvedClass=type("common.CurvedDisplayManager");
            XposedHelpers.findAndHookMethod(type("common.CurvedDisplayManager$ShowCurvedNotification"),"run",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(curved!=null&&XposedHelpers.getObjectField(p.thisObject,"instance")==curved){
                        try{if(mode!=2||!enabled()||!dreaming()||!blackScreen())p.setResult(null);}
                        catch(Throwable error){p.setResult(null);ColorOsBridge.failure("notification display gate",error);}
                    }
                }
            });
            XposedHelpers.findAndHookMethod(panoramic,"handlePanoramicAodWhenDisplayStateChange",int.class,boolean.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    // AOD takes priority even if a native light began on black.
                    // Remove its queued callback/view before the AOD show request.
                    if(p.thisObject==controller&&Boolean.TRUE.equals(p.args[1]))run(()->clearNativeLight());
                }
            });
            for(String name:new String[]{"setCurrentNearState","setOrientationUpsideDown"}){
                XposedHelpers.findAndHookMethod(type("common.AodManager"),name,boolean.class,new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam p){if(Boolean.TRUE.equals(p.args[0]))run(()->{finishPulse();clearFlymeLight();});}
                });
            }
            XposedHelpers.findAndHookMethod(base,"initDefaultComponent",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(panoramic.isInstance(p.thisObject))run(()->bind(p.thisObject));}
            });
            XposedHelpers.findAndHookMethod(base,"onDreamingStarted",boolean.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(panoramic.isInstance(p.thisObject))run(()->{bind(p.thisObject);dreamStarted=System.currentTimeMillis();seen.clear();prepare();});}
            });
            XposedHelpers.findAndHookMethod(panoramic,"startShow",boolean.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(p.thisObject==controller)run(()->prepare());}
            });
            XposedHelpers.findAndHookMethod(base,"clearAllComponent",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){if(p.thisObject==controller)run(()->detach());}
            });
            XposedHelpers.findAndHookMethod(panoramic,"onDreamingStopped",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){if(p.thisObject==controller)run(()->detach());}
            });
            // Native taps, proximity changes and wake-up take ownership back from
            // the temporary notification session. Its old timer must not hide them.
            for(String name:new String[]{"handleAodShowingStateChange","hideClock","onWakingUpAnimationStart"}){
                XposedBridge.hookAllMethods(panoramic,name,new XC_MethodHook(){
                    @Override protected void beforeHookedMethod(MethodHookParam p){
                        if(p.thisObject==controller&&!internalShow)run(()->{
                            cancelTimer();
                            if(!name.equals("handleAodShowingStateChange"))clearFlymeLight();
                            if(name.equals("onWakingUpAnimationStart"))clearNativeLight();
                        });
                    }
                });
            }
            XposedHelpers.findAndHookMethod(type("surface.OplusAodCurvedDisplayView"),
                    "updateReceiveNotification",StatusBarNotification.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    // The native view still renders/redacts notification content.
                    // Only its selected color belongs to this settings page.
                    if(curved!=null&&mode==2&&selected.getAsBoolean())try{
                        if(XposedHelpers.getObjectField(p.thisObject,"manager")==curved)
                            XposedHelpers.callMethod(p.thisObject,"setColor",color,false);
                    }catch(Throwable error){ColorOsBridge.failure("notification color",error);}
                }
            });
            XposedHelpers.findAndHookMethod(type("display.AODDisplayUtil"),"dump",java.io.PrintWriter.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){((java.io.PrintWriter)p.args[0]).println(
                    "OpenAlive notifications: installed="+installed+" mode="+mode+" listener="+(listener!=null)+" pulse="+window.active()+" pulses="+pulses+" lightRequests="+lights);}
            });
            installed=true;
        }catch(Throwable error){installed=false;ColorOsBridge.failure("notification hooks",error);}
    }
    void configure(int newMode,int newSeconds,String newColor){
        configure(newMode,newSeconds,newColor,NotificationOptions.RING_BLUE);
    }
    void configure(int newMode,int newSeconds,String newColor,int newRingColor){
        if(!installed)return;
        run(()->{
            int next=NotificationOptions.mode(newMode);
            if(mode!=next||!selected.getAsBoolean()){finishPulse();clearLight();}
            mode=next;seconds=NotificationPulseWindow.seconds(newSeconds);color=NotificationOptions.color(newColor);
            ringColor=newRingColor|0xff000000;if(flymeLight!=null)flymeLight.setRingColor(ringColor);
            prepare();
        });
    }
    private void bind(Object target){
        if(controller==target)return;
        detach();controller=target;context=(Context)XposedHelpers.callMethod(target,"getContext");
        alarm=context.getSystemService(AlarmManager.class);
    }
    private boolean enabled(){return installed&&mode!=0&&selected.getAsBoolean()&&controller!=null;}
    private boolean dreaming(){return controller!=null&&(Boolean)XposedHelpers.callMethod(controller,"getDreamingStarted");}
    private void prepare(){
        if(!enabled()||!dreaming()){removeListener();return;}
        if(listener==null){
            manager=XposedHelpers.callStaticMethod(managerClass,"getInstance",context);
            XposedHelpers.callMethod(manager,"init");
            listener=Proxy.newProxyInstance(loader,new Class<?>[]{listenerClass},(proxy,method,args)->{
                switch(method.getName()){
                    case "equals":return proxy==args[0];
                    case "hashCode":return System.identityHashCode(proxy);
                    case "toString":return "OpenAliveNotificationListener";
                    case "onReceiveNotification":
                        StatusBarNotification n=(StatusBarNotification)args[1];boolean fresh=(Boolean)args[2];
                        main.post(()->run(()->receive(n,fresh)));break;
                }
                return null;
            });
            XposedHelpers.callMethod(manager,"addNotificationListener",listener);
            XposedHelpers.callMethod(manager,"registerNotification");
        }
    }
    private void receive(StatusBarNotification n,boolean fresh)throws Exception {
        if(!enabled()||!fresh||n==null||n.getPostTime()<dreamStarted)return;
        Long previous=seen.put(n.getKey(),n.getPostTime());
        if(previous!=null&&previous>=n.getPostTime())return;
        if(!eligible(n))return;
        // Only the native effect changes the panel state before attaching.
        // Flyme's overlays may coexist with an already visible AOD.
        if(mode==2&&!blackScreen())return;
        if(mode==1)showPulse();else if(mode==2)showLight(n);else showFlymeLight(n);
    }
    private boolean blackScreen(){
        Display display=context==null?null:context.getDisplay();
        return controller!=null&&display!=null&&display.getState()==Display.STATE_OFF
                &&!context.getSystemService(PowerManager.class).isInteractive()
                &&(Integer)XposedHelpers.callMethod(XposedHelpers.callMethod(controller,"getAodDisplayUtil"),"getDeviceDisplayState")==Display.STATE_OFF
                &&!XposedHelpers.getBooleanField(XposedHelpers.callMethod(controller,"getAodData"),"mAodIsInShow");
    }
    private boolean eligible(StatusBarNotification n){
        if(!dreaming()||context.getSystemService(PowerManager.class).isInteractive())return false;
        Notification notification=n.getNotification();
        if(n.isOngoing()||(notification.flags&(Notification.FLAG_FOREGROUND_SERVICE|Notification.FLAG_GROUP_SUMMARY))!=0)return false;
        if(Settings.Global.getInt(context.getContentResolver(),"zen_mode",0)!=0)return false;
        Object aod=XposedHelpers.callMethod(controller,"getAodManager");
        if(XposedHelpers.getBooleanField(aod,"mCurrentNearState")||XposedHelpers.getBooleanField(aod,"mIsOrientationUpsideDown")
                ||(Boolean)XposedHelpers.callMethod(aod,"isInCall"))return false;
        Object saving=XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.oplus.systemui.qs.observer.SuperPowerSaveSettingsObserver",loader),"getInstance");
        if(XposedHelpers.getBooleanField(saving,"isSuperPowerSaveState"))return false;
        Object data=XposedHelpers.callMethod(controller,"getAodData"),options=XposedHelpers.getObjectField(data,"mAodOptionsMgr");
        if(!(Boolean)XposedHelpers.callMethod(options,"isCurrentAodSwitchEnable"))return false;
        if((Boolean)XposedHelpers.callMethod(options,"isCurrentAodEnableTimeOpen")
                &&!(Boolean)XposedHelpers.callStaticMethod(type("aodclock.util.CommonUtils"),"checkInDuration",context))return false;
        // Use the already filtered SystemUI entry; do not query or render private
        // notification text in OpenAlive, and reject silent/suspended channels.
        Iterable<?> entries=(Iterable<?>)XposedHelpers.callMethod(manager,"getActiveNotificationEntries");
        if(entries==null)return false;
        for(Object entry:entries){
            StatusBarNotification sbn=(StatusBarNotification)XposedHelpers.callMethod(entry,"getSbn");
            if(sbn==null||!n.getKey().equals(sbn.getKey()))continue;
            Ranking rank=(Ranking)XposedHelpers.callMethod(entry,"getRanking");
            if(rank.getImportance()<android.app.NotificationManager.IMPORTANCE_DEFAULT||rank.isSuspended()
                    ||!rank.matchesInterruptionFilter()||(rank.getSuppressedVisualEffects()&129)!=0)return false;
            Class<?> dependencies=XposedHelpers.findClass("com.android.systemui.DependencyEx",loader);
            Object helper=XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(dependencies,"sDependency"),"getDependency",
                    XposedHelpers.findClass("com.oplus.systemui.statusbar.notification.helper.NotificationKeyguardHelper",loader));
            return (Boolean)XposedHelpers.callMethod(helper,"shouldShowOnKeyguard",n);
        }
        return false;
    }
    private void showPulse(){
        Object display=XposedHelpers.callMethod(controller,"getAodDisplayUtil");
        boolean dark=(Integer)XposedHelpers.callMethod(display,"getDeviceDisplayState")==Display.STATE_OFF
                &&!XposedHelpers.getBooleanField(XposedHelpers.callMethod(controller,"getAodData"),"mAodIsInShow");
        boolean existing=window.active();
        if(!window.begin(SystemClock.elapsedRealtime(),dark,seconds))return;
        // Schedule before showing, so even a native call failure has bounded ownership.
        scheduleTimeout();
        if(!existing){
            XposedHelpers.callMethod(XposedHelpers.callMethod(controller,"getAodManager"),"acquireWakeLock",2000L);
            Message message=Message.obtain();message.what=1;
            internalShow=true;
            try{XposedHelpers.callMethod(controller,"handleAodShowingStateChange",message);}
            finally{internalShow=false;message.recycle();}
            pulses++;
        }
    }
    private void scheduleTimeout(){
        alarm.cancel(timeout);
        alarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+window.remaining(SystemClock.elapsedRealtime()),
                "OpenAliveNotificationPulse",timeout,main);
    }
    private void finishPulse(){
        boolean owned=window.active();cancelTimer();
        if(owned&&controller!=null)try{
            if(dreaming()&&!context.getSystemService(PowerManager.class).isInteractive()){
                XposedHelpers.callMethod(XposedHelpers.callMethod(controller,"getAodManager"),"acquireWakeLock",1500L);
                XposedHelpers.callMethod(controller,"hideClock",0);
            }
        }catch(Throwable error){ColorOsBridge.failure("notification pulse end",error);}
    }
    private void cancelTimer(){window.cancel();if(alarm!=null)alarm.cancel(timeout);}
    private void showLight(StatusBarNotification n){
        if(XposedHelpers.getBooleanField(XposedHelpers.callMethod(controller,"getAodData"),"mIsShowingCurvedDisplay"))return;
        // Panoramic already owns this native overlay for its three-key component.
        // Do not create another root window, clock or wallpaper surface.
        if(XposedHelpers.callMethod(controller,"getAodBlackLayout")==null)return;
        clearLight();
        curved=XposedHelpers.newInstance(curvedClass,context);
        XposedHelpers.setIntField(curved,"mCurvedDisplayNotificationSwitch",1);
        XposedHelpers.setBooleanField(controller,"withCurvedDisplayView",true);
        XposedHelpers.callMethod(controller,"setCurvedDisplayManager",curved);
        XposedHelpers.callMethod(curved,"onReceiveNotification",new StatusBarNotification[]{n},n,true);lights++;
    }
    private void clearLight(){
        clearFlymeLight();
        clearNativeLight();
    }
    private void clearNativeLight(){
        if(curved==null)return;
        Object old=curved;curved=null;
        // startShow may have registered this native manager as a listener too.
        // Remove it so an old instance cannot enqueue an unowned notification.
        XposedHelpers.callMethod(XposedHelpers.callStaticMethod(managerClass,"getInstance",context),"removeNotificationListener",old);
        ((Handler)XposedHelpers.getObjectField(old,"mHandler")).removeCallbacksAndMessages(null);
        View view=(View)XposedHelpers.getObjectField(old,"mCurvedDisplayView");
        if(view!=null){
            XposedHelpers.callMethod(view,"removeAnimationEndListener");
            XposedHelpers.callMethod(controller,"removeCurvedDisplayView",view);
        }
        XposedHelpers.callMethod(controller,"setCurvedDisplayManager",new Object[]{null});
        XposedHelpers.setBooleanField(controller,"withCurvedDisplayView",false);
    }
    private void showFlymeLight(StatusBarNotification notification)throws Exception {
        if(flymeLight!=null||XposedHelpers.getBooleanField(XposedHelpers.callMethod(controller,"getAodData"),"mIsShowingCurvedDisplay"))return;
        Object host=XposedHelpers.callMethod(controller,"getAodBlackLayout");
        if(!(host instanceof ViewGroup))return;
        final int generation=++lightGeneration;final Object owner=controller;final boolean ring=mode==3;
        Context assets=context.createPackageContext("org.aliveclean",0);
        new Thread(()->{
            try{
                FlymeNotificationLight.Model model=FlymeNotificationLight.load(assets,ring);
                main.post(()->run(()->{
                    if(generation!=lightGeneration||controller!=owner||!enabled()||!eligible(notification))return;
                    try{
                        ViewGroup parent=(ViewGroup)host;
                        if(!parent.isAttachedToWindow()||XposedHelpers.getBooleanField(XposedHelpers.callMethod(controller,"getAodData"),"mIsShowingCurvedDisplay"))return;
                        flymeLight=new FlymeNotificationLight(context,model,ring);lightHost=parent;
                        flymeLight.setRingColor(ringColor);
                        Point size=new Point();context.getDisplay().getRealSize(size);
                        int[] xy=new int[2];parent.getLocationOnScreen(xy);
                        flymeLight.layoutForDisplay(size.x,size.y,context.getDisplay().getCutout(),xy[0],xy[1]);
                        long duration=model.duration()+500;
                        // Native black-layout invalidation supplies DOZE refresh while
                        // the light exists. Its ordinary OFF vote resumes on removal.
                        XposedHelpers.callMethod(XposedHelpers.callMethod(controller,"getAodData"),"setShowingCurvedDisplay",true);
                        parent.addView(flymeLight,new android.widget.FrameLayout.LayoutParams(-1,-1));
                        XposedHelpers.callMethod(XposedHelpers.callMethod(controller,"getAodManager"),"acquireWakeLock",duration);
                        XposedHelpers.callMethod(parent,"onInvalidated");
                        alarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+duration,"OpenAliveNotificationLight",lightTimeout,main);
                        flymeLight.play(()->run(()->clearFlymeLight()));lights++;
                    }catch(Exception error){clearFlymeLight();throw new IllegalStateException(error);}
                }));
            }catch(Exception error){ColorOsBridge.failure("notification preset",error);}
        },"OpenAliveNotificationAsset").start();
    }
    private void clearFlymeLight(){
        lightGeneration++;
        if(alarm!=null)alarm.cancel(lightTimeout);
        FlymeNotificationLight old=flymeLight;ViewGroup parent=lightHost;flymeLight=null;lightHost=null;
        if(old==null)return;
        old.stop();if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);
        if(controller!=null)XposedHelpers.callMethod(XposedHelpers.callMethod(controller,"getAodData"),"setShowingCurvedDisplay",false);
        if(parent!=null)XposedHelpers.callMethod(parent,"onInvalidated");
    }
    private void removeListener(){if(listener!=null&&manager!=null)XposedHelpers.callMethod(manager,"removeNotificationListener",listener);listener=null;manager=null;}
    private void detach(){cancelTimer();removeListener();clearLight();controller=null;seen.clear();}
    private Class<?> type(String name){return XposedHelpers.findClass(ROOT+name,loader);}
    private interface Action {void run()throws Exception;}
    private void run(Action action){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->run(action));return;}
        try{action.run();}catch(Throwable error){finishPulse();try{clearFlymeLight();}catch(Throwable ignored){}ColorOsBridge.failure("notification effects",error);}
    }
}
