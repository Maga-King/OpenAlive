package org.aliveclean;

/** One continuous wallpaper reveal, using HyperOS's full-AOD spring timings.
 * Night dimming is part of the final target, never a second wake animation.
 */
final class VivoWallpaperDim {
    private double value=1,target=1,velocity,omega=2*Math.PI/.5;
    private long previous;
    private boolean active;
    void target(float nightLevel,float aodMask,int mode,boolean animate,long now){
        if(now!=0)sample(now);
        double next=nightLevel*(mode==0?1-aodMask:1);
        if(!animate){value=target=next;velocity=0;active=false;previous=now;return;}
        if(next==target){if(now==0)previous=0;return;}
        target=next;omega=2*Math.PI/(mode==0?.72:.5);previous=now;active=true;
    }
    float sample(long now){
        if(active&&previous!=0){
            double t=Math.max(0,(now-previous)/1e9),x=value-target,b=velocity+omega*x;
            double e=Math.exp(-omega*t);
            value=target+(x+b*t)*e;velocity=(velocity-omega*b*t)*e;
            if(Math.abs(value-target)<.00002&&Math.abs(velocity)<.0002){value=target;velocity=0;active=false;}
        }
        previous=now;return (float)value;
    }
    boolean active(){return active;}
    void pause(){value=target;velocity=0;active=false;}
}
