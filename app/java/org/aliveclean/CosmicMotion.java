package org.aliveclean;

import android.content.res.AssetManager;
import android.graphics.Color;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import org.json.*;
import java.io.*;

/** Cosmic's three official parameter sets and per-property transition curves. */
final class CosmicMotion {
    static final int SIZE=62;
    final float[] values=new float[SIZE];
    private final float[][] states=new float[3][SIZE];
    private final float[] from=new float[SIZE];
    private final Interpolator smooth=t->(float)(.5-.5*Math.cos(Math.PI*t));
    private Interpolator position=smooth,scale=smooth,background=smooth,material=smooth;
    private int mode,positionMs,scaleMs,scaleDelay,backgroundMs,materialMs,duration;
    private long started,last;
    private boolean transitioning;
    float time,angle;
    CosmicMotion(AssetManager assets,int variant,boolean dark,int scene)throws IOException{
        try{
            JSONObject config=new JSONObject(AssetGl.text(assets,"cosmic/v"+variant+(dark?"_dark":"")+".json"));
            String[] names={"aod","keyguard","unlock"};
            float[][] geometry={{0,.325f,.22f,1,1},{.764f,-.206f,1.181f,.8f,.8f},{0,0,1.55f,.6f,1}};
            for(int s=0;s<3;s++){
                float[] v=states[s];System.arraycopy(geometry[s],0,v,0,5);
                JSONObject object=config.getJSONObject(names[s]);
                String[] factors={"stripeDivergent","stripeThickness","highlightThickness","highlightStrength","alpha"};
                for(int i=0;i<5;i++)v[5+i]=(float)object.getDouble(factors[i]);
                String[] colors={"baseColor","stripeColor1","stripeColor2","stripeColor3","highlightColor"};
                for(int i=0;i<5;i++)color(v,10+4*i,object.getString(colors[i]));
                for(int r=0;r<4;r++){
                    JSONObject gradient=object.getJSONObject("rotate_"+(90*r));
                    color(v,30+r*8,gradient.getString("startColor"));color(v,34+r*8,gradient.getString("endColor"));
                }
            }
        }catch(JSONException|IllegalArgumentException e){throw new IOException("Invalid Cosmic configuration",e);}
        mode=scene;System.arraycopy(states[mode],0,values,0,SIZE);
    }
    private static void color(float[] values,int offset,String color){
        int c=Color.parseColor(color);values[offset]=Color.red(c)/255f;values[offset+1]=Color.green(c)/255f;
        values[offset+2]=Color.blue(c)/255f;values[offset+3]=Color.alpha(c)/255f;
    }
    void follow(float x,float y){
        states[0][0]=x;states[0][1]=y;
        if(mode==0&&!transitioning){values[0]=x;values[1]=y;}
    }
    void change(int next,boolean animate){
        if(next==mode)return;
        int previous=mode;mode=next;
        System.arraycopy(values,0,from,0,SIZE);started=0;
        positionMs=scaleMs=backgroundMs=materialMs=1000;scaleDelay=0;
        position=scale=background=material=smooth;
        if(next==0){
            int ms=previous==2?600:700;positionMs=scaleMs=backgroundMs=materialMs=ms;
            position=scale=background=previous==2?new PathInterpolator(.08f,.42f,.23f,1.02f):new PathInterpolator(.52f,.01f,.21f,.99f);
            if(previous==2){backgroundMs=0;material=position;}
        }else if(next==1&&previous==0){
            positionMs=scaleMs=1200;backgroundMs=1617;materialMs=700;
            position=new PathInterpolator(.15f,.06f,.21f,1);scale=new PathInterpolator(.48f,.05f,.21f,1);
            background=new PathInterpolator(.15f,.46f,.42f,1);
        }else if(next==2&&previous==0){
            positionMs=scaleMs=950;backgroundMs=1200;
            position=scale=new PathInterpolator(.52f,.01f,.21f,.99f);background=new PathInterpolator(.33f,0,.67f,1);
        }else if(next==2&&previous==1){
            positionMs=450;position=new PathInterpolator(.33f,0,.67f,1);
            scaleMs=433;scaleDelay=317;scale=new PathInterpolator(.34f,.03f,.16f,1);
        }
        duration=Math.max(1000,Math.max(positionMs,backgroundMs));transitioning=animate;
        if(!animate)System.arraycopy(states[mode],0,values,0,SIZE);
    }
    void pause(){last=0;}
    boolean active(){return transitioning;}
    void advance(long now,float speed){
        if(transitioning){
            if(started==0)started=now;
            float ms=(now-started)/1_000_000f;
            for(int i=0;i<SIZE;i++){
                int d=i<2?positionMs:i==2?scaleMs:i<5?1000:i<30?materialMs:backgroundMs;
                Interpolator curve=i<2?position:i==2?scale:i<5?smooth:i<30?material:background;
                float elapsed=ms-(i==2?scaleDelay:0);
                float t=d==0?1:curve.getInterpolation(Math.max(0,Math.min(1,elapsed/d)));
                values[i]=from[i]+(states[mode][i]-from[i])*t;
            }
            if(ms>=duration){System.arraycopy(states[mode],0,values,0,SIZE);transitioning=false;}
        }
        float delta=last==0?0:Math.min(.05f,Math.max(0,(now-last)/1_000_000_000f))*speed;
        last=now;time+=delta;angle=(angle+delta*30*values[3])%360;
    }
}
