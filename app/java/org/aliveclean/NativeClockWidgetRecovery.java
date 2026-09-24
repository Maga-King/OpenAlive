package org.aliveclean;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Retries native failed cards on a visible scene edge, never on an animation frame. */
final class NativeClockWidgetRecovery {
    private final WeakHashMap<Object,Boolean> attempted=new WeakHashMap<>();

    void visible(View clock){
        try{
            View root=NativeClockEditor.findRoot(clock);
            if(root==null)return;
            Object container=root.getClass().getMethod("getWidgetPluginContainer").invoke(root);
            Object proxy=container.getClass().getMethod("getPluginInstance").invoke(container);
            if(proxy==null)return;
            Object plugin=fieldOfType(proxy,"com.oplus.keyguard.style.widgets.container.KeyguardStyleWidgetsPlugin");
            Object impl=fieldOfType(plugin,"com.oplus.keyguard.style.widgets.container.KeyguardStyleWidgetsImpl");
            Object model=call(impl,"getWidgetsContainerViewModel");
            if(!Boolean.TRUE.equals(call(call(model,"getUserUnlocked"),"getValue")))return;
            Object calculator=call(impl,"getCellSizeCalculator");
            retry((View)container,calculator,model);
        }catch(Exception unsupported){NativeClockLoadState.failure("Native widget retry",unsupported);}
    }
    private void retry(View view,Object calculator,Object model)throws Exception{
        if(view.getClass().getName().equals("com.oplus.keyguard.style.widgets.container.view.InstantCardView")){
            Field field=view.getClass().getDeclaredField("instantCardWidget");field.setAccessible(true);
            Object widget=field.get(view);
            // null is an in-flight load. The native callback stores a terminal
            // result here; 1 means success, 0/-1 are failed/skeleton results.
            if(widget==null||((Number)call(widget,"isLoadSuccess")).intValue()==1||attempted.containsKey(widget))return;
            attempted.put(widget,Boolean.TRUE);
            view.getClass().getMethod("onReloadView",calculator.getClass(),model.getClass()).invoke(view,calculator,model);
            return;
        }
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)retry(group.getChildAt(i),calculator,model);
        }
    }
    private static Object call(Object owner,String name)throws Exception{
        Method method;
        try{method=owner.getClass().getMethod(name);}catch(NoSuchMethodException privateMethod){method=owner.getClass().getDeclaredMethod(name);}
        method.setAccessible(true);return method.invoke(owner);
    }
    private static Object fieldOfType(Object owner,String name)throws Exception{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
            field.setAccessible(true);Object value=field.get(owner);
            if(value!=null&&value.getClass().getName().equals(name))return value;
        }
        throw new NoSuchFieldException(name);
    }
}
