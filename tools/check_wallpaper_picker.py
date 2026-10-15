"""Exercise picker fallback/cancellation behavior on the host, without a device."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'android/content/ComponentName.java': '''package android.content;
public final class ComponentName {
    final String value; public ComponentName(String value){this.value=value;}
    public ComponentName(String pkg,String cls){this(pkg+"/"+cls);}
    public String getPackageName(){return value.contains("/")?value.substring(0,value.indexOf('/')):"";}
    public String getClassName(){return value.contains("/")?value.substring(value.indexOf('/')+1):value;}
    public boolean equals(Object o){return o instanceof ComponentName&&value.equals(((ComponentName)o).value);}
    public int hashCode(){return value.hashCode();}
}''',
    'android/content/ActivityNotFoundException.java': '''package android.content;
public class ActivityNotFoundException extends RuntimeException {
    public ActivityNotFoundException(String message){super(message);}
}''',
    'android/content/Intent.java': '''package android.content;
public class Intent {
    public static final String ACTION_SET_WALLPAPER="set";
    public final String action; public int flags; public Object component; public ComponentName destination;
    public Intent(String action){this.action=action;}
    public Intent(Intent other){action=other.action;flags=other.flags;component=other.component;destination=other.destination;}
    public Intent setComponent(ComponentName destination){this.destination=destination;return this;}
    public Intent putExtra(String key,Object value){component=value;return this;}
    public Intent setFlags(int flags){this.flags=flags;return this;}
}''',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
import android.content.Intent;
public class PackageManager {
    public static final int MATCH_ALL=131072;
    public final java.util.List<ResolveInfo> handlers=new java.util.ArrayList<>();
    public java.util.List<ResolveInfo> queryIntentActivities(Intent intent,int flags){return handlers;}
    public Intent launcher;
    public Intent getLaunchIntentForPackage(String name){return name.equals("com.oplus.wallpapers")?launcher:null;}
}''',
    'android/content/pm/ActivityInfo.java': '''package android.content.pm;
public class ActivityInfo {public String packageName,name;public boolean exported=true,enabled=true;}''',
    'android/content/pm/ResolveInfo.java': '''package android.content.pm;
public class ResolveInfo {public ActivityInfo activityInfo;}''',
    'android/widget/Toast.java': '''package android.widget;
public class Toast {
    public static final int LENGTH_LONG=1;
    public static Toast makeText(android.app.Activity a,String message,int duration){return new Toast();}
    public void show(){}
}''',
    'android/app/Activity.java': '''package android.app;
import android.content.*;import android.content.pm.PackageManager;import java.util.*;
public class Activity {
    public static final int RESULT_OK=-1,RESULT_CANCELED=0;
    public final PackageManager packages=new PackageManager();
    public final List<Intent> attempts=new ArrayList<>();
    public String available;public ComponentName explicit;public boolean security;
    public PackageManager getPackageManager(){return packages;}
    public void startActivityForResult(Intent intent,int request){
        if(request!=20)throw new AssertionError("request code lost");
        attempts.add(intent);if(intent.destination!=null?intent.destination.equals(explicit):intent.action.equals(available))return;
        if(security)throw new SecurityException();throw new ActivityNotFoundException("missing");
    }
}''',
    'android/app/WallpaperInfo.java': '''package android.app;
import android.content.ComponentName;
public class WallpaperInfo {
    final ComponentName component;public WallpaperInfo(ComponentName c){component=c;}
    public ComponentName getComponent(){return component;}
}''',
    'android/app/WallpaperManager.java': '''package android.app;
public class WallpaperManager {
    public static final String ACTION_CHANGE_LIVE_WALLPAPER="change",ACTION_LIVE_WALLPAPER_CHOOSER="live",EXTRA_LIVE_WALLPAPER_COMPONENT="component";
    public static final int FLAG_SYSTEM=1,FLAG_LOCK=2;
    public WallpaperInfo home,lock;public int homeId=10,lockId=20;
    public WallpaperInfo getWallpaperInfo(){return home;}
    public WallpaperInfo getWallpaperInfo(int flag){return lock;}
    public int getWallpaperId(int flag){return flag==FLAG_SYSTEM?homeId:lockId;}
}''',
    'android/os/Build.java': '''package android.os;
public class Build {public static class VERSION {public static int SDK_INT=35;}}''',
    'android/util/Log.java': '''package android.util;
public class Log {public static int w(String tag,String text,Throwable error){return 0;}}''',
    'org/aliveclean/WallpaperPickerTest.java': '''package org.aliveclean;
import android.app.*;import android.content.*;import android.os.Build;
public class WallpaperPickerTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        ComponentName expected=new ComponentName("alive");
        for(String target:new String[]{"change","live","set","launcher"}){
            Activity activity=new Activity();activity.available=target;
            activity.packages.launcher=new Intent("launcher").setFlags(0x10000000);
            boolean reliable=WallpaperPicker.open(activity,expected,20);
            check(reliable==target.equals("change"),"result contract: "+target);
            for(Intent attempt:activity.attempts)check(attempt.component==expected,"wallpaper component lost on fallback: "+target);
            check(activity.attempts.get(activity.attempts.size()-1).flags==0,"new task breaks result delivery");
            check(activity.attempts.size()==(target.equals("change")?1:target.equals("live")?3:target.equals("launcher")?4:5),"fallback order");
        }
        Activity routed=new Activity();
        android.content.pm.ResolveInfo handler=new android.content.pm.ResolveInfo();
        handler.activityInfo=new android.content.pm.ActivityInfo();handler.activityInfo.packageName="oem.picker";handler.activityInfo.name="oem.picker.Change";
        routed.packages.handlers.add(handler);routed.explicit=new ComponentName("oem.picker","oem.picker.Change");
        check(WallpaperPicker.open(routed,expected,20),"explicit component preview must be reliable");
        check(routed.attempts.size()==2&&routed.attempts.get(1).component==expected,"explicit retry lost service");
        Activity legacy=new Activity();legacy.explicit=new ComponentName("com.android.wallpaper.livepicker","com.android.wallpaper.livepicker.LiveWallpaperChange");
        check(WallpaperPicker.open(legacy,expected,20)&&legacy.attempts.size()==2,"missing OEM intent filter should use explicit change activity");
        handler.activityInfo.exported=false;
        Activity privateEntry=new Activity();privateEntry.packages.handlers.add(handler);privateEntry.available="live";
        check(!WallpaperPicker.open(privateEntry,expected,20)&&privateEntry.attempts.size()==3,"must not invoke private activities");
        Activity secured=new Activity();secured.available="live";secured.security=true;
        check(!WallpaperPicker.open(secured,expected,20)&&secured.attempts.size()==3,"security rejection should fall back");
        try{WallpaperPicker.open(new Activity(),expected,20);throw new AssertionError("missing picker accepted");}
        catch(ActivityNotFoundException correct){}
        WallpaperManager manager=new WallpaperManager();manager.home=new WallpaperInfo(expected);
        check(!WallpaperPicker.applied(manager,expected,0,true,10,20),"cancel must not commit an existing wallpaper");
        check(WallpaperPicker.applied(manager,expected,-1,true,10,20),"explicit reapply should commit even with unchanged ID");
        check(!WallpaperPicker.applied(manager,expected,-1,false,10,20),"generic hub OK is not apply confirmation");
        manager.homeId=11;
        check(WallpaperPicker.applied(manager,expected,0,false,10,20),"changed home should commit without result");
        manager.home=new WallpaperInfo(new ComponentName("other"));
        check(!WallpaperPicker.applied(manager,expected,-1,true,10,20),"another wallpaper must not commit");
        manager.lock=new WallpaperInfo(expected);manager.lockId=21;
        check(WallpaperPicker.applied(manager,expected,0,false,10,20),"lock-only apply should commit");
        manager.lock=new WallpaperInfo(new ComponentName("other"));
        check(!WallpaperPicker.applied(manager,expected,0,false,10,20),"changed unrelated lock must not commit");
        Build.VERSION.SDK_INT=33;manager.home=new WallpaperInfo(expected);manager.homeId=10;
        check(!WallpaperPicker.applied(manager,expected,0,true,10,20),"legacy cancel must not commit");
        WallpaperInfo alive=new WallpaperInfo(new ComponentName("org.aliveclean/org.aliveclean.CleanWallpaper"));
        WallpaperInfo other=new WallpaperInfo(new ComponentName("other/other.Service"));
        Build.VERSION.SDK_INT=35;manager.home=other;manager.lock=alive;manager.lockId=21;
        check(AppliedWallpaper.supported(AppliedWallpaper.lock(manager)),"lock-only Alive must configure AOD");
        manager.home=alive;manager.lock=other;
        check(!AppliedWallpaper.supported(AppliedWallpaper.lock(manager)),"home-only Alive must not change another lock wallpaper's AOD");
        manager.lock=null;
        check(!AppliedWallpaper.supported(AppliedWallpaper.lock(manager)),"independent static lock must not inherit home AOD");
        manager.lockId=-1;
        check(AppliedWallpaper.supported(AppliedWallpaper.lock(manager)),"shared lock must inherit home");
        for(String family:new String[]{"Earth","Moon","Mars","Saturn","Snowmountain","Geometry"})
            check(AppliedWallpaper.supported(new WallpaperInfo(new ComponentName("org.aliveclean/org.aliveclean.XiaomiWallpaper$"+family))),"supported Xiaomi component: "+family);
        check(!AppliedWallpaper.supported(new WallpaperInfo(new ComponentName("other/org.aliveclean.CleanWallpaper"))),"component in wrong package");
        check(!AppliedWallpaper.supported(new WallpaperInfo(new ComponentName("org.aliveclean/org.aliveclean.UnknownWallpaper"))),"unknown component in app package");
        Build.VERSION.SDK_INT=33;
        check(AppliedWallpaper.supported(AppliedWallpaper.lock(manager)),"legacy shared lock");
        System.out.println("WALLPAPER_PICKER_OK");
    }
}''',
}


def main():
    java_bin = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17' if os.name == 'nt'
                                   else '/usr/lib/jvm/java-17-openjdk-amd64')) / 'bin'
    suffix = '.exe' if os.name == 'nt' else ''
    javac = str(java_bin / ('javac' + suffix)) if java_bin.is_dir() else shutil.which('javac')
    java = str(java_bin / ('java' + suffix)) if java_bin.is_dir() else shutil.which('java')
    with tempfile.TemporaryDirectory(prefix='openalive-picker-') as temporary:
        out = Path(temporary)
        sources = []
        for name, source in STUBS.items():
            path = out / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding='utf8')
            sources.append(path)
        subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(out),
                        *map(str, sources), str(ROOT / 'app/java/org/aliveclean/WallpaperPicker.java'),
                        str(ROOT / 'app/java/org/aliveclean/AppliedWallpaper.java')], check=True)
        subprocess.run([java, '-cp', str(out), 'org.aliveclean.WallpaperPickerTest'], check=True)


if __name__ == '__main__':
    main()
