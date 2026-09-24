package org.aliveclean;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;

public final class NativeClockEditBoxInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable[] failure={null};
        ClockTestActivity activity=(ClockTestActivity)startActivitySync(new Intent(getTargetContext(),ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->{
            try(NativeClockHostFixture host=new NativeClockHostFixture(activity)){
                View root=(View)host.root;activity.content.addView(root,new android.widget.FrameLayout.LayoutParams(-1,-1));
                java.util.List<String> ids=new java.util.ArrayList<>();
                ids.add(NativeFlymeClockPlugin.ID);ids.add(NativeFlymeClockPlugin.HORIZONTAL_ID);ids.add(NativeFlymeArtworkPlugin.PERSPECTIVE);
                for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)ids.add(style.id);
                for(String id:ids){
                    JSONObject config=new JSONObject(host.read()).put("pkg",id).put("clockStyleConfig",NativeClockHostFixture.config(id));host.write(config.toString());
                    layout(root);int before=findClock(root).getHeight();
                    call(root,"onViewStateChanged",1);layout(root);
                    View box=find(root,"com.oplus.keyguard.clock.common.view.EditBoxView");
                    if(box==null||box.getWidth()<=0||box.getHeight()<=0||box.getVisibility()!=View.VISIBLE||!box.isClickable())throw new AssertionError("Missing edit target "+id);
                    if(findClock(root).getHeight()!=before)throw new AssertionError("Outline altered clock size "+id);
                    if(!box.performClick())throw new AssertionError("Outline not clickable "+id);
                    Object transport=transport(root);
                    java.lang.reflect.Field editor=NativeOriginalClockPlugin.class.getDeclaredField("editor");editor.setAccessible(true);
                    android.app.Dialog dialog=(android.app.Dialog)editor.get(transport);
                    if(dialog==null||!dialog.isShowing())throw new AssertionError("Outline did not reopen selector "+id);
                    ((NativeOriginalClockPlugin)transport).apply("requestHideEditPanel",null);
                    if(id.equals(NativeFlymeClockPlugin.ID)){
                        Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
                        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"native-clock-editbox.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
                    }
                    call(root,"onViewStateChanged",0);layout(root);
                    if(box.getVisibility()==View.VISIBLE||box.isClickable()||box.hasOnClickListeners())throw new AssertionError("Lockscreen retained editor interaction "+id);
                }
                result.putString("stream","NATIVE_EDITBOX_OK styles="+ids.size()+" original_outline=true click_reopens=true geometry_unchanged=true outside_editor_disabled=true\n");
            }catch(Throwable error){failure[0]=error;result.putString("stream",android.util.Log.getStackTraceString(error));}
            finally{activity.finish();}
        });
        finish(failure[0]==null?-1:0,result);
    }
    private static void call(View root,String command,int state)throws Exception{Bundle args=new Bundle();args.putInt("viewState",state);root.getClass().getMethod("onCall",String.class,Bundle.class).invoke(root,command,args);}
    private static void layout(View root){int w=root.getResources().getDisplayMetrics().widthPixels,h=root.getResources().getDisplayMetrics().heightPixels;root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
    private static View findClock(View root)throws Exception{return ((NativeOriginalClockPlugin)transport(root)).clockContainer;}
    private static Object transport(View root)throws Exception{
        View plugin=find(root,"android.widget.LinearLayout");
        // Follow the host's public transport wrapper rather than matching view order.
        Object container=root.getClass().getMethod("getClockPluginContainer").invoke(root);
        Object wrapper=container.getClass().getMethod("getPluginInstance").invoke(container);
        for(java.lang.reflect.Field field:wrapper.getClass().getDeclaredFields()){field.setAccessible(true);Object value=field.get(wrapper);if(value instanceof NativeOriginalClockPlugin)return value;}
        throw new AssertionError("Independent transport missing");
    }
    private static View find(View root,String type){if(root.getClass().getName().equals(type))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),type);if(found!=null)return found;}return null;}
}
