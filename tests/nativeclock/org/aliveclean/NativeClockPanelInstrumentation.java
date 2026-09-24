package org.aliveclean;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import java.io.*;

/** Render the installed ColorOS bottom sheet with an independently selectable original face. */
public final class NativeClockPanelInstrumentation extends Instrumentation {
    private ClockTestActivity activity;
    private Dialog dialog;
    private NativeClockStylePanel panel;
    private int selections;
    private Bitmap thumb;
    private NativeClockHostFixture host;
    private NativeClockEditSession session;
    private Throwable selectionFailure;
    private final java.util.List<String> hostEvents=new java.util.ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable[] failure={null};
        try {
            activity=(ClockTestActivity)startActivitySync(new Intent(getTargetContext(),ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->{try{show();}catch(Throwable e){failure[0]=e;}});
            waitForIdleSync();
            if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);
            runOnMainSync(()->dialog.getWindow().getDecorView().postDelayed(entered::countDown,600));
            if(!entered.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("sheet entrance timeout");
            Bitmap screen=getUiAutomation().takeScreenshot();
            if(screen==null)throw new AssertionError("compositor screenshot unavailable");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"native-clock-panel.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}
            screen.recycle();
            runOnMainSync(()->{try{verify();}catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            waitForIdleSync();
            runOnMainSync(()->{try{
                if(!hostEvents.contains("onStyleDialogHidden"))throw new AssertionError("editor close was not reported to native host");
                verifyApplyContract();
            }catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            runOnMainSync(()->{try{verifyStockExtension();}catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            result.putString("stream","NATIVE_PANEL_OK original_sheet=true original_card=true native_selection=true native_cancel=true production_editor=true reopen=true stock_panel_extension=true native_host_edit_events=true dex_apply_contract=true\n");
        }catch(Throwable error){failure[0]=error;result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{runOnMainSync(()->{if(dialog!=null)dialog.dismiss();try{if(host!=null)host.close();}catch(Exception error){android.util.Log.e("ClockPanelTest","release",error);}if(activity!=null)activity.finish();});}
        finish(failure[0]==null?-1:0,result);
    }
    private void show()throws Exception {
        host=new NativeClockHostFixture(activity);
        ClassLoader loader=host.root.getClass().getClassLoader();
        Class<?> callbackType=loader.loadClass("b4.c");
        Object listener=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{callbackType},(proxy,method,args)->{
            if(method.getName().equals("onCall")){
                hostEvents.add((String)args[0]);return null;
            }
            if(method.getName().equals("equals"))return proxy==args[0];
            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            return null;
        });
        host.root.getClass().getMethod("registerCallback",callbackType).invoke(host.root,listener);
        activity.content.addView((View)host.root,new android.widget.FrameLayout.LayoutParams(-1,-1));
        Bitmap sampling=Bitmap.createBitmap(96,192,Bitmap.Config.ARGB_8888);sampling.eraseColor(0xff526f99);
        Bundle wallpaper=new Bundle();wallpaper.putParcelable("wallpaperBitmap",sampling);
        host.customClock.apply("setWallpaperBitmap",wallpaper);
        session=new NativeClockEditSession(host);
        dialog=NativeClockEditor.show(activity,activity,(View)host.root,()->selections++);
        if(dialog==null||!dialog.isShowing())throw new AssertionError("original sheet did not show");
        if(!hostEvents.contains("onStyleDialogShown"))throw new AssertionError("editor did not enter native editing state");
        panel=findPanel(dialog.getWindow().getDecorView());
        if(panel==null)throw new AssertionError("production selector missing");
    }
    private static NativeClockStylePanel findPanel(View view){
        if(view instanceof NativeClockStylePanel)return (NativeClockStylePanel)view;
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){
            NativeClockStylePanel found=findPanel(((ViewGroup)view).getChildAt(n));if(found!=null)return found;
        }
        return null;
    }
    private void verify()throws Exception {
        int edits=editCount();
        View card=panel.card(1);
        if(card.getWidth()==0||card.getHeight()==0)throw new AssertionError("native card not laid out");
        card.performClick();card.performClick();
        if(selectionFailure!=null)throw new IllegalStateException(selectionFailure);
        if(selections!=1||!card.isSelected())throw new AssertionError("card selection not committed once");
        requireEdit(edits,"switch clock");
        if(!new org.json.JSONObject(host.read()).getString("pkg").equals(NativeFlymeClockPlugin.HORIZONTAL_ID))throw new AssertionError("native host did not switch renderer");
        if(host.customClock.apply("openAliveGetWallpaper",null).getParcelable("wallpaperBitmap")==null)throw new AssertionError("Wallpaper lost during provider replacement");
        session.cancel();
        if(!new org.json.JSONObject(host.read()).getString("pkg").equals(NativeFlymeClockPlugin.ID))throw new AssertionError("cancel did not restore original renderer");
        panel.setSelectedStyle(NativeFlymeClockPlugin.ID);
        if(dialog.isShowing())throw new AssertionError("old native panel survived renderer switch");
        dialog=NativeClockEditor.show(activity,activity,(View)host.root,()->selections++);
        if(dialog==null||!dialog.isShowing())throw new AssertionError("independent clock could not reopen editor");
        verifyColors();
        NativeClockEditor.dismissForSwitch(dialog);
        String stockConfig=host.stockConfig();
        org.json.JSONObject stock=new org.json.JSONObject(host.read()).put("pkg","com.oplus.keyguard.clock.digital").put("clockStyleConfig",stockConfig);
        host.write(stock.toString());
        String acceptedStockConfig=new org.json.JSONObject(host.read()).getString("clockStyleConfig");
        dialog=NativeClockEditor.show(activity,activity,(View)host.root,()->selections++);
        NativeClockStylePanel stockPanel=findPanel(dialog.getWindow().getDecorView());
        stockPanel.card(0).performClick();
        org.json.JSONObject selected=new org.json.JSONObject(host.read());
        if(!selected.getString("pkg").equals(NativeFlymeClockPlugin.ID))throw new AssertionError("stock to original selection failed");
        String retained=new org.json.JSONObject(selected.getString("clockStyleConfig")).getString("nativeReturnStyle");
        if(!new org.json.JSONObject(retained).getString("clockStyleConfig").equals(acceptedStockConfig))throw new AssertionError("original system clock configuration lost");
        dialog=NativeClockEditor.show(activity,activity,(View)host.root,()->selections++);
        NativeClockStylePanel originalPanel=findPanel(dialog.getWindow().getDecorView());
        edits=editCount();
        originalPanel.card("com.oplus.keyguard.clock.digital").performClick();
        requireEdit(edits,"return to stock clock");
        if(!new org.json.JSONObject(host.read()).getString("pkg").equals("com.oplus.keyguard.clock.digital"))throw new AssertionError("return to native clock failed");
        java.util.List<String> independentIds=new java.util.ArrayList<>();
        independentIds.add(NativeFlymeArtworkPlugin.PERSPECTIVE);
        for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)independentIds.add(style.id);
        for(String id:independentIds){
            String before=host.read();
            NativeClockEditSession undo=new NativeClockEditSession(host);
            dialog=NativeClockEditor.show(activity,activity,(View)host.root,()->selections++);
            findPanel(dialog.getWindow().getDecorView()).card(id).performClick();
            if(!new org.json.JSONObject(host.read()).getString("pkg").equals(id))throw new AssertionError("Original card did not select independent renderer: "+id);
            if(dialog.isShowing())throw new AssertionError("previous sheet survived original selection");
            dialog=NativeClockEditor.show(activity,activity,(View)host.root,()->selections++);
            if(!findPanel(dialog.getWindow().getDecorView()).card(id).isSelected())throw new AssertionError("Original selection not retained on reopen: "+id);
            NativeClockEditor.dismissForSwitch(dialog);undo.cancel();
            if(!new org.json.JSONObject(host.read()).getString("pkg").equals(new org.json.JSONObject(before).getString("pkg")))throw new AssertionError("Original cancel failed: "+id);
        }
    }
    private void verifyColors()throws Exception{
        View colors=findView(dialog.getWindow().getDecorView(),"com.oplus.keyguard.clock.common.view.color.OplusKeyguardStyleColorSettingsPanel");
        if(colors==null)throw new AssertionError("native color panel missing");
        java.lang.reflect.Field field=colors.getClass().getDeclaredField("colorSettingsController");field.setAccessible(true);
        Object controller=field.get(colors);
        java.util.List<?> palette=(java.util.List<?>)controller.getClass().getMethod("getColorInfos").invoke(controller);
        if(palette.size()<4)throw new AssertionError("official palette not populated");
        for(int mode:new int[]{0,1,3,0,2,1}){
            int edits=editCount();
            Object item=null;
            if(mode==0||mode==1){
                java.lang.reflect.Field entry=controller.getClass().getDeclaredField(mode==0?"mixColorInfo":"inverseColorInfo");entry.setAccessible(true);item=entry.get(controller);
            }
            if(item==null)for(Object candidate:palette){if(((Integer)candidate.getClass().getMethod("getColoringMode").invoke(candidate))==mode){item=candidate;break;}}
            if(item==null)throw new AssertionError("official color mode unavailable "+mode);
            controller.getClass().getMethod("onColorInfoSelected",item.getClass()).invoke(controller,item);
            java.lang.reflect.Field action=controller.getClass().getSuperclass().getDeclaredField("onNotifyColorSelectedAction");action.setAccessible(true);
            Runnable notify=(Runnable)action.get(controller);colors.removeCallbacks(notify);notify.run();
            requireEdit(edits,"native material/color mode "+mode);
            org.json.JSONObject saved=new org.json.JSONObject(new org.json.JSONObject(host.read()).getString("clockStyleConfig"));
            int expected=mode==0?1:mode==1?2:mode==2?4:3;
            if(saved.optInt("coloringType",2)!=expected)throw new AssertionError("native panel/draft diverged "+saved);
            if(saved.optJSONArray("primaryColorDepthHSL")==null)throw new AssertionError("color depth not persisted");
        }
        View reset=dialog.getWindow().getDecorView().findViewWithTag("openalive_reset_clock_color");
        if(reset==null)throw new AssertionError("Default color action missing");
        View soft=dialog.getWindow().getDecorView().findViewWithTag("openalive_soft_clock_color");
        if(soft==null)throw new AssertionError("Soft color mode missing");
        int edits=editCount();soft.performClick();requireEdit(edits,"wallpaper blur");
        org.json.JSONObject softStyle=new org.json.JSONObject(new org.json.JSONObject(host.read()).getString("clockStyleConfig"));
        if(softStyle.optInt("coloringType")!=1||softStyle.optInt("openAliveColorEffect")!=6||!soft.isSelected())throw new AssertionError("Wallpaper soft mode not selected");
        View special=dialog.getWindow().getDecorView().findViewWithTag("openalive_special_clock_color");
        if(special==null)throw new AssertionError("Special effect missing");
        edits=editCount();special.performClick();requireEdit(edits,"special gradient");
        org.json.JSONObject specialStyle=new org.json.JSONObject(new org.json.JSONObject(host.read()).getString("clockStyleConfig"));
        if(specialStyle.optInt("coloringType")!=1||specialStyle.optInt("openAliveColorEffect")!=5||!special.isSelected()||soft.isSelected())throw new AssertionError("Special effect/native mode isolation failed");
        java.lang.reflect.Field selected=controller.getClass().getSuperclass().getDeclaredField("selectedColorInfo");selected.setAccessible(true);
        if(selected.get(controller)!=null)throw new AssertionError("Two color modes selected together");
        edits=editCount();reset.performClick();requireEdit(edits,"reset color");
        org.json.JSONObject restored=new org.json.JSONObject(new org.json.JSONObject(host.read()).getString("clockStyleConfig"));
        if(restored.optInt("coloringType")!=2||restored.has("openAliveColorEffect")||restored.has("primaryColorDepthHSL")||soft.isSelected()||special.isSelected())throw new AssertionError("Default color not restored "+restored);
    }
    private int editCount(){return java.util.Collections.frequency(hostEvents,"onStyleDataEdited");}
    private void requireEdit(int previous,String action){
        if(editCount()!=previous+1)throw new AssertionError(action+" did not notify current native host exactly once");
    }
    private void verifyApplyContract()throws Exception{
        Context editor=activity.createPackageContext("com.oplus.wallpapers",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader=editor.getClassLoader();
        Class<?> type=Class.forName("com.oplus.wallpapers.themes.edit.ThemeEditActivity",false,loader);
        for(String name:new String[]{"x0","e0"})if(type.getDeclaredField(name).getType()!=boolean.class)throw new AssertionError("native apply field "+name);
        Class<?> binding=type.getDeclaredMethod("v5").getReturnType();
        if(!View.class.isAssignableFrom(binding.getDeclaredField("f").getType()))throw new AssertionError("native done binding");
        Class<?> model=type.getDeclaredMethod("g5").getReturnType();
        if(model.getDeclaredMethod("Z1").getReturnType()!=boolean.class)throw new AssertionError("native readiness");
        type.getDeclaredMethod("T5",type,loader.loadClass("com.oplus.wallpapers.business.themeedit.mvvm.eventstate.l"));
        type.getDeclaredMethod("l4",type,loader.loadClass("com.oplus.wallpapers.business.themeedit.mvvm.eventstate.i"));
    }
    private void verifyStockExtension()throws Exception{
        View nativeRoot=findView((View)host.root,"com.oplus.keyguard.clock.digital.ui.view.ClockViewRoot");
        if(nativeRoot==null)throw new AssertionError("native digital clock root missing");
        Object state=nativeRoot.getClass().getMethod("getRenderedViewState").invoke(nativeRoot);
        if(state==null)throw new AssertionError("native digital renderer did not settle");
        Class<?> editorClass=nativeRoot.getContext().getClassLoader().loadClass("com.oplus.keyguard.clock.digital.ui.view.EditPanelView");
        Object editor=editorClass.getConstructor(View.class,Context.class).newInstance(nativeRoot,nativeRoot.getContext());
        java.lang.reflect.Method create=editorClass.getDeclaredMethod("createClockStylePanel",Context.class,Context.class,state.getClass());
        create.setAccessible(true);
        android.widget.LinearLayout original=(android.widget.LinearLayout)create.invoke(editor,activity,nativeRoot.getContext(),state);
        int children=original.getChildCount();View stockRow=original.getChildAt(0);
        NativeClockEditor.extendStockPanel(editor,original,activity.getAssets());
        if(original.getChildCount()!=children+1||original.getChildAt(0)!=stockRow)throw new AssertionError("extension removed native style row");
        NativeClockStylePanel added=findPanel(original);
        if(added==null)throw new AssertionError("native style panel did not receive independent entries");
        added.card(0).performClick();
        if(!new org.json.JSONObject(host.read()).getString("pkg").equals(NativeFlymeClockPlugin.ID))throw new AssertionError("native panel extension did not switch provider");
    }
    private static View findView(View view,String name){
        if(view.getClass().getName().equals(name))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View result=findView(((ViewGroup)view).getChildAt(i),name);if(result!=null)return result;}
        return null;
    }
}
