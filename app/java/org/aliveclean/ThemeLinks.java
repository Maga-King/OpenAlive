package org.aliveclean;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.widget.Toast;

/** Public actions advertised by the installed OPPO theme apps. */
final class ThemeLinks {
    static Intent resolve(Context context,boolean font){
        String[] actions=font?new String[]{"com.oplus.themestore.action.SET_FONT_INDIVIDUATION","com.oplus.themestore.action.SET_FONT","com.nearme.themespace.SET_FONT"}
            :new String[]{"com.oplus.themestore.action.SET_THEME","com.nearme.themespace.SET_THEME","com.oplus.themestore.basic.action.SET_THEME"};
        for(String pkg:new String[]{"com.heytap.themestore","com.oplus.themestore"})for(String action:actions){
            Intent intent=new Intent(action).setPackage(pkg);
            ResolveInfo found=context.getPackageManager().resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
            if(found!=null&&found.activityInfo!=null&&found.activityInfo.exported&&found.activityInfo.enabled
                    &&(found.activityInfo.permission==null||context.checkSelfPermission(found.activityInfo.permission)==PackageManager.PERMISSION_GRANTED))return intent;
        }
        return null;
    }
    static void open(Activity activity,Intent intent){
        try{activity.startActivity(intent);}catch(ActivityNotFoundException|SecurityException error){Toast.makeText(activity,"系统页面暂时无法打开",Toast.LENGTH_SHORT).show();}
    }
}
