package org.aliveclean;

import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Observes the native opaque music layer; never changes its surfaces or blur lifecycle. */
final class ColorOsImmersiveBackdrop {
    private final ClassLoader loader;
    private final BooleanSupplier selected;
    private final Consumer<Boolean> delivery;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WeakReference<Object> controller=new WeakReference<>(null);
    private WeakReference<View> observedHost=new WeakReference<>(null);
    private boolean covered,failed;
    private int bridgeMode=1;
    private final Runnable refresh=this::refreshNow;
    private final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->refresh();
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        @Override public void onViewAttachedToWindow(View v){refresh();}
        @Override public void onViewDetachedFromWindow(View v){clear();}
    };

    ColorOsImmersiveBackdrop(ClassLoader loader,BooleanSupplier selected,Consumer<Boolean> delivery){
        this.loader=loader;this.selected=selected;this.delivery=delivery;
    }
    void install(){
        try{
            Class<?> type=XposedHelpers.findClass("com.oplus.systemui.keyguard.watcher.OplusKgdImmersiveController",loader);
            XposedBridge.hookAllConstructors(type,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){observe(p.thisObject);}
            });
            XC_MethodHook changed=new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){observe(p.thisObject);}
            };
            for(String name:new String[]{"attachHostSurfaceToWallpaper","setMusicArtViewToSurface",
                    "onMusicLockEnterAnimationFinished","setImmersiveWallpaperState","bindMusicArtView",
                    "abortPipelineCrossfade","updateUiStateInner","checkMatrix",
                    "relayoutImmersiveHostOnConfigChanged"})
                XposedBridge.hookAllMethods(type,name,changed);
            XC_MethodHook exiting=new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){clear();}
                @Override protected void afterHookedMethod(MethodHookParam p){observe(p.thisObject);}
            };
            for(String name:new String[]{"runMusicExitAnimation","releaseMusicSurface","stopMusicImmersiveWallpaper",
                    "finishMusicImmersiveExit","releaseMusicPipelineSilently","clearMusicImmersiveState",
                    "runPipelineCrossfade","onConfigChanged"})
                XposedBridge.hookAllMethods(type,name,exiting);
            XposedHelpers.findAndHookMethod(type,"setSurfaceAlpha",float.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if((Float)p.args[0]!=1f)clear();
                }
                @Override protected void afterHookedMethod(MethodHookParam p){observe(p.thisObject);}
            });
            XposedBridge.hookAllMethods(type,"setHostSurfaceAlpha",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){clear();}
                // Native helper is static; keep the controller captured by instance callbacks.
                @Override protected void afterHookedMethod(MethodHookParam p){refresh();}
            });
        }catch(Throwable error){fail(error);}
    }
    private void observe(Object value){controller=new WeakReference<>(value);refresh();}
    void scene(int mode){bridgeMode=mode;if(mode==2)clear();else refresh();}
    void refresh(){main.removeCallbacks(refresh);main.post(refresh);}
    void clear(){
        if(Looper.myLooper()!=main.getLooper()){main.post(this::clear);return;}
        main.removeCallbacks(refresh);publish(false);
    }
    private void refreshNow(){
        if(failed||!selected.getAsBoolean()){publish(false);return;}
        Object target=controller.get();if(target==null){publish(false);return;}
        try{publish(isCovered(target));}catch(Throwable error){fail(error);}
    }
    private boolean isCovered(Object target)throws ReflectiveOperationException{
        Object musicHost=XposedHelpers.getObjectField(target,"musicSurfaceControlViewHost");
        if(musicHost==null)return false;
        View view=(View)XposedHelpers.callMethod(target,"getMusicArtHostContainer");
        if(observedHost.get()!=view){
            View previous=observedHost.get();
            if(previous!=null){previous.removeOnLayoutChangeListener(layout);previous.removeOnAttachStateChangeListener(attachment);}
            observedHost=new WeakReference<>(view);view.addOnLayoutChangeListener(layout);view.addOnAttachStateChangeListener(attachment);
        }
        Object pkg=XposedHelpers.callMethod(musicHost,"getSurfacePackage");
        Object surface=pkg==null?null:XposedHelpers.callMethod(pkg,"getSurfaceControl");
        Object parent=XposedHelpers.getObjectField(target,"lastAttachedWallpaperSurfaceControl");
        boolean valid=surface!=null&&parent!=null&&(Boolean)XposedHelpers.callMethod(surface,"isValid")
                &&(Boolean)XposedHelpers.callMethod(parent,"isValid");
        Object keyguardLazy=XposedHelpers.getObjectField(target,"keyguardStateController");
        Object keyguard=XposedHelpers.callMethod(keyguardLazy,"get");
        // XposedHelpers only walks concrete superclasses; isVisible is an
        // inherited Java interface default on this ColorOS build.
        boolean showing=(Boolean)keyguard.getClass().getMethod("isVisible").invoke(keyguard);
        String scene=String.valueOf(XposedHelpers.getObjectField(target,"lastUiState"));
        // Native ScreenUtils sizes this host using real default-display metrics.
        // Resource metrics can exclude system bars even for this fullscreen host.
        android.view.Display display=view.getDisplay();
        if(display==null||display.getDisplayId()!=android.view.Display.DEFAULT_DISPLAY)return false;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();display.getRealMetrics(metrics);
        Object immersiveType=XposedHelpers.getObjectField(target,"currentImmersiveType");
        return ImmersiveBackdropPolicy.covered(selected.getAsBoolean(),
                Integer.valueOf(2).equals(immersiveType)&&"MUSIC_VIEW".equals(String.valueOf(XposedHelpers.getObjectField(target,"immersiveRenderMode"))),
                XposedHelpers.getIntField(target,"isImmersiveWallpaperState")==4,
                bridgeMode!=2&&showing&&("KEYGUARD".equals(scene)||"PANORAMIC_AOD".equals(scene)),
                view.isAttachedToWindow()&&view.isShown(),
                view.getBackground()!=null&&view.getBackground().getOpacity()==PixelFormat.OPAQUE,
                view.getWidth()==metrics.widthPixels&&view.getHeight()==metrics.heightPixels,
                valid,XposedHelpers.getBooleanField(target,"isPipelineCrossfading"),
                view.getAlpha(),XposedHelpers.getFloatField(target,"surfaceAlpha"));
    }
    private void publish(boolean next){
        if(covered==next)return;
        covered=next;delivery.accept(next);
        android.util.Log.i("AliveClean","Native music backdrop covered="+next);
    }
    private void fail(Throwable error){
        clear();
        if(!failed){failed=true;ColorOsBridge.failure("immersive backdrop observation",error);}
    }
}
