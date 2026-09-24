package org.aliveclean;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** The installed ColorOS edit outline; created only for an editing scene. */
final class NativeClockEditBox implements AutoCloseable {
    final View view;
    private final Method render, setup, align, release;
    private final Constructor<?> state;
    private View target;
    private boolean enabled;

    NativeClockEditBox(Context host,FrameLayout parent,Runnable clicked)throws Exception{
        Context plugin=host.createPackageContext("com.oplus.keyguard.personality.clocks",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        Context context=new NativeClockEditor.PanelContext(host,plugin);
        ClassLoader loader=context.getClassLoader();
        Class<?> type=loader.loadClass("com.oplus.keyguard.clock.common.view.EditBoxView");
        Class<?> stateType=loader.loadClass("com.oplus.keyguard.clock.common.domain.state.EditBoxViewState");
        view=(View)type.getConstructor(Context.class).newInstance(context);
        setup=type.getMethod("setUp",View[].class,int.class,int.class,boolean.class,float.class);
        align=type.getMethod("alignWithTargetViews");
        release=type.getMethod("release");
        render=type.getMethod("renderEx",stateType);
        state=stateType.getConstructor(int.class,boolean.class,boolean.class,int.class,float.class,int.class,float.class,boolean.class);
        Method intent=null;
        for(Method method:type.getMethods())if(method.getName().equals("setIntentListener")&&method.getParameterCount()==1){intent=method;break;}
        if(intent==null)throw new NoSuchMethodException("EditBoxView.setIntentListener");
        Class<?> listener=intent.getParameterTypes()[0];
        intent.invoke(view,Proxy.newProxyInstance(loader,new Class<?>[]{listener},(proxy,method,args)->{
            if(method.getName().equals("invoke")){if(enabled)clicked.run();return null;}
            if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
            if(method.getName().equals("equals"))return proxy==args[0];
            if(method.getName().equals("toString"))return "OpenAlive clock edit intent";
            return null;
        }));
        // The original control lays itself out around its targets. Zero layout
        // dimensions keep its outline out of the clock's wrap-content metrics.
        parent.addView(view,0,new FrameLayout.LayoutParams(0,0));
    }

    void update(View face,boolean edit,boolean animation)throws Exception{
        if(target!=face){
            if(target!=null){target.setOnClickListener(null);target.setClickable(false);}
            view.setOnClickListener(null);target=face;
            float radius=view.getResources().getDimension(view.getResources().getIdentifier("edit_box_radius","dimen","com.oplus.keyguard.personality.clocks"));
            setup.invoke(view,new View[]{face},0,0,false,radius);
            // A new target needs native listeners even when edit mode is unchanged.
            render.invoke(view,state.newInstance(0,false,false,0,-1f,0,-1f,true));
        }
        enabled=edit;
        render.invoke(view,state.newInstance(edit?1:0,animation,false,0,-1f,0,-1f,true));
        view.setVisibility(edit?View.VISIBLE:View.GONE);
        if(!edit){view.setClickable(false);face.setClickable(false);}
        if(edit)align.invoke(view);
    }
    @Override public void close(){
        enabled=false;
        try{release.invoke(view);}catch(Exception ignored){}
        if(target!=null){target.setOnClickListener(null);target.setClickable(false);}
        view.setOnClickListener(null);view.setClickable(false);
    }
}
