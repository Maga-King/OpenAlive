package org.aliveclean;

/** Original gyro-vector displacement and .87/1 scene crop, on the render thread. */
final class StereoMotion implements WallpaperMotion {
    private int scene;
    private float scale,fromScale,targetScale,elapsed,duration;
    private float x,y,z=1,rx,ry,rz,quiet,sensorAge=1;
    private long last=-1,sensorStamp=-1;
    private boolean moving,unlock;
    private final boolean keepLock;
    private float sensitivityGain=1;
    private boolean allowAod=true;

    StereoMotion(int scene){this(scene,false);}
    StereoMotion(int scene,boolean keepLock){this.keepLock=keepLock;this.scene=scene;scale=targetScale=scene==2&&!keepLock?1:.87f;}
    float scale(){return scale;}
    float shiftX(){return Math.max(-1,Math.min(1,-2*x))*.04f;}
    float shiftY(){return Math.max(-1,Math.min(1,-y))*.04f;}
    /** Apply on the GL thread; retain the current tilt and scene animation. */
    void configure(int sensitivity,boolean allowAod){
        sensitivityGain=multiplier(sensitivity);
        if(this.allowAod!=allowAod&&scene==0){
            sensorStamp=-1;sensorAge=1;rx=ry=rz=0;quiet=1;
        }
        this.allowAod=allowAod;
        if((scene!=0||allowAod)&&(rx*rx+ry*ry+rz*rz)*sensitivityGain*sensitivityGain>.0004f){quiet=0;moving=true;}
    }
    static float multiplier(int relative){
        int value=Math.max(-50,Math.min(50,relative));
        // Same incoming-velocity curve as VivoSensorGain, kept Android-free for
        // motion tests. 0 preserves original motion, -50=.1x and +50=2x.
        return (float)Math.pow(value<0?10:2,value/50.0);
    }
    public void change(int next,boolean animate){
        scene=next;fromScale=scale;targetScale=next==2&&!keepLock?1:.87f;elapsed=0;
        unlock=next==2;duration=animate?(unlock?1.15f:.1f):0;
        if(duration==0)scale=targetScale;
        if(next==0&&!allowAod){rx=ry=rz=0;moving=false;quiet=1;}
    }
    /** Android gyroscope values in rad/s; sensor timestamp only defines epochs. */
    void gyroscope(float vx,float vy,float vz,long timestamp){
        if((scene==0&&!allowAod)||!Float.isFinite(vx)||!Float.isFinite(vy)||!Float.isFinite(vz))return;
        if(sensorStamp<0||timestamp<=sensorStamp||timestamp-sensorStamp>500_000_000L){
            sensorStamp=timestamp;sensorAge=0;return;
        }
        sensorStamp=timestamp;sensorAge=0;rx=vx;ry=vy;rz=vz;
        if((vx*vx+vy*vy+vz*vz)*sensitivityGain*sensitivityGain>.0004f){quiet=0;moving=true;}
    }
    public void advance(long now,float speed){
        float dt=last<0?0:Math.min(.05f,Math.max(0,(now-last)*1e-9f));last=now;
        if(elapsed<duration){
            elapsed=Math.min(duration,elapsed+dt);float t=elapsed/duration;
            float eased=unlock?unlockCurve(t):.5f-.5f*(float)Math.cos(Math.PI*t);
            scale=fromScale+(targetScale-fromScale)*eased;
        }
        sensorAge+=dt;
        if(sensorAge>.25f){rx=ry=rz=0;}
        float vx=rx*sensitivityGain,vy=ry*sensitivityGain,vz=rz*sensitivityGain;
        float length=(float)Math.sqrt(vx*vx+vy*vy+vz*vz);
        if(length<.02f)quiet+=dt;else quiet=0;
        if((scene==0&&!allowAod)||quiet>=.75f){
            // Meizu's private motion sensor selects this return-to-neutral path.
            // Standard gyro quiet detection provides that signal on other devices.
            x=returnAxis(x,dt);y=returnAxis(y,dt);
            z=(z<0?-1:1)*(float)Math.sqrt(Math.max(0,1-x*x-y*y));
            moving=x!=0||y!=0;
        }else if(length>0&&dt>0){
            // Original SensorManager matrix is consumed column-major, i.e. its
            // transpose. Below .1 rad/s the source keeps the unnormalized axis.
            float gain=length>.1f?1:length;
            float angle=-length*dt*gain;
            float ax=vx/length,ay=vy/length,az=vz/length;
            float c=(float)Math.cos(angle),s=(float)Math.sin(angle),dot=ax*x+ay*y+az*z;
            float nx=x*c+(ay*z-az*y)*s+ax*dot*(1-c);
            float ny=y*c+(az*x-ax*z)*s+ay*dot*(1-c);
            z=z*c+(ax*y-ay*x)*s+az*dot*(1-c);x=nx;y=ny;moving=true;
        }
    }
    private static float returnAxis(float value,float dt){
        float amount=Math.max(Math.abs(value)*.3f,.18f)*dt;
        return Math.abs(value)<=amount?0:value-Math.copySign(amount,value);
    }
    private static float unlockCurve(float t){
        if(t<=0)return 0;if(t>=1)return 1;
        float lo=0,hi=1,u=.5f;
        for(int i=0;i<16;i++){
            u=(lo+hi)*.5f;float a=1-u;
            float x=3*a*a*u*.2f+3*a*u*u*.25f+u*u*u;
            if(x<t)lo=u;else hi=u;
        }
        float a=1-u;return 3*a*a*u*.65f+3*a*u*u+u*u*u;
    }
    public void follow(float x,float y){} // Stereo does not follow the clock's AOD region.
    public void pause(){last=-1;sensorStamp=-1;sensorAge=1;rx=ry=rz=0;quiet=1;}
    public boolean active(){return elapsed<duration||moving||x!=0||y!=0;}
}
