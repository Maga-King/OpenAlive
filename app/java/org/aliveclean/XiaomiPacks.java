package org.aliveclean;

import android.content.*;
import android.content.pm.*;
import android.content.res.Resources;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Built-in, checksum-pinned original runtime packs; no downloads or APK installation. */
final class XiaomiPacks {
    static final class Pack {
        final String id,title,digest; final long bytes; final int lands;
        final android.os.Bundle clock=new android.os.Bundle();
        Pack(JSONObject o)throws JSONException{id=o.getString("id");title=o.getString("title");digest=o.getString("sha256");bytes=o.getLong("bytes");lands=o.getInt("lands");
            JSONObject original=o.getJSONObject("clock");clock.putString("family",id);
            for(String key:new String[]{"clock_position_x","clock_position_y","dual_clock_position_x_anchor_right","dual_clock_position_y"})clock.putFloat(key,(float)original.getDouble(key));
            clock.putInt("support_change_with_time",original.optInt("support_change_with_time",0));
        }
        String pkg(){return "com.miui.miwallpaper."+id;}
        String component(){return "org.aliveclean.XiaomiWallpaper$"+Character.toUpperCase(id.charAt(0))+id.substring(1);}
    }
    private static List<Pack> catalog;
    static synchronized List<Pack> all(Context c)throws Exception{
        if(catalog==null){JSONArray a=new JSONArray(AssetGl.text(c.getAssets(),"xiaomi/catalog.json"));ArrayList<Pack> list=new ArrayList<>();for(int i=0;i<a.length();i++)list.add(new Pack(a.getJSONObject(i)));catalog=Collections.unmodifiableList(list);}return catalog;
    }
    static Pack get(Context c,String id)throws Exception{for(Pack p:all(c))if(p.id.equals(id))return p;throw new IOException(I18n.t("未知的小米壁纸"));}
    static File directory(Context c)throws IOException{File dir=new File(c.getFilesDir(),"xiaomi-packs");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException(I18n.t("无法创建壁纸目录"));return dir;}
    static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static File find(Context c,Pack p)throws Exception{
        File stored=new File(directory(c),p.digest+".apk");
        if(stored.isFile())try{verify(stored,p);return stored;}catch(IOException corrupt){}
        try{ApplicationInfo a=c.getPackageManager().getApplicationInfo(p.pkg(),0);File installed=new File(a.sourceDir);verify(installed,p);return installed;}
        catch(PackageManager.NameNotFoundException|IOException missingOrDifferentVersion){}
        return XiaomiBuiltinPacks.prepare(c,p);
    }
    static void verify(File f,Pack p)throws Exception{if(f.length()!=p.bytes||!p.digest.equals(hash(f)))throw new IOException(I18n.t("壁纸包版本不匹配：")+I18n.t(p.title));}
    static Resources resources(Context c,Pack p,File f)throws Exception{
        ApplicationInfo info=new ApplicationInfo();info.packageName=p.pkg();info.sourceDir=info.publicSourceDir=f.getPath();info.uid=android.os.Process.myUid();Resources resources=c.getPackageManager().getResourcesForApplication(info);
        if("org.aliveclean".equals(c.getPackageName()))I18n.localize(resources);return resources;
    }
    static int land(Context c,Pack p){android.os.Bundle b=new android.os.Bundle();b.putString("family",p.id);return Math.max(0,Math.min(p.lands-1,XiaomiState.call(c,"read",b).getInt("land",0)));}
    static boolean saveLand(Context c,Pack p,int land){android.os.Bundle b=new android.os.Bundle();b.putString("family",p.id);b.putInt("land",land);return XiaomiState.call(c,"save",b).getInt("land",-1)==land;}
}
