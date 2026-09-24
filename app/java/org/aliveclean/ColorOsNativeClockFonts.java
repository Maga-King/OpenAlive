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

    static synchronized void install(ClassLoader hostLoader) {
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
            watching = true;
        } catch (Throwable unavailable) {
            if (Diagnostics.TRACE) XposedBridge.log("OpenAlive: native clock package loader unavailable: " + unavailable);
        }
    }

    private static synchronized void attach(ClassLoader loader) {
        if (installed.contains(loader)) return;
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
            installed.add(loader);
            ColorOsNativeClockStyles.attach(loader);
        } catch (Throwable unsupported) {
            for (XC_MethodHook.Unhook hook : added) hook.unhook();
            if (Diagnostics.TRACE) XposedBridge.log("OpenAlive: native clock font contract unavailable: " + unsupported);
        }
    }

    private static final class Assets {
        private AssetManager manager;
        private boolean unavailable;
        private final WeakHashMap<Context, WeakReference<Context>> contexts = new WeakHashMap<>();
        synchronized Context wrap(Context base) {
            if (unavailable) return null;
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
                unavailable = true;
                if (Diagnostics.TRACE) XposedBridge.log("OpenAlive: native clock font assets unavailable: " + missing);
                return null;
            }
        }
    }

    private ColorOsNativeClockFonts() {}
}
