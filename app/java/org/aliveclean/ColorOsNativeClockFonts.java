package org.aliveclean;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Extends native digital-clock fonts; ColorOS owns selection, persistence and rendering. */
final class ColorOsNativeClockFonts {
    private static final String PLUGIN = "com.oplus.keyguard.personality.clocks";
    private static final String COMMON = "com.oplus.keyguard.clock.common.util.CommonUtils";
    private static final String CACHE = "com.oplus.keyguard.clock.digital.util.FontTypefaceCache";
    private static final Set<ClassLoader> installed = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean watching;
    private static boolean startupObserved;
    private static boolean startupComplete;
    private static final Set<Class<?>> observedServices=Collections.newSetFromMap(new IdentityHashMap<>());

    static void systemUiStarted(Context host) {
        Context application=host.getApplicationContext();
        Context app=application==null?host:application;
        if(!"com.android.systemui".equals(app.getPackageName()))return;
        synchronized(ColorOsNativeClockFonts.class){
            if(startupComplete){NativeClockAvailability.announce(app);return;}
            if(startupObserved)return;
            startupObserved=true;
        }
        NativeClockLoadState.startup("SystemUI context available");
        new Thread(()->{
            try{
                Context plugin=app.createPackageContext(PLUGIN,
                        Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
                NativeClockLoadState.startup("Clock package context created");
                if(attach(plugin.getClassLoader())){
                    synchronized(ColorOsNativeClockFonts.class){startupComplete=true;}
                    NativeClockLoadState.startup("Clock factory hooks attached");
                    NativeClockAvailability.announce(app);
                }
            }catch(Throwable unavailable){
                NativeClockLoadState.failure("Clock startup initialization",unavailable);
                android.util.Log.w("OpenAliveClock","Clock startup initialization failed",unavailable);
            }finally{
                synchronized(ColorOsNativeClockFonts.class){startupObserved=false;}
            }
        },"OpenAlive-clock-bootstrap").start();
    }

    private static void observeSystemUiService(ClassLoader loader) {
        // SystemUI can hand us its stub loader first and its implementation loader
        // later. Resolve this on each callback, independently of the Context hooks.
        Class<?> service=XposedHelpers.findClassIfExists("com.android.systemui.SystemUIService",loader);
        if(service==null||observedServices.contains(service))return;
        try{
            XposedHelpers.findAndHookMethod(service,"onCreate",new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam call){
                    if(!call.hasThrowable())systemUiStarted((Context)call.thisObject);
                }
            });
            XposedHelpers.findAndHookMethod(service,"dump",java.io.FileDescriptor.class,
                    java.io.PrintWriter.class,String[].class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam call){
                    String[] args=(String[])call.args[2];
                    if(args==null||args.length!=1||!"openalive-clock".equals(args[0]))return;
                    java.io.PrintWriter out=(java.io.PrintWriter)call.args[1];
                    android.os.Bundle state=NativeClockLoadState.snapshot();
                    out.println("OpenAlive clock runtime (SystemUI)");
                    for(String key:state.keySet())out.println(key+"="+state.get(key));
                    call.setResult(null);
                }
            });
            observedServices.add(service);
        }catch(Throwable unavailable){
            NativeClockLoadState.failure("Clock SystemUI lifecycle hook",unavailable);
            android.util.Log.w("OpenAliveClock","Clock SystemUI lifecycle hook unavailable",unavailable);
        }
    }

    static synchronized void install(ClassLoader hostLoader) {
        observeSystemUiService(hostLoader);
        if (watching) return;
        try {
            Class<?> contextImpl = XposedHelpers.findClass("android.app.ContextImpl", hostLoader);
            XC_MethodHook contextCreated = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam call) {
                    if (call.hasThrowable() || call.args.length == 0 || !PLUGIN.equals(call.args[0])
                            || !(call.getResult() instanceof Context)) return;
                    attach(((Context)call.getResult()).getClassLoader());
                }
            };
            XposedBridge.hookAllMethods(contextImpl, "createPackageContextAsUser", contextCreated);
            XposedBridge.hookAllMethods(contextImpl, "createPackageContext", contextCreated);
            // Covers late module/package callbacks too. Posting once lets the
            // application finish being created; this is not a polling loop.
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                Context app=android.app.AndroidAppHelper.currentApplication();
                if(app!=null)systemUiStarted(app);
            });
            watching = true;
        } catch (Throwable unavailable) {
            NativeClockLoadState.failure("Clock package loader hook",unavailable);
            android.util.Log.w("OpenAliveClock","Clock package loader hook unavailable",unavailable);
        }
    }

    private static synchronized boolean attach(ClassLoader loader) {
        if (installed.contains(loader)) return true;
        List<XC_MethodHook.Unhook> added = new ArrayList<>();
        try {
            // Resolve the entire supported contract before offering a selectable entry.
            Class<?> common = Class.forName(COMMON, false, loader);
            Class<?> cache = Class.forName(CACHE, false, loader);
            Class<?> key = Class.forName(CACHE + "$Key", false, loader);
            Class<?> preview = Class.forName("com.oplus.keyguard.clock.common.util.KStyleUtil", false, loader);
            Method fonts = common.getDeclaredMethod("getFontPath", Context.class, int.class);
            Method commonBuild = common.getDeclaredMethod("buildTypeFace", Context.class, String.class, int.class, int.class);
            Method cacheBuild = cache.getDeclaredMethod("buildTypeFace", Context.class, String.class, int.class, int.class);
            Method previewBuild = preview.getDeclaredMethod("getClockNumFont", Context.class, String.class);
            Method preload = cache.getDeclaredMethod("doBuildTypefaceCache", Context.class, key);
            Method family = key.getDeclaredMethod("getFamilyName");
            Assets assets = new Assets();
            XC_MethodHook selectedFont = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam call) {
                    if (NativeClockFonts.contains(call.args[1])) {
                        Context replacement = assets.wrap((Context)call.args[0]);
                        if (replacement != null) call.args[0] = replacement;
                    }
                }
            };
            for (Method method : new Method[]{commonBuild, cacheBuild, previewBuild}) {
                added.add(XposedBridge.hookMethod(method, selectedFont));
            }
            added.add(XposedBridge.hookMethod(preload, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam call) throws Throwable {
                    if (NativeClockFonts.contains(family.invoke(call.args[1]))) {
                        Context replacement = assets.wrap((Context)call.args[0]);
                        if (replacement != null) call.args[0] = replacement;
                    }
                }
            }));
            added.add(XposedBridge.hookMethod(fonts, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam call) {
                    // Mode 2 is the native digital clock panel, not text/gallery clocks.
                    if (!call.hasThrowable() && Integer.valueOf(2).equals(call.args[1])
                            && call.getResult() instanceof String[] && assets.wrap((Context)call.args[0]) != null) {
                        call.setResult(NativeClockFonts.append((String[])call.getResult()));
                    }
                }
            }));
            if(!ColorOsNativeClockStyles.attach(loader)){
                for(XC_MethodHook.Unhook hook:added)hook.unhook();
                return false;
            }
            installed.add(loader);
            return true;
        } catch (Throwable unsupported) {
            for (XC_MethodHook.Unhook hook : added) hook.unhook();
            NativeClockLoadState.failure("Clock font contract",unsupported);
            android.util.Log.w("OpenAliveClock","Clock font contract unavailable",unsupported);
            return false;
        }
    }

    private static final class Assets {
        private AssetManager manager;
        private final WeakHashMap<Context, WeakReference<Context>> contexts = new WeakHashMap<>();
        synchronized Context wrap(Context base) {
            try {
                if (manager == null) {
                    AssetManager candidate = base.createPackageContext("org.aliveclean", 0).getAssets();
                    for (String path : NativeClockFonts.PATHS) {
                        try (InputStream in = candidate.open(path)) { if (in.read() < 0) return null; }
                    }
                    manager = candidate;
                }
                WeakReference<Context> reference = contexts.get(base);
                Context wrapped = reference == null ? null : reference.get();
                if (wrapped == null) {
                    wrapped = NativeClockFonts.withAssets(base, manager);
                    contexts.put(base, new WeakReference<>(wrapped));
                }
                return wrapped;
            } catch (Exception missing) {
                // A missing package during boot/update is transient. Retry on
                // the next native font request, rather than disabling fonts for
                // the whole lifetime of SystemUI/the editor.
                NativeClockLoadState.failure("Clock font assets",missing);
                if (Diagnostics.TRACE) XposedBridge.log("OpenAlive: native clock font assets unavailable: " + missing);
                return null;
            }
        }
    }

    private ColorOsNativeClockFonts() {}
}
