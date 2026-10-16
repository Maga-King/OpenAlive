package de.robv.android.xposed;
import java.lang.reflect.Member;
import java.util.HashMap;
public class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}
    protected void afterHookedMethod(MethodHookParam p)throws Throwable{}
    public static class MethodHookParam {
        public Object thisObject;public Object[] args;public Member method;
        private final HashMap<String,Object> extras=new HashMap<>();
        private Object result;
        private Throwable throwable;
        public boolean returnEarly;
        public boolean hasThrowable(){return throwable!=null;}
        public Throwable getThrowable(){return throwable;}
        public void setThrowable(Throwable value){throwable=value;returnEarly=true;}
        public void setObjectExtra(String key,Object value){extras.put(key,value);}
        public Object getObjectExtra(String key){return extras.get(key);}
        public void setResult(Object value){result=value;throwable=null;returnEarly=true;}
        public Object getResult(){return result;}
    }
    public class Unhook {public void unhook(){}}
}
