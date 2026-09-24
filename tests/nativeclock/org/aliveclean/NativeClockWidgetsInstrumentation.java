package org.aliveclean;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

/** Loads the installed widget plugin beside the clock, using an empty local widget draft. */
public final class NativeClockWidgetsInstrumentation extends Instrumentation {
    private ClockTestActivity activity;
    private NativeClockHostFixture host;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable[] failure={null};
        try{
            activity=(ClockTestActivity)startActivitySync(new Intent(getTargetContext(),ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->{try{
                host=new NativeClockHostFixture(activity,6);
                activity.content.addView((View)host.root,new FrameLayout.LayoutParams(-1,-1));
            }catch(Throwable error){failure[0]=error;}});
            waitForIdleSync();
            if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            runOnMainSync(()->{try{
                Object container=host.widgetContainer();
                Object plugin=container.getClass().getMethod("getPluginInstance").invoke(container);
                if(plugin==null)throw new AssertionError("installed widget plugin did not load in isolated host");
                if(((android.view.ViewGroup)container).getChildCount()==0)throw new AssertionError("widget plugin has no root view");
                describe((View)container,result);
                View grid=find((View)container,"com.oplus.keyguard.style.widgets.container.view.CustomRecyclerView");
                int inset=new NativeClockGeometry(activity).widgetInset();
                if(grid==null||grid.getWidth()!=((View)container).getWidth()-2*inset||grid.getLeft()!=inset)
                    throw new AssertionError("native widget grid collapsed or received an absolute right edge");
                String originalWidgets=new org.json.JSONObject(host.read()).optString("widgetStyleConfig");
                NativeClockEditSession edit=new NativeClockEditSession(host);
                edit.select(NativeFlymeClockPlugin.HORIZONTAL_ID,NativeClockHostFixture.config(NativeFlymeClockPlugin.HORIZONTAL_ID));
                if(container.getClass().getMethod("getPluginInstance").invoke(container)!=plugin)throw new AssertionError("clock selection recreated widget plugin");
                if(!originalWidgets.equals(new org.json.JSONObject(host.read()).optString("widgetStyleConfig")))throw new AssertionError("clock selection changed widget configuration");
                edit.cancel();
                if(container.getClass().getMethod("getPluginInstance").invoke(container)!=plugin)throw new AssertionError("cancel replaced system widgets");
            }catch(Throwable error){failure[0]=error;}});
            if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            for(String id:new String[]{"org.aliveclean.clock.hyperos.doodle.1",NativeFlymeClockPlugin.HORIZONTAL_ID}){
                runOnMainSync(()->{try{
                    Bundle edit=new Bundle();edit.putInt("viewState",1);host.call("onViewStateChanged",edit);
                    new NativeClockEditSession(host).select(id,NativeClockHostFixture.config(id));
                }catch(Throwable error){failure[0]=error;}});
                waitForIdleSync();Thread.sleep(600);waitForIdleSync();
                runOnMainSync(()->{try{
                    android.view.ViewGroup container=(android.view.ViewGroup)host.widgetContainer();
                    View widgetRoot=container.getChildAt(0);
                    int expected=host.customClock.clockContainer.getBottom()+new NativeClockGeometry(activity).widgetGap();
                    if(widgetRoot.getPaddingTop()!=expected)throw new AssertionError("Widget stayed at old editor top: actual="+widgetRoot.getPaddingTop()+" expected="+expected+" style="+id);
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            }
            runOnMainSync(()->{try{verifyRecovery();}catch(Throwable error){failure[0]=error;}});
            if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            for(int mode:new int[]{1,2,0,3,1,4,2,5,6,1}){
                runOnMainSync(()->{try{
                    org.json.JSONObject config=new org.json.JSONObject(host.customClock.apply("getStyleData",null).getString("styleData"));
                    NativeOriginalClockPlugin.writeColorMode(config,mode);
                    Bundle args=new Bundle();args.putString("styleData",config.toString());
                    host.customClock.apply("setStyleData",args);
                }catch(Throwable error){failure[0]=error;}});
                waitForIdleSync();Thread.sleep(250);waitForIdleSync();
                runOnMainSync(()->{try{
                    Object proxy=host.widgetContainer().getClass().getMethod("getPluginInstance").invoke(host.widgetContainer());
                    Object nativePlugin=fieldValue(proxy,"com.oplus.keyguard.style.widgets.container.KeyguardStyleWidgetsPlugin");
                    Object impl=fieldValue(nativePlugin,"com.oplus.keyguard.style.widgets.container.KeyguardStyleWidgetsImpl");
                    java.lang.reflect.Method getter=impl.getClass().getDeclaredMethod("getWidgetsContainerViewModel");getter.setAccessible(true);
                    Object model=getter.invoke(impl);
                    Object state=model.getClass().getMethod("getColoringType").invoke(model);
                    java.lang.reflect.Method value=state.getClass().getMethod("getValue");value.setAccessible(true);
                    int actual=((Number)value.invoke(state)).intValue();
                    if(actual!=(mode==0?2:mode>=5?1:mode))throw new AssertionError("Native cards got wrong material mode: "+actual+" selected="+mode);
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw new IllegalStateException(failure[0]);
            }
            result.putString("stream","NATIVE_WIDGETS_OK installed_widget_plugin=true automatic_recruit=true separate_root=true selection_keeps_widgets=true cancel_keeps_widgets=true material_modes=true\n"+result.getString("widgetTree",""));
        }catch(Throwable error){failure[0]=error;result.putString("stream",android.util.Log.getStackTraceString(error));}
        finally{runOnMainSync(()->{try{if(host!=null)host.close();}catch(Exception error){android.util.Log.e("ClockWidgetsTest","release",error);}if(activity!=null)activity.finish();});}
        finish(failure[0]==null?-1:0,result);
    }
    private static void describe(View root,Bundle result){
        StringBuilder tree=new StringBuilder();describe(root,tree,0);result.putString("widgetTree",tree.toString());
    }
    private void verifyRecovery()throws Exception{
        android.view.ViewGroup container=(android.view.ViewGroup)host.widgetContainer();
        Object proxy=container.getClass().getMethod("getPluginInstance").invoke(container);
        Object plugin=fieldValue(proxy,"com.oplus.keyguard.style.widgets.container.KeyguardStyleWidgetsPlugin");
        Object impl=fieldValue(plugin,"com.oplus.keyguard.style.widgets.container.KeyguardStyleWidgetsImpl");
        java.lang.reflect.Method getter=impl.getClass().getDeclaredMethod("getWidgetsContainerViewModel");getter.setAccessible(true);
        Object model=getter.invoke(impl);
        java.lang.reflect.Method unlocked=model.getClass().getDeclaredMethod("setUserUnlocked",boolean.class);unlocked.setAccessible(true);
        ClassLoader loader=impl.getClass().getClassLoader();
        Class<?> cardType=loader.loadClass("com.oplus.keyguard.style.widgets.container.view.InstantCardView");
        View card=(View)cardType.getConstructor(android.content.Context.class).newInstance(container.getChildAt(0).getContext());
        Class<?> data=loader.loadClass("com.oplus.keyguard.style.widgets.container.data.InstantCardWidget");
        java.lang.reflect.Constructor<?> ctor=data.getConstructor(View.class,loader.loadClass("pantanal.app.Card"),String.class,int.class,int.class,int.class);
        java.lang.reflect.Field current=cardType.getDeclaredField("instantCardWidget");current.setAccessible(true);
        Object failed=ctor.newInstance(null,null,"",0,0,0);
        current.set(card,failed);container.addView(card,new android.view.ViewGroup.LayoutParams(1,1));
        NativeClockWidgetRecovery recovery=new NativeClockWidgetRecovery();
        java.lang.reflect.Field seen=NativeClockWidgetRecovery.class.getDeclaredField("attempted");seen.setAccessible(true);
        java.util.Map<?,?> attempts=(java.util.Map<?,?>)seen.get(recovery);
        unlocked.invoke(model,false);recovery.visible(host.customClock.root);
        if(!attempts.isEmpty())throw new AssertionError("Recovery ran before user unlock");
        unlocked.invoke(model,true);recovery.visible(host.customClock.root);
        if(!attempts.containsKey(failed))throw new AssertionError("Failed card not retried: "+NativeClockLoadState.snapshot());
        recovery.visible(host.customClock.root);
        if(attempts.size()!=1)throw new AssertionError("Same failed card retried repeatedly");
        Object healthy=ctor.newInstance(null,null,"",0,0,1);current.set(card,healthy);recovery.visible(host.customClock.root);
        if(attempts.containsKey(healthy))throw new AssertionError("Healthy card reloaded");
        current.set(card,null);recovery.visible(host.customClock.root);
        if(attempts.size()!=1)throw new AssertionError("In-flight load retried");
        container.removeView(card);
    }
    private static Object fieldValue(Object owner,String className)throws Exception{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())
            for(java.lang.reflect.Field field:type.getDeclaredFields()){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                field.setAccessible(true);Object value=field.get(owner);
                if(value!=null&&value.getClass().getName().equals(className))return value;
            }
        throw new NoSuchFieldException(className);
    }
    private static View find(View root,String name){
        if(root.getClass().getName().equals(name))return root;
        if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),name);if(found!=null)return found;}}
        return null;
    }
    private static void describe(View view,StringBuilder out,int depth){
        if(depth>7)return;
        out.append(view.getClass().getName()).append(" ").append(view.getLeft()).append(',').append(view.getTop())
                .append(' ').append(view.getWidth()).append('x').append(view.getHeight()).append(" visible=").append(view.getVisibility()).append('\n');
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)describe(group.getChildAt(i),out,depth+1);}
    }
}
