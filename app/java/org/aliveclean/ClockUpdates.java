package org.aliveclean;

import android.content.*;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;

/** Clock events are independent of wallpaper frames. Runtime hosts supply AOD ticks. */
final class ClockUpdates {
    interface Listener {void refresh(long time);}
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final boolean preview;
    private boolean active;
    private final Runnable minute=()->refresh(System.currentTimeMillis());
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refresh(System.currentTimeMillis());}};
    private final ContentObserver format=new ContentObserver(main){@Override public void onChange(boolean self){refresh(System.currentTimeMillis());}};
    ClockUpdates(Context context,boolean preview,Listener listener){this.context=context;this.preview=preview;this.listener=listener;}
    void start(){
        if(active)return;
        IntentFilter filter=new IntentFilter();
        for(String action:new String[]{Intent.ACTION_TIME_TICK,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_LOCALE_CHANGED,Intent.ACTION_CONFIGURATION_CHANGED})filter.addAction(action);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,null,main,Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver,filter,null,main);
        try{context.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24),false,format);}
        catch(RuntimeException error){context.unregisterReceiver(receiver);throw error;}
        active=true;refresh(System.currentTimeMillis());
    }
    void refresh(long time){
        if(!active)return;
        listener.refresh(time);main.removeCallbacks(minute);
        // Only the visible editor needs a fallback callback. Actual AOD refresh
        // is driven by ColorOS's existing wake/display window, not a second alarm.
        if(preview)main.postDelayed(minute,60000-Math.floorMod(System.currentTimeMillis(),60000L));
    }
    void stop(){if(!active)return;active=false;main.removeCallbacks(minute);context.unregisterReceiver(receiver);context.getContentResolver().unregisterContentObserver(format);}
}
