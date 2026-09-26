package org.aliveclean;

import android.os.Bundle;

/** Small process-local load snapshot, returned only by an explicit runtime dump. */
final class NativeClockLoadState {
    static final int TRANSACTION_SNAPSHOT=android.os.IBinder.FIRST_CALL_TRANSACTION;
    static final String DESCRIPTOR="org.aliveclean.clock.LoadState";
    private static int providers,requests,customRequests,created;
    private static int materialSeeds;
    private static java.lang.ref.WeakReference<Object> materialController=new java.lang.ref.WeakReference<>(null);
    private static boolean materialLive;
    static synchronized void materialInput(Object controller,boolean live){
        if(materialController.get()!=controller)materialController=new java.lang.ref.WeakReference<>(controller);
        materialLive=live;
    }
    static synchronized void materialSeeded(){materialSeeds++;}
    private static String lastRequest="",lastEntry="",firstFailure="";
    private static String startup="No SystemUI context observed";
    private static String lastRegistration="No clock registration attempted";
    static synchronized void startup(String value){startup=value;}
    static synchronized void registration(String value){lastRegistration=value;}
    static synchronized void provider(){providers++;}
    static synchronized void request(String id,String entry,boolean custom){
        requests++;if(custom)customRequests++;lastRequest=id;lastEntry=entry;
    }
    static synchronized void created(){created++;}
    static synchronized void failure(String stage,Throwable error){
        if(!firstFailure.isEmpty())return;
        firstFailure=stage+(error==null?"":"\n"+android.util.Log.getStackTraceString(error));
        if(firstFailure.length()>12000)firstFailure=firstFailure.substring(0,12000);
    }
    static synchronized Bundle snapshot(){
        Bundle out=new Bundle();out.putInt("providers",providers);out.putInt("requests",requests);
        out.putString("startup",startup);
        out.putString("lastRegistration",lastRegistration);
        out.putInt("customRequests",customRequests);out.putInt("created",created);
        out.putString("lastRequest",lastRequest);out.putString("lastEntry",lastEntry);
        out.putInt("materialSeeds",materialSeeds);
        Object controller=materialController.get();
        if(controller!=null)try{
            Class<?> type=controller.getClass();
            out.putString("nativeMaterial","live="+materialLive+" mode="+type.getMethod("getColoringMode$KeyguardPersonalityClocks_release").invoke(controller)
                    +" prepared="+type.getMethod("isColoringPrepared").invoke(controller)
                    +" sampled="+type.getMethod("isStaticMixColorComplete$KeyguardPersonalityClocks_release").invoke(controller));
        }catch(Exception unavailable){out.putString("nativeMaterial",unavailable.toString());}
        else out.putString("nativeMaterial","no wallpaper input observed");
        out.putString("firstFailure",firstFailure);return out;
    }
    private NativeClockLoadState(){}
}
