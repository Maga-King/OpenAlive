package org.aliveclean;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import java.util.function.Function;

/** Platform-only factory boundary; vendor and module class loaders never share AndroidX. */
final class NativeClockProvider implements Function<String,Object> {
    private final Context context;
    private final Function<String,Object> original;

    NativeClockProvider(Context host,AssetManager assets,Function<String,Object> original){
        this.original=original;
        context=resourceContext(host,assets);
    }
    static Context resourceContext(Context host,AssetManager assets){
        // Only immutable bundled clock code/resources belong in DE storage.
        // Keep the host's configuration and private user data in their original
        // stores; do not change its application-wide default storage context.
        Context device=host.isDeviceProtectedStorage()?host:host.createDeviceProtectedStorageContext();
        return new ContextWrapper(host){
            private android.view.LayoutInflater inflater;
            @Override public java.io.File getCodeCacheDir(){return device.getCodeCacheDir();}
            @Override public AssetManager getAssets(){return assets;}
            @Override public ClassLoader getClassLoader(){return NativeClockProvider.class.getClassLoader();}
            @Override public Object getSystemService(String name){
                if(LAYOUT_INFLATER_SERVICE.equals(name)){
                    if(inflater==null)inflater=new NativeClockInflater(this);
                    return inflater;
                }
                return super.getSystemService(name);
            }
        };
    }
    static boolean contains(String id){
        return NativeFlymeClockPlugin.ID.equals(id)||NativeFlymeClockPlugin.HORIZONTAL_ID.equals(id)||NativeFlymeArtworkPlugin.contains(id)||NativeHyperOsClockPlugin.contains(id);
    }
    @Override public Object apply(String id){
        if(!contains(id))return original==null?null:original.apply(id);
        try{
            if(NativeHyperOsClockPlugin.contains(id))return new NativeHyperOsClockPlugin(context,id);
            if(NativeFlymeArtworkPlugin.contains(id))return new NativeFlymeArtworkPlugin(context,id);
            return new NativeFlymeClockPlugin(context,id);
        }
        catch(Exception error){throw new IllegalStateException("Original clock resources could not be loaded",error);}
    }
}
