package org.aliveclean;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.res.*;
import android.view.*;
import java.io.*;
import java.security.MessageDigest;

/** Private resource namespace: official 0x7f IDs never enter the app's Resources. */
final class OfficialUi extends ContextWrapper implements AutoCloseable {
    private final Resources resources;
    private final Resources.Theme theme;
    private final ClassLoader loader;
    private LayoutInflater inflater;

    private static synchronized File unpack(Context context)throws Exception{
        String digest;
        try(InputStream in=context.getAssets().open("ui/editor-ui.sha256")){
            ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] b=new byte[256];for(int n;(n=in.read(b))!=-1;)data.write(b,0,n);
            digest=data.toString("US-ASCII").trim();
        }
        if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid UI bundle digest");
        File folder=new File(context.getCodeCacheDir(),"official-ui");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("UI cache unavailable");
        File file=new File(folder,digest+".apk");
        if(!file.isFile()){
            File part=File.createTempFile("ui-",".tmp",folder);
            try{
                MessageDigest hash=MessageDigest.getInstance("SHA-256");
                try(InputStream in=context.getAssets().open("ui/editor-ui.apk");FileOutputStream out=new FileOutputStream(part)){
                    if(!part.setReadOnly())throw new IOException("Cannot protect UI bytecode");
                    byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;){out.write(b,0,n);hash.update(b,0,n);}out.getFD().sync();
                }
                StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(!digest.contentEquals(actual))throw new IOException("UI bundle checksum mismatch");
                if(!part.renameTo(file))throw new IOException("Cannot store UI bundle");
            }finally{if(part.exists())part.delete();}
        }
        return file;
    }

    OfficialUi(Context base)throws Exception{
        this(base,false);
    }
    @SuppressWarnings("deprecation")
    OfficialUi(Context base,boolean forceDark)throws Exception{
        super(base);
        File file=unpack(base);
        // Original controls are an unchanged secondary DEX in this APK. This
        // also makes XML drawables available to Android's resource inflater.
        loader=base.getClassLoader();
        ApplicationInfo info=new ApplicationInfo();
        info.packageName="com.flyme.systemuieditor";
        info.uid=android.os.Process.myUid();
        info.targetSdkVersion=base.getApplicationInfo().targetSdkVersion;
        info.sourceDir=file.getPath();info.publicSourceDir=file.getPath();
        info.splitSourceDirs=null;info.splitPublicSourceDirs=null;
        info.sharedLibraryFiles=null;
        // Load an APK resource namespace through PackageManager, rather than
        // attaching a loader to the framework's special android context.
        Resources source=base.getPackageManager().getResourcesForApplication(info);
        resources=source;
        if(forceDark){
            Configuration config=new Configuration(source.getConfiguration());
            config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_YES;
            // Preserve PackageManager's application class loader for Flyme's
            // XML drawables. The public Resources constructor uses the boot
            // loader and cannot inflate MzPressAnimationDrawable on a cold start.
            // This resource namespace belongs only to the dark editor bundle;
            // the personalization home uses a separate APK and Resources.
            resources.updateConfiguration(config,source.getDisplayMetrics());
        }
        theme=resources.newTheme();theme.applyStyle(id("style","Theme.EditorActivity"),true);
    }
    int id(String type,String name){int id=resources.getIdentifier(name,type,"com.flyme.systemuieditor");if(id==0)throw new IllegalArgumentException(type+"/"+name);return id;}
    View inflate(String name,ViewGroup parent){return LayoutInflater.from(this).inflate(id("layout",name),parent,false);}
    View find(View root,String name){return root.findViewById(id("id",name));}
    @Override public Resources getResources(){return resources;}
    @Override public AssetManager getAssets(){return resources.getAssets();}
    @Override public ClassLoader getClassLoader(){return loader;}
    @Override public Resources.Theme getTheme(){return theme;}
    @Override public Context getApplicationContext(){return this;}
    @Override public Object getSystemService(String name){
        if(LAYOUT_INFLATER_SERVICE.equals(name)){
            if(inflater==null)inflater=LayoutInflater.from(getBaseContext()).cloneInContext(this);
            return inflater;
        }
        return super.getSystemService(name);
    }
    @Override public void close(){inflater=null;}
}
