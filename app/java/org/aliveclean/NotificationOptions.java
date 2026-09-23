package org.aliveclean;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

final class NotificationOptions {
    static final int RING_BLUE=0xff1f7ffb;
    static final String PREFS="notification_options";
    static SharedPreferences preferences(Context c){return c.getSharedPreferences(PREFS,0);}
    static void export(Context c,Bundle out){
        SharedPreferences p=preferences(c);
        out.putInt("notification_mode",mode(p.getInt("mode",0)));
        out.putInt("notification_seconds",NotificationPulseWindow.seconds(p.getInt("seconds",10)));
        out.putString("notification_color",color(p.getString("color","blue")));
        out.putInt("notification_ring_color",p.getInt("ring_color",RING_BLUE)|0xff000000);
    }
    static int mode(int value){return value>=0&&value<=4?value:0;}
    static String color(String value){return "red".equals(value)||"gold".equals(value)?value:"blue";}
}
