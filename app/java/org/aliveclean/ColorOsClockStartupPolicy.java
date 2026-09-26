package org.aliveclean;

import android.os.Bundle;
import java.util.ArrayList;

/** Ask ColorOS's own startup manager to allow SystemUI to open our clock provider. */
final class ColorOsClockStartupPolicy {
    private static final String PACKAGE="org.aliveclean";

    static void allow() throws Exception {
        try(PlatformProvider startup=new PlatformProvider("com.oplus.startup.provider",0)){
            Bundle request=new Bundle();
            ArrayList<String> packages=new ArrayList<>();packages.add(PACKAGE);
            request.putStringArrayList("packageList",packages);
            if(allowed(startup,request))return;
            request.putBoolean(PACKAGE,true);
            startup.call("setAssociateStartState",null,request);
            if(!allowed(startup,request))throw new IllegalStateException("ColorOS did not allow associated startup");
        }
    }

    private static boolean allowed(PlatformProvider startup,Bundle request)throws Exception {
        Bundle state=startup.call("getAssociateStartState",null,request);
        if(state==null||!state.containsKey(PACKAGE))
            throw new IllegalStateException("ColorOS associated-startup state unavailable");
        return state.getBoolean(PACKAGE);
    }

    private ColorOsClockStartupPolicy(){}
}
