package org.aliveclean;

import android.content.res.AssetManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import java.io.IOException;
import org.json.*;

/** Three independent bubble tracks plus the original flowing background palette. */
final class BubbleMotion implements WallpaperMotion {
    static final int STRIDE=36,SIZE=STRIDE*3+52;
    final float[] values=new float[SIZE],from=new float[SIZE];
    private final float[][] states=new float[3][SIZE];
    private final int[][] duration=new int[3][3],delay=new int[3][3];
    private final Interpolator[][] curves=new Interpolator[3][3];
    private final Interpolator smooth=t->(float)(.5-.5*Math.cos(Math.PI*t));
    private final boolean keepLock;
    private int mode,previous,total,bgDuration,bgDelay;
    private Interpolator bgCurve;
    private long started,last;
    private boolean moving;
    float time;
    BubbleMotion(AssetManager assets,int variant,boolean dark,int scene,boolean keepLock)throws IOException {
        this.keepLock=keepLock;
        try {
            JSONArray config=new JSONArray(AssetGl.text(assets,"bubble/v"+variant+(dark?"_dark":"")+".json"));
            for(int s=0;s<3;s++){
                JSONObject entry=config.getJSONObject(s);JSONArray bubbles=entry.getJSONArray("spheres");
                for(int b=0;b<3;b++){JSONArray data=bubbles.getJSONArray(b);if(data.length()!=STRIDE)throw new JSONException("Bubble preset size");for(int i=0;i<STRIDE;i++)states[s][b*STRIDE+i]=(float)data.getDouble(i);}
                JSONArray bg=entry.getJSONArray("background");for(int i=0;i<52;i++)states[s][108+i]=(float)bg.getDouble(i);
            }
        }catch(JSONException e){throw new IOException("Invalid Bubble configuration",e);}
        mode=keepLock&&scene==2?1:scene;System.arraycopy(states[mode],0,values,0,SIZE);
    }
    public void follow(float x,float y){} // This family has a full composition, not a clock-centered orb.
    public void pause(){last=0;}
    public boolean active(){return moving;}
    private void track(int b,int component,int ms,int wait,Interpolator curve){duration[b][component]=ms;delay[b][component]=wait;curves[b][component]=curve;}
    public void change(int scene,boolean animate){
        if(keepLock&&scene==2)scene=1;if(scene==mode)return;
        previous=mode;mode=scene;System.arraycopy(values,0,from,0,SIZE);started=0;moving=animate;
        bgDuration=300;bgDelay=0;bgCurve=smooth;
        for(int b=0;b<3;b++)for(int c=0;c<3;c++)track(b,c,300,0,smooth);
        Interpolator position=new PathInterpolator(.52f,.1f,.21f,.99f),scale=new PathInterpolator(.52f,.07f,.21f,.99f);
        Interpolator linear=new PathInterpolator(.33f,0,.67f,1);
        if(previous==0){
            track(0,0,1400,0,position);track(0,1,1400,0,scale);
            track(2,0,1400,260,position);track(2,2,678,582,linear);
            bgDuration=833;bgDelay=230;bgCurve=new PathInterpolator(.17f,.17f,.67f,1);
            if(mode==2){track(1,1,1850,0,new PathInterpolator(.4f,.15f,.24f,.98f));track(1,2,1850,0,linear);}
        }else if(mode==0){
            track(0,0,700,0,position);track(0,1,700,0,new PathInterpolator(.52f,.07f,.21f,1.01f));
            track(2,0,523,0,position);track(2,2,261,0,linear);bgDuration=523;bgCurve=linear;
        }else if(previous==1&&mode==2){
            Interpolator expand=new PathInterpolator(.4f,-.15f,.24f,1.02f);
            track(0,0,1883,300,expand);track(0,1,1883,300,expand);
            track(1,1,1850,0,new PathInterpolator(.4f,.15f,.24f,.98f));track(1,2,1850,0,linear);
            track(2,0,1883,0,expand);track(2,1,1883,0,new PathInterpolator(.4f,.21f,.24f,.97f));bgDuration=1200;
        }
        total=bgDelay+bgDuration;
        // AnimatorSet promotes default 300 ms tracks to the longest property duration.
        for(int b=0;b<3;b++){
            int longest=Math.max(duration[b][0],Math.max(duration[b][1],duration[b][2]));
            int start=previous==0&&b==2?260:previous==1&&mode==2&&b==0?300:0;
            for(int c=0;c<3;c++){if(duration[b][c]==300){duration[b][c]=longest;delay[b][c]=start;}total=Math.max(total,delay[b][c]+duration[b][c]);}
        }
        if(!animate)System.arraycopy(states[mode],0,values,0,SIZE);
    }
    private float fraction(float elapsed,int ms,int wait,Interpolator curve){return curve.getInterpolation(Math.max(0,Math.min(1,(elapsed-wait)/ms)));}
    public void advance(long now,float speed){
        if(moving){
            if(started==0)started=now;float elapsed=(now-started)/1_000_000f;
            for(int b=0;b<3;b++){
                int offset=b*STRIDE;float other=fraction(elapsed,Math.max(duration[b][0],Math.max(duration[b][1],duration[b][2])),Math.min(delay[b][0],Math.min(delay[b][1],delay[b][2])),smooth);
                for(int i=0;i<STRIDE;i++){
                    int component=i<2?0:i==3?1:i==4?2:-1;
                    float t=component<0?other:fraction(elapsed,duration[b][component],delay[b][component],curves[b][component]);
                    values[offset+i]=from[offset+i]+(states[mode][offset+i]-from[offset+i])*t;
                    if(b==2&&previous==1&&mode==2&&i<2){float inverse=1-t,control=i==0?1006:422;values[offset+i]=inverse*inverse*from[offset+i]+2*t*inverse*control+t*t*states[mode][offset+i];}
                }
            }
            float t=fraction(elapsed,bgDuration,bgDelay,bgCurve);for(int i=108;i<SIZE;i++)values[i]=from[i]+(states[mode][i]-from[i])*t;
            if(elapsed>=total){System.arraycopy(states[mode],0,values,0,SIZE);moving=false;}
        }
        float delta=last==0?0:Math.min(.05f,Math.max(0,(now-last)/1_000_000_000f))*speed;last=now;time+=delta*.4f;
    }
}
