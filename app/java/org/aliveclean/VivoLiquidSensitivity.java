package org.aliveclean;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Adjust the native liquid controller's motion input after gravity removal. */
final class VivoLiquidSensitivity implements AutoCloseable {
    private final VivoCalls api;
    private final Object sensor,original;
    private volatile float gain;

    VivoLiquidSensitivity(VivoCalls api,Object sensor,int relative)throws Exception{
        this.api=api;this.sensor=sensor;original=api.get(sensor,"b");relative(relative);
        Class<?> callback=api.type("e4.a");
        Object scaled=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(proxy,method,args)->{
            // e4.b has already removed gravity and corrected screen rotation.
            // Scaling raw accelerometer samples would disturb its gravity filter
            // whenever the slider changed, causing a false shake while stationary.
            float factor=gain;
            if(method.getName().equals("p")&&factor!=1f){
                args=new Object[]{(Float)args[0]*factor,(Float)args[1]*factor,(Float)args[2]*factor};
            }
            try{return method.invoke(original,args);}
            catch(InvocationTargetException error){throw error.getCause();}
        });
        api.set(sensor,"b",scaled);
    }

    void relative(int value){gain=VivoSensorGain.multiplier(value);}
    @Override public void close()throws Exception{api.set(sensor,"b",original);}
}
