package de.robv.android.xposed;
import java.lang.reflect.*;
public final class XposedHelpers {
    public static Class<?> findClass(String name,ClassLoader loader){try{return Class.forName(name,true,loader);}catch(Exception error){throw new RuntimeException(error);}}
    private static Field field(Object target,String name){
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new IllegalArgumentException(name);
    }
    public static Object getObjectField(Object target,String name){try{return field(target,name).get(target);}catch(Exception error){throw new RuntimeException(error);}}
    public static boolean getBooleanField(Object target,String name){return (Boolean)getObjectField(target,name);}
    public static int getIntField(Object target,String name){return (Integer)getObjectField(target,name);}
    public static void setIntField(Object target,String name,int value){try{field(target,name).setInt(target,value);}catch(Exception error){throw new RuntimeException(error);}}
    public static Object getStaticObjectField(Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception error){throw new RuntimeException(error);}}
    public static Object callStaticMethod(Class<?> type,String name,Object... args){
        try{for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(null,args);}}
        catch(Exception error){throw new RuntimeException(error);}
        throw new IllegalArgumentException(name);
    }
    public static Object callMethod(Object target,String name,Object... args){
        try{for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(target,args);}}
        catch(Exception error){throw new RuntimeException(error);}
        throw new IllegalArgumentException(name);
    }
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> type,String name,Object... args){
        try{Class<?>[] params=new Class<?>[args.length-1];for(int i=0;i<params.length;i++)params[i]=(Class<?>)args[i];return XposedBridge.hookMethod(type.getDeclaredMethod(name,params),(XC_MethodHook)args[args.length-1]);}
        catch(Exception error){throw new RuntimeException(error);}
    }
}
