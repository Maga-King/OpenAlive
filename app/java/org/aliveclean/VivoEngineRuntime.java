package org.aliveclean;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Isolated, unchanged Vivo rendering runtime. Open off the UI/GL threads. */
final class VivoEngineRuntime extends ContextWrapper {
    private static final String PREFIX = "vivo/runtime/";
    private static ClassLoader sharedLoader;
    private static String sharedCodePath;
    private final ClassLoader loader;
    private final Resources resources;
    private static java.util.concurrent.Future<VivoEngineRuntime> prepared;
    static synchronized java.util.concurrent.Future<VivoEngineRuntime> prepare(Context host){
        if(prepared==null){
            java.util.concurrent.FutureTask<VivoEngineRuntime> task=new java.util.concurrent.FutureTask<>(()->open(host.getApplicationContext()));
            prepared=task;new Thread(task,"VivoResources").start();
        }
        return prepared;
    }

    static VivoEngineRuntime open(Context host) throws Exception {
        File code = unpack(host, "engine.apk");
        File textures = unpack(host, "textures.zip");
        File pag=unpack(host,"libpag.so"),avc=unpack(host,"libffavc.so");
        File nativeDir=new File(host.getCodeCacheDir(),"vivo-native-"+pag.getName()+"-"+avc.getName());
        if(!nativeDir.isDirectory()&&!nativeDir.mkdirs())throw new IOException("Cannot prepare Vivo UI libraries");
        // Verified, immutable copies named for the original ELF DT_NEEDED entries.
        for(File source:new File[]{pag,avc}){
            File target=new File(nativeDir,source==pag?"libpag.so":"libffavc.so");
            try(InputStream expected=new FileInputStream(source)){
                boolean valid=false;
                if(target.isFile())try(InputStream current=new FileInputStream(target)){valid=hash(expected).equals(hash(current));}
                if(!valid){if(target.exists()&&!target.delete())throw new IOException("Cannot replace Vivo UI library");
                    try(InputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(target)){
                        target.setReadOnly();byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))!=-1;)output.write(buffer,0,n);output.getFD().sync();
                    }
                }
            }
        }
        return new VivoEngineRuntime(host, code, textures,nativeDir);
    }

    private VivoEngineRuntime(Context host, File code, File textures,File nativeDir) throws Exception {
        super(host);
        synchronized (VivoEngineRuntime.class) {
            if (sharedLoader == null || !code.getPath().equals(sharedCodePath)) {
                // Keep Vivo's bundled dependencies separate from Flyme and the app's UI libraries.
                sharedLoader = new DexClassLoader(code.getPath(), host.getCodeCacheDir().getPath(),
                        nativeDir.getPath(), Context.class.getClassLoader());
                sharedCodePath = code.getPath();
            }
            loader = sharedLoader;
        }
        AssetManager assets = AssetManager.class.getConstructor().newInstance();
        try {
            java.lang.reflect.Method add = AssetManager.class.getMethod("addAssetPath", String.class);
            if ((Integer) add.invoke(assets, code.getPath()) == 0
                    || (Integer) add.invoke(assets, textures.getPath()) == 0)
                throw new IOException("Vivo renderer resources unavailable");
            resources = new Resources(assets, host.getResources().getDisplayMetrics(),
                    "org.aliveclean".equals(host.getPackageName())
                            ? I18n.config(host.getResources().getConfiguration())
                            : host.getResources().getConfiguration());
        } catch (Exception error) {
            assets.close();
            throw error;
        }
    }

    private static synchronized File unpack(Context host, String name) throws Exception {
        String expected = AssetGl.text(host.getAssets(), PREFIX + name + ".sha256").trim();
        if (!expected.matches("[a-f0-9]{64}")) throw new IOException("Invalid Vivo runtime digest");
        File directory = new File(host.getCodeCacheDir(), "vivo-runtime");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Vivo runtime cache unavailable");
        File ready = new File(directory, expected + (name.endsWith(".apk") ? ".apk" : ".zip"));
        if (ready.isFile()) {
            try (InputStream input = new FileInputStream(ready)) {
                if (expected.equals(hash(input))) {
                    if (!ready.setReadOnly()) throw new IOException("Cannot protect Vivo runtime cache");
                    return ready;
                }
            }
            if (!ready.delete()) throw new IOException("Cannot replace damaged Vivo runtime cache");
        }
        File temporary = File.createTempFile("vivo-", ".tmp", directory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = host.getAssets().open(PREFIX + name);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                // Android requires dynamically loaded code to be read-only before it is populated.
                if (!temporary.setReadOnly()) throw new IOException("Cannot protect Vivo renderer code");
                byte[] buffer = new byte[65536];
                for (int n; (n = input.read(buffer)) != -1;) {
                    output.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                }
                output.getFD().sync();
            }
            if (!expected.equals(hex(digest.digest()))) throw new IOException("Vivo runtime checksum mismatch");
            if (!temporary.renameTo(ready)) throw new IOException("Cannot store Vivo renderer code");
            return ready;
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static String hash(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
        return hex(digest.digest());
    }

    private static String hex(byte[] digest) {
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    Class<?> originalClass(String name) throws ClassNotFoundException { return loader.loadClass(name); }
    @Override public ClassLoader getClassLoader() { return loader; }
    @Override public Resources getResources() { return resources; }
    @Override public AssetManager getAssets() { return resources.getAssets(); }
    @Override public Context getApplicationContext() { return this; }
}
