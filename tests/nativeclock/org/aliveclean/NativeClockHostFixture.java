package org.aliveclean;

import android.content.Context;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.function.Function;
import org.json.JSONObject;

/** Installed outer host with a local provider; never connected to persisted clock settings. */
final class NativeClockHostFixture implements NativeClockEditSession.Host, AutoCloseable {
    final Object root;
    private final Class<?> rootClass;
    private final Function<String,Object> stock;
    NativeOriginalClockPlugin customClock;
    NativeClockHostFixture(Context app)throws Exception {
        this(app,2);
    }
    NativeClockHostFixture(Context app,int scope)throws Exception {
        Context base=app.createPackageContext("com.oplus.keyguard.clock.base",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader=base.getClassLoader();
        rootClass=loader.loadClass("com.oplus.keyguard.ui.KeyguardPluginViewRoot");
        root=rootClass.getConstructor(Context.class,Context.class).newInstance(app,base);
        Object container=rootClass.getMethod("getClockPluginContainer").invoke(root);
        Class<?> providerClass=loader.loadClass("v3.c");
        Object provider=providerClass.getConstructor(int.class).newInstance(7);
        Context nativeContext=app.createPackageContext("com.oplus.keyguard.personality.clocks",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        android.view.Display display=app.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
        nativeContext=nativeContext.createDisplayContext(display);
        stock=(Function<String,Object>)nativeContext.getClassLoader().loadClass("com.oplus.keyguard.OplusStyleClockPluginProvider")
                .getConstructor(Context.class,Context.class).newInstance(app.createDisplayContext(display),nativeContext);
        Function<String,Object> originalFactory=new NativeClockProvider(app,app.getAssets(),stock);
        Function<String,Object> factory=id->{
            Object value=originalFactory.apply(id);
            if(value instanceof NativeOriginalClockPlugin)customClock=(NativeOriginalClockPlugin)value;
            return value;
        };
        field(providerClass,Object.class).set(provider,factory);
        field(loader.loadClass("com.oplus.keyguard.ui.ClockPluginContainer"),providerClass).set(container,provider);
        rootClass.getMethod("setUp",int.class).invoke(root,scope);
        Bundle scene=new Bundle();scene.putInt("uiState",2);scene.putInt("clockSize",1);scene.putBoolean("isAnim",false);
        rootClass.getMethod("onCall",String.class,Bundle.class).invoke(root,"onClockStateChanged",scene);
        String widgetData=new JSONObject().put("containerSize",new JSONObject().put("containerRows",1).put("containerCols",4)).put("gridDataString","").toString();
        String widgetStyle=new JSONObject().put("styleWidgetsConfig",widgetData).toString();
        write(new JSONObject().put("pkg",NativeFlymeClockPlugin.ID).put("clockStyleConfig",config(NativeFlymeClockPlugin.ID))
                .put("widgetStyleConfig",scope==2?"":widgetStyle).put("baseUiStyleConfig","").toString());
    }
    Object widgetContainer()throws Exception{return rootClass.getMethod("getWidgetPluginContainer").invoke(root);}
    void call(String name,Bundle args)throws Exception{rootClass.getMethod("onCall",String.class,Bundle.class).invoke(root,name,args);}
    String stockConfig()throws Exception{
        Object clock=stock.apply("com.oplus.keyguard.clock.digital");
        java.util.function.BiFunction<String,Bundle,Bundle> transport=(java.util.function.BiFunction<String,Bundle,Bundle>)clock;
        try{return transport.apply("getStyleData",null).getString("styleData");}
        finally{transport.apply("release",null);}
    }
    static String config(String id)throws Exception{return new JSONObject().put("version",1).put("style",id).put("color",0xffffffff).toString();}
    @Override public String read()throws Exception{return (String)rootClass.getMethod("getStyleData").invoke(root);}
    @Override public void write(String json)throws Exception{rootClass.getMethod("setStyleData",String.class).invoke(root,json);}
    @Override public void close()throws Exception{rootClass.getMethod("onCall",String.class,Bundle.class).invoke(root,"release",null);}
    private static Field field(Class<?> owner,Class<?> type)throws Exception {
        Field found=null;for(Field f:owner.getDeclaredFields())if(f.getType()==type&&!Modifier.isStatic(f.getModifiers())){
            if(found!=null)throw new IllegalStateException("ambiguous field");found=f;
        }
        if(found==null)throw new NoSuchFieldException(type.getName());found.setAccessible(true);return found;
    }
}
