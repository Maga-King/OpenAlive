package org.aliveclean;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.res.*;
import android.util.*;
import android.view.*;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.lang.reflect.*;

/** Unmodified Xiaomi layouts, styles, drawables and controls, isolated from Flyme. */
final class XiaomiUi extends ContextWrapper {
    final ClassLoader loader; final Resources resources; final Resources.Theme theme; private final boolean module;
    private LayoutInflater inflater;
    static synchronized File bundle(Context c)throws Exception {
        String digest=AssetGl.text(c.getAssets(),"xiaomi/ui.sha256").trim();
        if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid Xiaomi UI digest");
        File dir=new File(c.getCodeCacheDir(),"xiaomi-ui");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create UI cache");
        File file=new File(dir,digest+".apk");
        if(!file.isFile()){
            File part=File.createTempFile("ui-",".part",dir);
            try{
                try(InputStream in=c.getAssets().open("xiaomi/ui.apk");FileOutputStream out=new FileOutputStream(part)){
                    if(!part.setReadOnly())throw new IOException("Cannot protect UI code");byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);out.getFD().sync();
                }
                if(!digest.equals(XiaomiPacks.hash(part)))throw new IOException("Xiaomi UI checksum mismatch");
                if(!part.renameTo(file))throw new IOException("Cannot save UI code");
            }finally{if(part.exists())part.delete();}
        }
        return file;
    }
    XiaomiUi(Context host)throws Exception {this(host,Context.class.getClassLoader());}
    XiaomiUi(Context host,ClassLoader platform)throws Exception {
        super(host);File apk=bundle(host);
        loader=new DexClassLoader(apk.getPath()+File.pathSeparator+host.getApplicationInfo().sourceDir,host.getCodeCacheDir().getPath(),null,platform);
        ApplicationInfo info=new ApplicationInfo();info.packageName="com.android.thememanager";info.uid=android.os.Process.myUid();info.sourceDir=info.publicSourceDir=apk.getPath();
        Resources original=host.getPackageManager().getResourcesForApplication(info);
        // Own Resources instance: never alter a cached PackageManager resource's loader.
        module="org.aliveclean".equals(host.getPackageName());
        resources=new Resources(original.getAssets(),original.getDisplayMetrics(),module?I18n.config(original.getConfiguration()):original.getConfiguration());
        if(module)LocaleOverlay.attach(host,resources,"com.android.thememanager");
        Field field=Resources.class.getDeclaredField("mClassLoader");field.setAccessible(true);field.set(resources,loader);
        theme=resources.newTheme();theme.setTo(host.getTheme());theme.applyStyle(id("style","AppTheme.NoTitle.Translucent.SuperWallpaperPreview"),true);
        Class<?> app=loader.loadClass("com.android.thememanager.ThemeApplication");Object instance=app.getConstructor().newInstance();
        Method attach=app.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(instance,this);
        loader.loadClass("com.android.thememanager.basemodule.context.AppContextManager").getMethod("n7h",Context.class).invoke(null,this);
    }
    int id(String type,String name){int value=resources.getIdentifier(name,type,"com.android.thememanager");if(value==0)throw new IllegalArgumentException(type+"/"+name);return value;}
    View find(View root,String name){return root.findViewById(id("id",name));}
    View inflate(String name){return LayoutInflater.from(this).inflate(id("layout",name),null,false);}
    static Object call(Object target,String name,Class<?>[] types,Object... values)throws Exception {
        try{return (target instanceof Class?(Class<?>)target:target.getClass()).getMethod(name,types).invoke(target instanceof Class?null:target,values);}
        catch(InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}
    }
    @Override public Resources getResources(){if(module)I18n.ensure(resources);return resources;}
    @Override public AssetManager getAssets(){return resources.getAssets();}
    @Override public Resources.Theme getTheme(){return theme;}
    @Override public ClassLoader getClassLoader(){return loader;}
    @Override public Context getApplicationContext(){return this;}
    @Override public Object getSystemService(String name){
        if(!LAYOUT_INFLATER_SERVICE.equals(name))return super.getSystemService(name);
        if(inflater==null){inflater=LayoutInflater.from(getBaseContext()).cloneInContext(this);inflater.setFactory2(new LayoutInflater.Factory2(){
            public View onCreateView(String n,Context c,AttributeSet a){return onCreateView(null,n,c,a);}
            public View onCreateView(View p,String n,Context c,AttributeSet a){
                if(!n.contains(".")||n.startsWith("android."))return null;
                try{return (View)loader.loadClass(n).getConstructor(Context.class,AttributeSet.class).newInstance(c,a);}catch(Exception e){throw new InflateException(n,e);}
            }
        });}return inflater;
    }
}
