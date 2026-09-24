package org.aliveclean;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.animation.ValueAnimator;
import java.lang.reflect.Field;
import org.json.JSONObject;

/** Real original renderers with the production retention host, no persisted settings. */
public final class NativeClockRetentionInstrumentation extends Instrumentation {
    private ClockTestActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable[] failure={null};
        try{
            activity=(ClockTestActivity)startActivitySync(new Intent(getTargetContext(),ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->{try{verify();}catch(Throwable error){failure[0]=error;}});
            if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            result.putString("stream","NATIVE_RETENTION_OK all_original_styles=true aod_off=true wake_after_frame=true direct_unlock=true widgets_preserved=true stock_restored=true\n");
        }catch(Throwable error){failure[0]=error;result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{runOnMainSync(()->{if(activity!=null)activity.finish();});}
        finish(failure[0]==null?-1:0,result);
    }
    private void verify()throws Exception{
        FrameLayout window=activity.content;
        AodClockHost retention=new AodClockHost();
        NativeClockHostFixture host=new NativeClockHostFixture(activity);
        window.addView((View)host.root,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout widgets=new FrameLayout(activity);
        View card=new View(activity);widgets.addView(card,new FrameLayout.LayoutParams(160,80));
        window.addView(widgets);widgets.setAlpha(.7f);widgets.setTranslationY(19f);
        try{
            java.util.List<String> ids=new java.util.ArrayList<>();
            ids.add(NativeFlymeClockPlugin.ID);ids.add(NativeFlymeClockPlugin.HORIZONTAL_ID);
            for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)ids.add(style.id);
            ids.add(NativeFlymeArtworkPlugin.PERSPECTIVE);
            for(String id:ids){
                host.write(new JSONObject(host.read()).put("pkg",id).put("clockStyleConfig",NativeClockHostFixture.config(id)).toString());
                NativeOriginalClockPlugin clock=host.customClock;
                View scope=clock.apply(1),content=AodClockHost.nativeContent(scope);
                if(content!=clock.clockContainer)throw new AssertionError("wrong native content: "+id);
                content.setAlpha(.65f);
                retention.showNative(window,content,scope);
                if(!retention.shown()||!retention.ownsClock())throw new AssertionError("retention not attached: "+id);
                for(int scene:new int[]{5,3,2}){
                    Bundle state=new Bundle();state.putInt("uiState",scene);state.putInt("clockSize",scene==3?0:1);state.putBoolean("isAnim",false);
                    clock.apply("onClockStateChanged",state);
                    window.getViewTreeObserver().dispatchOnPreDraw();
                    near(content.getTransitionAlpha(),0);near(content.getAlpha(),.65f);
                    near(scope.getTransitionAlpha(),1);near(widgets.getTransitionAlpha(),1);near(widgets.getAlpha(),.7f);near(widgets.getTranslationY(),19);
                }
                retention.contentAlpha(0);window.getViewTreeObserver().dispatchOnPreDraw();near(content.getTransitionAlpha(),0);
                retention.contentAlpha(1);retention.leave(true);end(retention,"fade");
                near(content.getTransitionAlpha(),0);
                retention.frameReady();ValueAnimator reveal=animator(retention,"stockFade");
                if(reveal==null)throw new AssertionError("expanded frame did not release native clock");
                reveal.setCurrentPlayTime(80);
                if(content.getTransitionAlpha()<=0||content.getTransitionAlpha()>=1)throw new AssertionError("native clock reveal did not fade");
                reveal.end();near(content.getTransitionAlpha(),1);
                for(float visible:new float[]{0,1}){
                    retention.showNative(window,content,scope);retention.contentAlpha(visible);
                    retention.leaveUnlocked();end(retention,"fade");retention.frameReady();
                    window.getViewTreeObserver().dispatchOnPreDraw();near(content.getTransitionAlpha(),0);
                    near(widgets.getTransitionAlpha(),1);
                    retention.unlockFinished();near(content.getTransitionAlpha(),1);retention.hide();
                }
            }
            // Stock time/date leaves keep their original independent masking contract.
            View time=new View(activity),date=new View(activity);window.addView(time);window.addView(date);
            retention.show(window,time,date);near(time.getTransitionAlpha(),0);near(date.getTransitionAlpha(),0);
            retention.hide();near(time.getTransitionAlpha(),1);near(date.getTransitionAlpha(),1);
            retention.showNative(window,widgets,window);
            if(retention.shown())throw new AssertionError("unmarked widgets accepted as clock");
            window.removeView(time);window.removeView(date);
        }finally{retention.hide();host.close();window.removeView((View)host.root);window.removeView(widgets);}
    }
    private static ValueAnimator animator(AodClockHost host,String name)throws Exception{
        Field field=AodClockHost.class.getDeclaredField(name);field.setAccessible(true);return (ValueAnimator)field.get(host);
    }
    private static void end(AodClockHost host,String name)throws Exception{ValueAnimator animator=animator(host,name);if(animator!=null)animator.end();}
    private static void near(float actual,float expected){if(Math.abs(actual-expected)>.0001f)throw new AssertionError(actual+" != "+expected);}
}
