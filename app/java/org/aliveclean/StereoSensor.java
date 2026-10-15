package org.aliveclean;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

/** Optional gyro registration shared by wallpaper and preview host lifecycles. */
final class StereoSensor implements SensorEventListener,AutoCloseable {
    interface Listener {void onGyroscope(float x,float y,float z,long timestamp);}
    private final SensorManager manager;
    private final Sensor gyro;
    private final Handler handler;
    private final Listener listener;
    private boolean active;
    StereoSensor(Context context,Handler handler,Listener listener){
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        gyro=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        this.handler=handler;this.listener=listener;
    }
    void setActive(boolean enabled){
        if(active==enabled)return;
        if(enabled){active=gyro!=null&&manager.registerListener(this,gyro,SensorManager.SENSOR_DELAY_UI,handler);}
        else{if(manager!=null)manager.unregisterListener(this);active=false;}
    }
    public void onSensorChanged(SensorEvent event){
        if(active&&event.sensor.getType()==Sensor.TYPE_GYROSCOPE)
            listener.onGyroscope(event.values[0],event.values[1],event.values[2],event.timestamp);
    }
    public void onAccuracyChanged(Sensor sensor,int accuracy){}
    public void close(){setActive(false);}
}
