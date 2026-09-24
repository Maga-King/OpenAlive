package org.aliveclean;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.view.LayoutInflater;
import dalvik.system.DexClassLoader;
import java.io.File;

/** Private original HyperOS runtime; only Android framework classes are shared. */
final class OfficialHyperOsUi extends ContextWrapper {
    private static ClassLoader sharedLoader;
    private static String sharedPath;
    private final Resources resources;
    private final Resources.Theme theme;
    private final ClassLoader loader;
    private android.graphics.Typeface neumatic;
    private LayoutInflater inflater;

    OfficialHyperOsUi(Context host,File protectedApk)throws Exception{
        super(host);
        miui.util.font.MultiLangHelper.configure(NativeClockRuntime.globalFont(host).getPath());
        loader=loader(host,protectedApk);
        ApplicationInfo info=new ApplicationInfo();info.packageName="com.miui.aod";info.uid=android.os.Process.myUid();
        info.sourceDir=protectedApk.getPath();info.publicSourceDir=protectedApk.getPath();info.targetSdkVersion=35;
        resources=host.getPackageManager().getResourcesForApplication(info);
        theme=resources.newTheme();theme.applyStyle(android.R.style.Theme_Material_NoActionBar,true);
        loadOriginalSystemFonts(host.getAssets());
    }
    private static synchronized ClassLoader loader(Context host,File apk){
        String path=apk.getAbsolutePath();
        if(sharedLoader==null||!path.equals(sharedPath)){
            // Share only the missing font-file service with this isolated APK.
            // All other dependencies still resolve against the Android framework.
            ClassLoader dependencies=new ClassLoader(Context.class.getClassLoader()){
                @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                    if(name.equals("miui.util.font.MultiLangHelper"))return miui.util.font.MultiLangHelper.class;
                    return super.loadClass(name,resolve);
                }
            };
            sharedLoader=new DexClassLoader(path,host.getCodeCacheDir().getPath(),null,dependencies);
            sharedPath=path;
        }
        return sharedLoader;
    }
    static OfficialHyperOsUi open(Context host)throws Exception{
        return new OfficialHyperOsUi(host,NativeClockRuntime.unpack(host,"hyperos"));
    }
    @SuppressWarnings("unchecked")
    private void loadOriginalSystemFonts(AssetManager moduleAssets)throws Exception{
        neumatic=new android.graphics.Typeface.Builder(moduleAssets,
                "native-clock/runtime/hyperos/fonts/NeumaticCompressed.otf").build();
        if(neumatic==null)throw new IllegalStateException("Original Neumatic font missing");
        Class<?> fonts=loader.loadClass("com.miui.clock.utils.FontUtils");
        java.util.Map<String,android.graphics.Typeface> cache=(java.util.Map<String,android.graphics.Typeface>)
                fonts.getField("mAllInOneFontCache").get(null);
        java.util.Map<String,String> names=(java.util.Map<String,String>)fonts.getField("ALLINONE_FONT_FILE_MAP").get(null);
        for(java.util.Map.Entry<String,String> entry:names.entrySet()){
            if(cache.containsKey(entry.getKey()))continue;
            // These three-axis clock fonts live in HyperOS /product/fonts,
            // not in MIUIAod.apk. Populate only this isolated loader's cache.
            android.graphics.Typeface face=new android.graphics.Typeface.Builder(moduleAssets,
                    "native-clock/runtime/hyperos/fonts/"+entry.getValue()).build();
            if(face==null)throw new IllegalStateException("Original HyperOS clock font missing: "+entry.getValue());
            cache.put(entry.getKey(),face);
        }
        java.util.Map<String,android.graphics.Typeface> other=(java.util.Map<String,android.graphics.Typeface>)
                fonts.getField("mOtherFontTypefaceMap").get(null);
        // The original getter now has a real DE font path and keeps ownership
        // of its final ConcurrentHashMap, including locale/weight cache entries.
        if(fonts.getMethod("getMiSansGlobal").invoke(null)==null)
            throw new IllegalStateException("Original MiSans global font missing");
        for(String locale:new String[]{"SC","TC"}){
            String key="miclock-miserif-"+locale.toLowerCase(java.util.Locale.ROOT)+"-vf";
            if(other.containsKey(key))continue;
            android.graphics.Typeface face=new android.graphics.Typeface.Builder(moduleAssets,
                    "native-clock/runtime/hyperos/fonts/MiSerif"+locale+"VF.ttf").build();
            if(face==null)throw new IllegalStateException("Original HyperOS Chinese clock font missing");
            other.put(key,face);
        }
        String[][] aesthetics={{"miclock-commuters-sans-mono","CommutersSansMono.otf"},
                {"miclock-bebas-neue-mono","BebasNeue-Mono.otf"}};
        for(String[] font:aesthetics){
            if(other.containsKey(font[0]))continue;
            android.graphics.Typeface face=new android.graphics.Typeface.Builder(moduleAssets,
                    "native-clock/runtime/hyperos/fonts/"+font[1]).build();
            if(face==null)throw new IllegalStateException("Original HyperOS aesthetics font missing: "+font[1]);
            other.put(font[0],face);
        }
    }
    android.graphics.Typeface originalSystemFamily(String family){
        return "miclock-neue-matic-compressed-black".equals(family)?neumatic:null;
    }
    @Override public Resources getResources(){return resources;}
    @Override public AssetManager getAssets(){return resources.getAssets();}
    @Override public Resources.Theme getTheme(){return theme;}
    @Override public ClassLoader getClassLoader(){return loader;}
    @Override public Context getApplicationContext(){return this;}
    @Override public Object getSystemService(String name){
        if(LAYOUT_INFLATER_SERVICE.equals(name)){
            if(inflater==null)inflater=LayoutInflater.from(getBaseContext()).cloneInContext(this);
            return inflater;
        }
        return super.getSystemService(name);
    }
}
