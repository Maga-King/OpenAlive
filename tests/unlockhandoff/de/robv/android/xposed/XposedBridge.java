package de.robv.android.xposed;
import java.lang.reflect.*;
import java.util.*;
public final class XposedBridge {
    public static int invocations;
    public static Runnable beforeInvoke;
    public static final Map<Member,XC_MethodHook> hooks=new HashMap<>();
    public static XC_MethodHook.Unhook hookMethod(Member method,XC_MethodHook hook){hooks.put(method,hook);return hook.new Unhook();}
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> type,String name,XC_MethodHook hook){
        Set<XC_MethodHook.Unhook> result=new HashSet<>();for(Method m:type.getDeclaredMethods())if(m.getName().equals(name))result.add(hookMethod(m,hook));return result;
    }
    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> type,XC_MethodHook hook){
        Set<XC_MethodHook.Unhook> result=new HashSet<>();for(Constructor<?> c:type.getDeclaredConstructors())result.add(hookMethod(c,hook));return result;
    }
    public static Object invokeOriginalMethod(Member member,Object receiver,Object[] args)throws Throwable{
        invocations++;if(beforeInvoke!=null)beforeInvoke.run();Method method=(Method)member;method.setAccessible(true);try{return method.invoke(receiver,args);}catch(InvocationTargetException error){throw error.getCause();}
    }
    public static Object invokeHookedMethod(Method method,Object receiver,Object... args)throws Throwable{
        XC_MethodHook hook=hooks.get(method);if(hook==null)return invokeOriginalMethod(method,receiver,args);
        XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.method=method;p.thisObject=receiver;p.args=args;
        hook.beforeHookedMethod(p);
        if(!p.returnEarly)try{p.setResult(invokeOriginalMethod(method,receiver,args));}catch(Throwable error){p.setThrowable(error);}
        hook.afterHookedMethod(p);if(p.hasThrowable())throw p.getThrowable();return p.getResult();
    }
}
