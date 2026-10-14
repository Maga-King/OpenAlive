package org.aliveclean;

import android.app.Application;
import android.content.*;
import android.os.Build;
import de.robv.android.xposed.*;

/** Only loaded in the injected SystemUI process, never in the settings app. */
final class ModuleActivationHook {
    private static boolean registered;
    static void install(){
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam call){
                Context context=(Context)call.thisObject;
                if(!"com.android.systemui".equals(context.getPackageName())||registered)return;
                registered=true;
                BroadcastReceiver receiver=new BroadcastReceiver(){
                    @Override public void onReceive(Context c,Intent intent){ModuleActivation.announce(context);}
                };
                IntentFilter filter=new IntentFilter(ModuleActivation.QUERY);
                if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
                else context.registerReceiver(receiver,filter);
                ModuleActivation.announce(context);
            }
        });
    }
    private ModuleActivationHook(){}
}
