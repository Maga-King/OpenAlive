package org.aliveclean;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Extra translations for an original UI bundle. The bundle stays unchanged;
 * a strings-only table with the same resource IDs is layered over it.
 */
final class LocaleOverlay {
    private static final Set<Resources> ATTACHED=Collections.newSetFromMap(new WeakHashMap<>());
    private LocaleOverlay(){}
    static synchronized void attach(Context host,Resources target,String pkg){
        if(Build.VERSION.SDK_INT<30||ATTACHED.contains(target)){I18n.localize(target);return;}
        try{
            File folder=new File(host.getCodeCacheDir(),"locale-overlay");
            if(!folder.isDirectory()&&!folder.mkdirs())return;
            long installed=host.getPackageManager().getPackageInfo(host.getPackageName(),0).lastUpdateTime;
            File file=new File(folder,pkg+"-"+installed+".apk");
            if(!file.isFile()){
                File part=File.createTempFile("overlay-",".tmp",folder);
                try{
                    try(InputStream in=host.getAssets().open("i18n/"+pkg+".apk");FileOutputStream out=new FileOutputStream(part)){
                        byte[] buffer=new byte[16384];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);out.getFD().sync();
                    }
                    if(!part.renameTo(file))return;
                }finally{if(part.exists())part.delete();}
            }
            ResourcesLoader loader=new ResourcesLoader();
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){
                loader.addProvider(ResourcesProvider.loadFromApk(fd));
            }
            target.addLoaders(loader);ATTACHED.add(target);
            // Adding a loader rebuilds the resource implementation with the system
            // configuration, so the chosen language has to be applied again.
            I18n.localize(target);
        }catch(Exception|LinkageError failure){Log.w("OpenAliveI18n","Translations unavailable for "+pkg,failure);}
    }
}
