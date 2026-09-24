package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.function.Function;
import org.json.JSONObject;

/** Uses the installed outer root to exercise selection and rollback without saving settings. */
public final class NativeClockEditorInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable[] failure={null};
        runOnMainSync(()->{try{verify();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null){result.putString("stream",android.util.Log.getStackTraceString(failure[0]));finish(0,result);}
        else{result.putString("stream","NATIVE_EDITOR_OK original_root=true selection=true cancel=true\n");finish(-1,result);}
    }
    private void verify()throws Exception {
        Context app=getTargetContext();
        Context base=app.createPackageContext("com.oplus.keyguard.clock.base",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader=base.getClassLoader();
        Class<?> rootClass=loader.loadClass("com.oplus.keyguard.ui.KeyguardPluginViewRoot");
        Object root=rootClass.getConstructor(Context.class,Context.class).newInstance(app,base);
        Object container=rootClass.getMethod("getClockPluginContainer").invoke(root);
        Class<?> containerClass=loader.loadClass("com.oplus.keyguard.ui.ClockPluginContainer");
        Class<?> providerClass=loader.loadClass("v3.c");
        Object provider=providerClass.getConstructor(int.class).newInstance(7);
        Function<String,Object> factory=id->{
            try {return NativeFlymeClockPlugin.ID.equals(id)?new NativeFlymeClockPlugin(app):null;}
            catch(Exception error){throw new IllegalStateException(error);}
        };
        field(providerClass,Object.class).set(provider,factory);
        field(containerClass,providerClass).set(container,provider);
        Method write=rootClass.getMethod("setStyleData",String.class);
        Method read=rootClass.getMethod("getStyleData");
        try {
            rootClass.getMethod("setUp",int.class).invoke(root,2);
            Bundle scene=new Bundle();scene.putInt("uiState",2);scene.putInt("clockSize",1);scene.putBoolean("isAnim",false);
            rootClass.getMethod("onCall",String.class,Bundle.class).invoke(root,"onClockStateChanged",scene);
            String config=config(0xff112233);
            write.invoke(root,config);
            String initial=(String)read.invoke(root);
            require(initial!=null,"outer root did not load clock");
            NativeClockEditSession.Host host=new NativeClockEditSession.Host(){
                public String read()throws Exception{return (String)read.invoke(root);}
                public void write(String value)throws Exception{write.invoke(root,value);}
            };
            NativeClockEditSession session=new NativeClockEditSession(host);
            session.select(NativeFlymeClockPlugin.ID,inner(0xffaaccee));
            require(new JSONObject(new JSONObject(host.read()).getString("clockStyleConfig")).getInt("color")==0xffaaccee,"selection lost color");
            session.cancel();
            session=new NativeClockEditSession(host);
            String beforeInvalid=host.read();
            boolean rejected=false;
            try{session.select(NativeFlymeClockPlugin.ID,new JSONObject(inner(0xffabcdef)).put("version",99).toString());}
            catch(Exception expected){rejected=true;}
            require(rejected,"same-ID invalid configuration was silently accepted");
            require(beforeInvalid.equals(host.read()),"invalid configuration failed to roll back");
            session.cancel();
            require(new JSONObject(new JSONObject(host.read()).getString("clockStyleConfig")).getInt("color")==0xff112233,"cancel did not restore original");
            // A rejected package must restore the last accepted preview.
            session=new NativeClockEditSession(host);
            rejected=false;
            try {session.select("org.aliveclean.clock.missing",inner(0xff000000));} catch(Exception expected){rejected=true;}
            require(rejected,"missing renderer accepted");
            require(new JSONObject(host.read()).getString("pkg").equals(NativeFlymeClockPlugin.ID),"failed selection lost current clock");
            session.cancel();
        } finally {rootClass.getMethod("onCall",String.class,Bundle.class).invoke(root,"release",null);}
    }
    private String config(int color)throws Exception{return new JSONObject().put("pkg",NativeFlymeClockPlugin.ID).put("clockStyleConfig",inner(color)).put("widgetStyleConfig","").put("baseUiStyleConfig","").toString();}
    private String inner(int color)throws Exception{return new JSONObject().put("version",1).put("style",NativeFlymeClockPlugin.ID).put("color",color).toString();}
    private static Field field(Class<?> owner,Class<?> type)throws Exception {
        Field found=null;for(Field f:owner.getDeclaredFields())if(f.getType()==type&&!Modifier.isStatic(f.getModifiers())){
            if(found!=null)throw new IllegalStateException("ambiguous field");found=f;
        }
        if(found==null)throw new NoSuchFieldException(type.getName());found.setAccessible(true);return found;
    }
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
