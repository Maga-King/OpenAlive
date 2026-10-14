package org.aliveclean;

import android.app.Instrumentation;
import android.os.Bundle;
import android.view.View;
import java.util.ArrayList;

/** All registered original clock styles, with actual attached vendor views. */
public final class NativeFingerprintClockInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();ClockTestActivity activity=null;
        StringBuilder evidence=new StringBuilder();
        try{
            android.content.Context app=getTargetContext();
            activity=(ClockTestActivity)startActivitySync(new android.content.Intent(app,ClockTestActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            final ClockTestActivity host=activity;
            ArrayList<String> ids=new ArrayList<>();
            ids.add(NativeFlymeClockPlugin.ID);ids.add(NativeFlymeClockPlugin.HORIZONTAL_ID);
            ids.add(NativeFlymeArtworkPlugin.PERSPECTIVE);
            for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)ids.add(style.id);
            for(NativeVivoClockStyles.Style style:NativeVivoClockStyles.all(app))ids.add(style.id);
            NativeClockProvider factory=new NativeClockProvider(app,app.getAssets(),null);
            Throwable[] failure={null};int[] cycles={0};
            for(String id:ids){
                NativeOriginalClockPlugin[] clock={null};
                runOnMainSync(()->{try{
                    clock[0]=(NativeOriginalClockPlugin)factory.apply(id);
                    host.content.addView(clock[0].root);
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];waitForIdleSync();
                for(int origin:new int[]{2,3,5})for(int size:new int[]{0,1,2}){
                    final int scene=origin,clockSize=size;
                    runOnMainSync(()->{try{
                        NativeOriginalClockPlugin plugin=clock[0];
                        state(plugin,scene,clockSize);layout(plugin);
                        View previous=plugin.apply(8);
                        for(int repeat=0;repeat<3;repeat++){
                            state(plugin,1,11);layout(plugin);
                            require(plugin.clockContainer.getVisibility()==View.INVISIBLE,"hidden unlock flashed",id);
                            require(plugin.apply(8)==previous,"hidden unlock changed face",id);
                        }
                        state(plugin,5,clockSize);layout(plugin);
                        require(plugin.clockContainer.getVisibility()==View.VISIBLE,"next AOD missing",id);
                        View aod=plugin.apply(8);state(plugin,5,clockSize);
                        require(plugin.apply(8)==aod,"repeated AOD changed face",id);
                        // Immediate reversal and a second sleep must not leave a
                        // pending spring restoring an old visible face.
                        state(plugin,2,clockSize);state(plugin,1,11);state(plugin,5,clockSize);layout(plugin);
                        require(plugin.clockContainer.getVisibility()==View.VISIBLE,"reversal hid AOD",id);
                        cycles[0]++;
                    }catch(Throwable error){failure[0]=error;}});
                    if(failure[0]!=null)throw failure[0];waitForIdleSync();
                    runOnMainSync(()->{try{
                        require(clock[0].clockContainer.getVisibility()==View.VISIBLE,"late callback hid AOD",id);
                        View face=clock[0].apply(8);
                        require(face.getAlpha()==1f&&face.getScaleX()==1f&&face.getScaleY()==1f,"stale spring changed AOD",id);
                    }catch(Throwable error){failure[0]=error;}});
                    if(failure[0]!=null)throw failure[0];
                }
                runOnMainSync(()->{clock[0].apply("release",null);host.content.removeAllViews();});
                evidence.append(id).append(" fingerprint_cycle=OK\n");
            }
            runOnMainSync(host::finish);
            result.putString("stream",evidence+"NATIVE_FINGERPRINT_CLOCK_OK styles="+ids.size()+" cycles="+cycles[0]+"\n");
            finish(-1,result);
        }catch(Throwable error){
            if(activity!=null){final ClockTestActivity host=activity;runOnMainSync(host::finish);}
            result.putString("stream",evidence+android.util.Log.getStackTraceString(error));finish(0,result);
        }
    }
    private static void state(NativeOriginalClockPlugin plugin,int state,int size){
        Bundle args=new Bundle();args.putInt("uiState",state);args.putInt("clockSize",size);args.putBoolean("isAnim",true);
        plugin.apply("onClockStateChanged",args);
    }
    private static void layout(NativeOriginalClockPlugin plugin){
        int width=plugin.root.getResources().getDisplayMetrics().widthPixels;
        plugin.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(4000,View.MeasureSpec.AT_MOST));
        plugin.root.layout(0,0,width,plugin.root.getMeasuredHeight());
    }
    private static void require(boolean value,String message,String id){if(!value)throw new AssertionError(message+": "+id);}
}
