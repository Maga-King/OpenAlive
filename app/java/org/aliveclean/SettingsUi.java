package org.aliveclean;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.res.*;
import android.util.*;
import android.view.*;
import android.widget.FrameLayout;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.security.MessageDigest;

/** The settings APK has different resource IDs and library versions from the editor. */
final class SettingsUi extends ContextWrapper {
    private static final String PACKAGE="com.meizu.wallpapersetting";
    private final Resources resources;
    private final Resources.Theme theme;
    private final ClassLoader loader;
    private LayoutInflater inflater;

    private static synchronized File bundle(Context base)throws Exception{
        String digest=AssetGl.text(base.getAssets(),"ui/settings-ui.sha256").trim();
        if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid settings UI digest");
        File folder=new File(base.getCodeCacheDir(),"settings-ui");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("UI cache unavailable");
        File file=new File(folder,digest+".apk");
        if(!file.isFile()){
            File part=File.createTempFile("ui-",".tmp",folder);
            try{
                MessageDigest hash=MessageDigest.getInstance("SHA-256");
                try(InputStream in=base.getAssets().open("ui/settings-ui.apk");FileOutputStream out=new FileOutputStream(part)){
                    if(!part.setReadOnly())throw new IOException("Cannot protect UI code");
                    byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;){out.write(buffer,0,n);hash.update(buffer,0,n);}out.getFD().sync();
                }
                StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(!digest.contentEquals(actual))throw new IOException("UI checksum mismatch");
                if(!part.renameTo(file))throw new IOException("Cannot store UI code");
            }finally{if(part.exists())part.delete();}
        }
        return file;
    }
    SettingsUi(Context base)throws Exception{
        super(base);File file=bundle(base);
        // Parent is the platform loader: the two official AndroidX/Flyme libraries
        // must not share classes with mismatched resource IDs.
        loader=new DexClassLoader(file.getPath(),base.getCodeCacheDir().getPath(),null,ClassLoader.getSystemClassLoader().getParent());
        ApplicationInfo info=new ApplicationInfo();info.packageName=PACKAGE;info.uid=android.os.Process.myUid();
        info.sourceDir=info.publicSourceDir=file.getPath();info.targetSdkVersion=base.getApplicationInfo().targetSdkVersion;
        resources=base.getPackageManager().getResourcesForApplication(info);
        theme=resources.newTheme();theme.applyStyle(id("style","Theme.WallpaperSetting"),true);
    }
    int id(String type,String name){int value=resources.getIdentifier(name,type,PACKAGE);if(value==0)throw new IllegalArgumentException(type+"/"+name);return value;}
    View find(View root,String name){return root.findViewById(id("id",name));}
    View inflate(String name,ViewGroup parent){return LayoutInflater.from(this).inflate(id("layout",name),parent,false);}
    int color(String attr){TypedValue value=new TypedValue();theme.resolveAttribute(id("attr",attr),value,true);return value.data;}
    boolean light(){return (resources.getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;}
    int pageColor(){return light()?android.graphics.Color.WHITE:color("colorSurface");}
    void pageBackground(View view){view.setBackgroundColor(pageColor());}
    void cardBackground(View view){
        // Keep the original rounded drawable distinct from the white page.
        if(!light()||view.getBackground()==null)return;
        android.graphics.drawable.Drawable background=view.getBackground().mutate();
        if(background instanceof android.graphics.drawable.GradientDrawable){
            // The wallpaper bar's native fill has 10% alpha. Tinting preserves
            // that alpha and washes the bar out against our white page.
            view.setBackgroundTintList(null);
            ((android.graphics.drawable.GradientDrawable)background).setColor(color("colorSurface"));
        }else view.setBackgroundTintList(ColorStateList.valueOf(color("colorSurface")));
    }
    @Override public Resources getResources(){return resources;}
    @Override public AssetManager getAssets(){return resources.getAssets();}
    @Override public Resources.Theme getTheme(){return theme;}
    @Override public ClassLoader getClassLoader(){return loader;}
    @Override public Context getApplicationContext(){return this;}
    @Override public Object getSystemService(String name){
        if(!LAYOUT_INFLATER_SERVICE.equals(name))return super.getSystemService(name);
        if(inflater==null){
            inflater=LayoutInflater.from(getBaseContext()).cloneInContext(this);
            inflater.setFactory2(new LayoutInflater.Factory2(){
                public View onCreateView(String name,Context context,AttributeSet attrs){return onCreateView(null,name,context,attrs);}
                public View onCreateView(View parent,String name,Context context,AttributeSet attrs){
                    // Its original onAttachedToWindow binds a Meizu-only service.
                    // The same slot is populated by our preview bindings instead.
                    if(name.equals("com.flyme.textureview.RemoteTextureView")||name.equals("org.libpag.PAGView"))return new FrameLayout(context,attrs);
                    if(!name.contains(".")||name.startsWith("android."))return null;
                    try{return (View)loader.loadClass(name).getConstructor(Context.class,AttributeSet.class).newInstance(context,attrs);}
                    catch(Exception e){throw new android.view.InflateException(name,e);}
                }
            });
        }
        return inflater;
    }
}
