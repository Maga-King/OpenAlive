package org.aliveclean;

import android.app.WallpaperInfo;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Package-scoped wallpaper capability bridge. No checks run inside the render loop. */
public final class CleanHooks implements IXposedHookLoadPackage {
    private static boolean ambientHooked;
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p){
        if("com.oplus.wallpapers".equals(p.packageName))NativeClockApplyPolicy.install(p.classLoader);
        if("com.android.systemui".equals(p.packageName)||"com.oplus.wallpapers".equals(p.packageName))
            ColorOsNativeClockFonts.install(p.classLoader);
        if(!"android".equals(p.packageName)&&!"com.android.systemui".equals(p.packageName))return;
        if(!ambientHooked){
            XposedHelpers.findAndHookMethod(WallpaperInfo.class,"supportsAmbientMode",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam call){WallpaperInfo info=(WallpaperInfo)call.thisObject;if("org.aliveclean".equals(info.getPackageName()))call.setResult(true);}});
            ambientHooked=true;
        }
        if("android".equals(p.packageName)){
            // Verified against this phone's WallpaperManagerService and IPackageManagerBase Smali.
            // Binding checks this exact permission independently of supportsAmbientMode().
            Class<?> base=XposedHelpers.findClassIfExists("com.android.server.pm.IPackageManagerBase",p.classLoader);
            if(base!=null)XposedHelpers.findAndHookMethod(base,"checkPermission",String.class,String.class,int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam call){
                    if("android.permission.AMBIENT_WALLPAPER".equals(call.args[0])&&"org.aliveclean".equals(call.args[1]))call.setResult(android.content.pm.PackageManager.PERMISSION_GRANTED);
                }
            });
            else XposedBridge.log("AliveClean: ambient permission adapter unavailable on this framework");
        }else ColorOsBridge.install(p.classLoader);
        {if(Diagnostics.TRACE)XposedBridge.log("AliveClean: platform hooks v26 loaded in "+p.packageName);}
    }
}
