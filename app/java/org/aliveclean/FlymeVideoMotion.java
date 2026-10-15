package org.aliveclean;

/** Flyme 9 video clock. Source time survives visibility and scene changes. */
final class FlymeVideoMotion implements WallpaperMotion {
    private static final long NORMAL=7_000_000_000L,END=8_000_000_000L;
    private static final float SLOW=.3333f;
    private final long loopEndUs;
    private long last=-1,activityStart=-1,nextTick=-1;
    private double sourceUs;
    private float speed=1;
    private boolean visible,continuous,running=true,tick;

    FlymeVideoMotion(long loopEndUs){
        if(loopEndUs<=0)throw new IllegalArgumentException("Invalid video loop endpoint");
        this.loopEndUs=loopEndUs;
    }
    void visible(boolean value){
        if(visible==value)return;
        visible=value;pause();if(value)interaction();
    }
    void continuous(boolean value){
        if(continuous==value)return;
        continuous=value;if(value)interaction();
    }
    void interaction(){activityStart=-1;nextTick=-1;speed=1;running=true;}
    long sourceTimeUs(){return (long)sourceUs%loopEndUs;}
    boolean takeTick(){boolean result=tick;tick=false;return result;}
    float playbackSpeed(){return speed;}
    int frameRate(){return (int)(speed*30+30);}
    public void change(int scene,boolean animate){interaction();}
    public void follow(float x,float y){} // The original video has no parallax or AOD clock tracking.
    public void pause(){last=-1;nextTick=-1;tick=false;}
    public boolean active(){return visible&&(continuous||running);}
    public void advance(long now,float ignored){
        tick=false;
        if(!visible)return;
        if(activityStart<0)activityStart=now;
        if(last>=0&&now<last){last=now;nextTick=now;return;}
        last=now;
        long age=Math.max(0,now-activityStart);
        if(!continuous&&age>=END){speed=SLOW;running=false;return;}
        if(!running&&!continuous)return;
        speed=continuous||age<=NORMAL?1:1+(SLOW-1)*(age-NORMAL)/(float)(END-NORMAL);
        int fps=frameRate();
        // The original advances by speed/fps for each scheduled update rather
        // than catching up missed wall time. Avoid a jump after a stalled frame.
        if(nextTick<0||now>=nextTick){
            sourceUs=(sourceUs+1_000_000d*speed/fps)%loopEndUs;
            long interval=1_000_000_000L/fps;
            nextTick=nextTick<0?now+interval:Math.max(nextTick+interval,now);tick=true;
        }
    }
}
