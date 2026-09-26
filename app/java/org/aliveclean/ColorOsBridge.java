package org.aliveclean;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.*;
import android.os.*;
import de.robv.android.xposed.*;

/** Event-driven adapter for the verified ColorOS 17 SystemUI classes. */
final class ColorOsBridge {
    static boolean ownsWallpaper(){return selected;}
    static final String ACTION="org.aliveclean.SCENE";
    static final String ACTION_LAYOUT="org.aliveclean.AOD_LAYOUT";
    static final String SENDER_PERMISSION="android.permission.STATUS_BAR";
    private static volatile boolean selected;
    private static Context context;
    private static Handler worker;
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final IBinder owner=new Binder();
    private static int wallpaperUid=-1;
    private static volatile Messenger channel;
    private static boolean connecting;
    private static int connectAttempts;
    private static int lastMode=-1;
    private static int lastPhase;
    private static boolean localAodColor;
    private static final SceneState stateOrder=new SceneState();
    private static ColorOsClockTracker clocks;
    private static ColorOsAodClock aodClock;
    private static ColorOsContinuousAod continuousAod;
    private static ColorOsNotificationEffects notificationEffects;
    private static boolean continuousAodRequested;
    private static final Messenger clockFeedback=new Messenger(new Handler(Looper.getMainLooper(),message->{
        if(message.sendingUid==wallpaperUid&&wallpaperUid>=0&&message.what==1&&selected&&aodClock!=null){
            Bundle b=message.getData();aodClock.frameReady(b.getLong("token",0),b.getBoolean("submitted",false));
        }
        return true;
    }));
    private static boolean clockApi;
    private static boolean observingConfiguration;
    private static ClassLoader installedLoader;
    private static final java.util.Set<String> failures=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    static synchronized void install(ClassLoader cl) {
        // SystemUI also loads package/plugin contexts. Their callbacks may carry
        // a loader without the SystemUI implementation. Never replace a live bridge.
        if(installedLoader!=null){{if(Diagnostics.TRACE)XposedBridge.log("AliveClean: duplicate SystemUI callback ignored");}return;}
        Class<?> application=null;
        for(String name:new String[]{"com.android.systemui.application.impl.SystemUIApplicationImpl","com.android.systemui.application.SystemUIApplication","com.android.systemui.SystemUIApplication"}){
            application=XposedHelpers.findClassIfExists(name,cl);if(application!=null)break;
        }
        Class<?> controller=XposedHelpers.findClassIfExists("com.oplus.systemui.aod.display.SmoothTransitionController",cl);
        if(application==null||controller==null){{if(Diagnostics.TRACE)XposedBridge.log("AliveClean: waiting for SystemUI implementation loader");}return;}
        installedLoader=cl;
        clocks=new ColorOsClockTracker(cl);
        clocks.install();
        aodClock=new ColorOsAodClock(cl);clockApi=aodClock.install();
        continuousAod=new ColorOsContinuousAod(cl);continuousAod.install();
        notificationEffects=new ColorOsNotificationEffects(cl,()->selected);notificationEffects.install();
        try{
            XposedHelpers.findAndHookMethod(application,"onCreate",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){initialize((Context)p.thisObject);}
            });
            {if(Diagnostics.TRACE)XposedBridge.log("AliveClean: SystemUI entry="+application.getName());}
        }catch(Throwable error){failure("application",error);}
        installPowerEvents(cl,controller);
        new ColorOsOccludedTransition(cl,()->selected).install();
        installPanoramicMask(cl);
        installPanoramicHandoff(cl);
        installUnlockEvents(cl);
        installAnimationWindow(cl);
        try{
            XposedHelpers.findAndHookMethod(controller,"shouldWindowBeTransparent",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    try{
                        Context c=(Context)XposedHelpers.getObjectField(p.thisObject,"mContext");
                        if(context==null)initialize(c);
                        if(!selected)return;
                        Object data=XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.oplus.systemui.aod.aodclock.constant.AodData",cl),"getInstance",c);
                        Object options=XposedHelpers.getObjectField(data,"mAodOptionsMgr");
                        boolean enabled=(boolean)XposedHelpers.callMethod(options,"isCurrentAodSwitchEnable");
                        Class<?> power=XposedHelpers.findClass("com.oplus.systemui.qs.observer.SuperPowerSaveSettingsObserver",cl);
                        Object observer=XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(power,"Companion"),"getInstance");
                        boolean saving=XposedHelpers.getBooleanField(observer,"isSuperPowerSaveState");
                        // Keep the official AOD switch and super-power-saving gates. Our service
                        // supplies the image independently of the stock clock theme's wallpaper list.
                        if(enabled&&!saving)p.setResult(true);
                    }catch(Throwable error){failure("transparency",error);}
                }
            });
        }catch(Throwable error){failure("transparency hook",error);}
        try{
            XposedBridge.hookAllMethods(controller,"updateCurrentUiState",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    try{
                    if(context==null)initialize((Context)XposedHelpers.getObjectField(p.thisObject,"mContext"));
                    if(!selected||context==null||p.args.length!=2||p.hasThrowable())return;
                    // The controller may reject a requested transition; use its accepted state.
                    String state=String.valueOf(XposedHelpers.getObjectField(p.thisObject,"currentUiState"));
                    int mode=state.equals("KEYGUARD")?1:state.equals("UNLOCK")?2:
                            state.equals("NORMAL_AOD")||state.equals("WORKSHOP_AOD")||state.equals("PANORAMIC_AOD")?0:-1;
                    if(mode>=0){
                        if(mode==0){if(Diagnostics.TRACE)XposedBridge.log("AliveClean: display transition supports="+XposedHelpers.getBooleanField(p.thisObject,"isSupportSmoothTransition")+" viaOff="+XposedHelpers.getBooleanField(p.thisObject,"gotoDozeWithOff"));}
                        boolean animate=(boolean)p.args[1];
                        long time=SystemClock.uptimeMillis();
                        dispatch(mode,animate,time,0,state);
                        {if(Diagnostics.TRACE)XposedBridge.log("AliveClean: scene="+state+" mode="+mode);}
                    }
                    }catch(Throwable error){failure("scene dispatch",error);}
                }
            });
        }catch(Throwable error){failure("scene hook",error);}
    }
    private static void dispatch(int mode,boolean animate,long time,int phase,String state){
        main.post(()->{
            if(!selected)return;
            stateOrder.accept(mode,time,phase);
            if(stateOrder.mode()!=mode)return;
            lastMode=mode;lastPhase=stateOrder.phase();
            updateContinuousAod();
            updateWallpaperColor(clocks.loader(),mode==0);
            clocks.scene(context,!aodClock.usesIndependentClock(),mode,state);
            aodClock.scene(mode,animate);
            Bundle b=new Bundle();b.putInt("mode",mode);b.putBoolean("animate",animate);b.putLong("time",time);b.putInt("phase",phase);b.putLong("clock_wake",aodClock.wakeToken());send(1,b);
        });
    }
    private static void installPanoramicHandoff(ClassLoader cl){
        try{
            Class<?> listener=XposedHelpers.findClass("com.oplus.systemui.keyguard.anim.OplusKeyguardAppearAnimControllerImpl$PanoramicAodAppearAnimRunner$playPanoramicRemoteAnimation$3",cl);
            XposedHelpers.findAndHookMethod(android.animation.ValueAnimator.class,"addUpdateListener",android.animation.ValueAnimator.AnimatorUpdateListener.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(!selected||!listener.isInstance(p.args[0]))return;
                    try{
                        // Adjust the native transaction's configuration once, before it starts.
                        // Native thermal/schedule gates, curves, surface ownership and cleanup remain.
                        XposedHelpers.setBooleanField(p.args[0],"$isThirdLiveWallpaperInOneComponent",false);
                        XposedHelpers.setBooleanField(p.args[0],"$isThirdLiveWallpaperOnlyLockscreenScene",false);
                        {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Panoramic native app/wallpaper fade enabled");}
                    }catch(Throwable error){failure("panoramic surface handoff",error);}
                }
            });
        }catch(Throwable error){failure("panoramic handoff hook",error);}
    }
    private static void installUnlockEvents(ClassLoader cl){
        try{
            Class<?> biometric=XposedHelpers.findClass("com.android.systemui.statusbar.phone.BiometricUnlockController",cl);
            XposedBridge.hookAllMethods(biometric,"startWakeAndUnlock",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(!selected||p.args.length!=2||!(p.args[0] instanceof Integer))return;
                    int mode=(int)p.args[0];
                    // Only the platform's already-authorized unlock modes, never detect/bouncer.
                    if(mode==1||mode==2||mode==5||mode==6||mode==7){
                        dispatch(2,true,SystemClock.uptimeMillis(),3,"UNLOCK");
                        {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Authenticated direct unlock mode="+mode);}
                    }
                }
            });
            XposedHelpers.findAndHookMethod("com.android.keyguard.KeyguardUpdateMonitor",cl,"setKeyguardGoingAway",boolean.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    if(selected&&!p.hasThrowable()&&(boolean)p.args[0])dispatch(2,true,SystemClock.uptimeMillis(),3,"UNLOCK");
                }
            });
        }catch(Throwable error){failure("unlock event hooks",error);}
        try{
            Class<?> adapter=XposedHelpers.findClass("com.oplus.keyguard.OplusKeyguardUnlockAnimationControllerExImpl",cl);
            XposedBridge.hookAllMethods(adapter,"surfaceBehindEntryAnimatorStartInit",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    if(!selected||p.hasThrowable())return;
                    try{
                        if(!(boolean)XposedHelpers.callMethod(p.thisObject,"isPanoramicAodInShowWhenFpUnlock"))return;
                        Object rule=XposedHelpers.getObjectField(p.thisObject,"curUnlockAnimRule");
                        if(rule==null||XposedHelpers.getBooleanField(rule,"launcherUnlockScene")
                                ||XposedHelpers.getBooleanField(rule,"oneShotAnim")||XposedHelpers.getBooleanField(rule,"isSkipUnlockAnim"))return;
                        // Flyme reveals a returning app over 600 ms instead of covering the lens immediately.
                        XposedHelpers.setBooleanField(rule,"behindShowImmediate",false);
                        XposedHelpers.setBooleanField(rule,"earlyExit",false);
                        XposedHelpers.setIntField(rule,"behindAnimDuration",600);
                        XposedHelpers.callMethod(rule,"setAnimInterpolator",new android.view.animation.PathInterpolator(.2f,0,0,1));
                        XposedHelpers.setBooleanField(p.thisObject,"shouldEarlyExit",false);
                        android.animation.ValueAnimator animator=(android.animation.ValueAnimator)XposedHelpers.callMethod(p.args[0],"getSurfaceBehindEntryAnimator");
                        animator.setDuration(600);
                        {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Direct app reveal duration=600 immediate=false");}
                    }catch(Throwable error){failure("direct app reveal",error);}
                }
            });
        }catch(Throwable error){failure("app reveal hook",error);}
    }
    private static void installAnimationWindow(ClassLoader cl){
        try{
            Class<?> display=XposedHelpers.findClass("com.oplus.systemui.aod.display.AODDisplayUtil",cl);
            XposedHelpers.findAndHookMethod(display,"requestScreenStateWhileDreamingStart",int.class,String.class,boolean.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    AodTransitionWindow window=AodTransitionWindow.prepare(selected,(String)p.args[1],(boolean)p.args[2],(int)p.args[0]);
                    if(window==null)return;
                    try{
                        // Native caller 5 protects the dreaming-start transition. Extending only
                        // the ON client allowed CPU suspend before its delayed DOZE callback ran.
                        // Native acquisition replaces the earlier caller-5 deadline and retains
                        // other callers, timeout limits and worker-thread release handling.
                        XposedHelpers.callMethod(p.thisObject,"acquireWakeLockInWorkThread",5,window.cpuMs);
                        p.args[0]=window.screenMs;
                        {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","AOD transition window screenMs="+window.screenMs+" cpuMs="+window.cpuMs);}
                    }catch(Throwable error){failure("animation CPU window",error);} // Keep original duration if CPU protection failed.
                }
            });
        }catch(Throwable error){failure("animation window hooks",error);}
    }
    private static void updateWallpaperColor(ClassLoader cl,boolean aod){
        if(localAodColor==aod||context==null)return;
        try{
            Class<?> dependency=XposedHelpers.findClass("com.android.systemui.DependencyEx",cl);
            Object surfaces=XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(dependency,"sDependency"),"getDependency",XposedHelpers.findClass("com.android.systemui.statusbar.phone.CentralSurfacesImpl",cl));
            android.view.View window=(android.view.View)XposedHelpers.callMethod(surfaces,"getNotificationShadeWindowView");
            if(window==null||window.getWindowToken()==null)return;
            // Verified EngineExtImpl command: x=1 disables the framework's extra night alpha.
            WallpaperManager.getInstance(context).sendWallpaperCommand(window.getWindowToken(),"wallpaper.support.local.dark.mode",aod?1:0,0,0,null);
            localAodColor=aod;
        }catch(Throwable error){failure("wallpaper AOD color",error);}
    }
    private static void installPanoramicMask(ClassLoader cl){
        try{
            Class<?> mask=XposedHelpers.findClass("com.oplus.systemui.keyguard.ui.interactor.OplusKgdMaskInteractor",cl);
            XposedHelpers.findAndHookMethod(mask,"backgroundMaskAnimTo$default",mask,float.class,
                    android.animation.TimeInterpolator.class,long.class,boolean.class,int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    try{
                        if(!selected||!"PANORAMIC".equals(String.valueOf(XposedHelpers.getObjectField(p.args[0],"maskState"))))return;
                        // The service already renders a black AOD background. Native SystemUI
                        // otherwise blacks out third-party live wallpapers and fades their surface.
                        // Keep its animator, power sequence and panel brightness controller intact.
                        p.args[1]=0f;p.args[4]=false;
                        {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Panoramic wallpaper mask transparent");}
                    }catch(Throwable error){failure("panoramic mask",error);}
                }
            });
            XposedHelpers.findAndHookMethod(mask,"getScreenOffAlphaInPanoramic",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    // The native bouncer-to-AOD path settles the same background without an animator.
                    if(selected&&!p.hasThrowable())p.setResult(0f);
                }
            });
        }catch(Throwable error){failure("panoramic mask hook",error);}
    }
    private static void installPowerEvents(ClassLoader cl,Class<?> controller){
        try{
            Class<?> mediator=XposedHelpers.findClass("com.oplus.systemui.keyguard.OplusKeyguardViewMediatorExImpl",cl);
            XposedHelpers.findAndHookMethod(mediator,"onStartedGoingToSleep",int.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    if(!selected||context==null||p.hasThrowable())return;
                    try{
                        Object display=XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.oplus.systemui.aod.display.AODDisplayUtil",cl),"getInstance",context);
                        // The original callback chooses this after call, battery, privacy and AOD gates.
                        int type=XposedHelpers.getIntField(display,"mPerformAodType");
                        if(type<1||type>3)return;
                        {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Sleep begin aodType="+type+" process="+XposedHelpers.getIntField(display,"mAODProcessType")+" keyguard="+XposedHelpers.getBooleanField(display,"mKgShowingWhileGoingToSleep")+" shade="+XposedHelpers.getBooleanField(display,"mWindowAlreadyShown"));}
                        dispatch(0,true,SystemClock.uptimeMillis(),1,type==3?"PANORAMIC_AOD":type==2?"WORKSHOP_AOD":"NORMAL_AOD");
                    }catch(Throwable error){failure("sleep event",error);}
                }
            });
            XposedHelpers.findAndHookMethod(mediator,"onStartedWakingUp",int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(!selected||context==null)return;
                    try{
                        Object transition=XposedHelpers.callStaticMethod(controller,"getInstance",context);
                        String state=String.valueOf(XposedHelpers.callMethod(transition,"getPowerOnUiState"));
                        dispatch(state.equals("UNLOCK")?2:1,true,SystemClock.uptimeMillis(),2,state);
                    }catch(Throwable error){failure("wake event",error);}
                }
            });
        }catch(Throwable error){failure("power hooks",error);}
    }
    private static synchronized void initialize(Context c) {
        ColorOsNativeClockFonts.systemUiStarted(c);
        if(context!=null)return;context=c.getApplicationContext();if(context==null)context=c;
        try{wallpaperUid=context.getPackageManager().getApplicationInfo("org.aliveclean",0).uid;}catch(android.content.pm.PackageManager.NameNotFoundException error){failure("wallpaper identity",error);}
        HandlerThread thread=new HandlerThread("AliveWallpaperObserver");thread.start();worker=new Handler(thread.getLooper());
        IntentFilter filter=new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
        filter.addAction("android.intent.action.USER_SWITCHED");
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        BroadcastReceiver updates=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(Intent.ACTION_USER_UNLOCKED.equals(i.getAction()))ColorOsNativeClockFonts.systemUiStarted(c);
            refresh();
        }};
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(updates,filter,Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(updates,filter);
        refresh();
    }
    private static void updateContinuousAod(){
        continuousAod.configure(context,selected&&channel!=null&&continuousAodRequested&&lastMode==0);
    }
    private static void configure(Bundle reply){
        notificationEffects.configure(reply.getInt("notification_mode",0),reply.getInt("notification_seconds",10),reply.getString("notification_color","blue"),reply.getInt("notification_ring_color",NotificationOptions.RING_BLUE));
        int style=reply.getInt("aod",0);
        boolean continuous=reply.getBoolean("continuous_aod",false);
        main.post(()->{continuousAodRequested=continuous;updateContinuousAod();aodClock.configure(context,selected,style);if(aodClock.usesIndependentClock())clocks.scene(context,false,-1,"");});
    }
    private static void readConfiguration(){
        try{
            Bundle reply=context.getContentResolver().call(SceneProvider.CONFIGURATION,"configuration",null,null);
            if(reply!=null)configure(reply);
        }catch(Throwable error){main.post(()->{continuousAodRequested=false;updateContinuousAod();aodClock.configure(context,false,0);});failure("clock configuration",error);}
    }
    private static void refresh(){worker.post(()->{
        try{
            if(!observingConfiguration)try{
                context.getContentResolver().registerContentObserver(SceneProvider.CONFIGURATION,false,new android.database.ContentObserver(worker){
                    @Override public void onChange(boolean self){readConfiguration();}
                });observingConfiguration=true;
            }catch(Throwable error){failure("clock configuration observer",error);}
            WallpaperManager manager=WallpaperManager.getInstance(context);
            WallpaperInfo info;
            if(Build.VERSION.SDK_INT>=34)info=manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
            else info=manager.getWallpaperInfo();
            if(info==null&&manager.getWallpaperId(WallpaperManager.FLAG_LOCK)<0)info=manager.getWallpaperInfo();
            selected=info!=null&&"org.aliveclean".equals(info.getPackageName());
            {if(Diagnostics.TRACE)XposedBridge.log("AliveClean: lock wallpaper selected="+selected);}
            if(selected){connect();readConfiguration();}
            else main.post(()->{notificationEffects.configure(0,10,"blue");updateContinuousAod();aodClock.configure(context,false,0);clocks.scene(context,false,-1,"");stateOrder.disconnect();lastMode=-1;lastPhase=0;synchronized(ColorOsBridge.class){connectAttempts=0;}});
        }catch(Throwable error){selected=false;main.post(()->{continuousAodRequested=false;updateContinuousAod();aodClock.configure(context,false,0);});failure("wallpaper selection",error);}
    });}
    private static synchronized void connect(){
        if(connecting||channel!=null||worker==null||!selected)return;connecting=true;
        worker.post(()->{
            try{
                Bundle request=new Bundle();request.putBinder("owner",owner);request.putBinder("clock_feedback",clockFeedback.getBinder());request.putInt("clock_api",clockApi?4:0);request.putString("clock_error",aodClock.installError());
                Bundle reply=context.getContentResolver().call(android.net.Uri.parse("content://org.aliveclean.scene"),"connect",null,request);
                if(reply==null||reply.getBinder("channel")==null)throw new IllegalStateException("Missing scene channel");
                IBinder binder=reply.getBinder("channel");
                binder.linkToDeath(()->{channel=null;main.post(()->{updateContinuousAod();aodClock.rendererLost();});if(selected)worker.postDelayed(ColorOsBridge::connect,500);},0);
                channel=new Messenger(binder);
                synchronized(ColorOsBridge.class){connectAttempts=0;}
                configure(reply);
                {if(Diagnostics.TRACE)XposedBridge.log("AliveClean: scene channel connected; clock_api="+(clockApi?4:0));}
                main.post(()->{if(lastMode>=0){Bundle b=new Bundle();b.putInt("mode",lastMode);b.putBoolean("animate",false);b.putInt("phase",lastPhase);b.putLong("time",SystemClock.uptimeMillis());b.putLong("clock_wake",aodClock.wakeToken());send(1,b);}});
            }catch(Throwable error){
                failure("scene channel",error);
                if(selected){
                    int attempt;
                    synchronized(ColorOsBridge.class){attempt=++connectAttempts;}
                    if(attempt<=8)worker.postDelayed(ColorOsBridge::connect,Math.min(4000L,250L<<Math.min(attempt-1,4)));
                }
            }
            finally{synchronized(ColorOsBridge.class){connecting=false;}}
        });
    }
    static void send(int what,Bundle data){
        Messenger target=channel;
        if(target!=null)try{Message message=Message.obtain();message.what=what;message.setData(data);target.send(message);return;}
        catch(RemoteException error){channel=null;main.post(ColorOsBridge::updateContinuousAod);}
        if(context!=null){
            Intent event=new Intent(what==1?ACTION:ACTION_LAYOUT).setPackage("org.aliveclean").addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtras(data);
            context.sendBroadcast(event);connect();
        }
    }
    static void failure(String stage,Throwable error){if(failures.add(stage)){XposedBridge.log("AliveClean: ColorOS "+stage+" unavailable: "+error);android.util.Log.w("AliveClean","ColorOS "+stage+" unavailable",error);}}
}
