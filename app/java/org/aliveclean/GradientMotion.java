package org.aliveclean;

import android.content.res.AssetManager;
import org.json.*;
import java.io.IOException;

/** Original six linked wave tracks; elapsed time counts visible frames only. */
final class GradientMotion implements WallpaperMotion {
    static final String[] COLORS={"a_inner","b_inner","a_cp1","b_cp1","a_cp2","b_cp2",
        "a_cp3","b_cp3","a_cp4","b_cp4","a_cp5","b_cp5","a_cp6","b_cp6","a_outer","b_outer"};
    final GradientSpline[] splines=new GradientSpline[6];
    private final float[] palette=new float[64];
    private final float[][] initial=new float[6][5],points=new float[6][5],waves=new float[6][5];
    private double seconds,shapeSeconds;
    private long last=-1;
    GradientMotion(AssetManager assets,int variant)throws IOException{
        String slug=variant==301?"damos":variant==302?"jiguang":variant==303?"yuanhai":null;
        if(slug==null)throw new IOException("Unknown gradient variant: "+variant);
        try{
            JSONObject config=new JSONObject(AssetGl.text(assets,"gradient/"+slug+".json"));
            JSONObject colors=config.getJSONObject("colors");
            for(int i=0;i<COLORS.length;i++){
                JSONArray rgba=colors.getJSONArray(COLORS[i]);
                for(int c=0;c<4;c++)palette[i*4+c]=(float)rgba.getDouble(c);
            }
            for(int curve=0;curve<6;curve++){
                JSONObject source=config.getJSONObject("splines").getJSONObject("spline"+(curve+1));
                float[] knots=new float[5];
                for(int i=0;i<5;i++){
                    knots[i]=(float)source.getJSONArray("x").getDouble(i)/1080f;
                    initial[curve][i]=(float)source.getJSONArray("y").getDouble(i);
                }
                splines[curve]=new GradientSpline(knots);
            }
            update(0);
        }catch(JSONException|IllegalArgumentException error){throw new IOException("Invalid gradient configuration",error);}
    }
    float[] colors(){return palette;}
    float time(){return (float)seconds;}
    public void change(int scene,boolean animate){} // Original palettes and shapes are shared by all scenes.
    public void follow(float x,float y){}
    public void pause(){last=-1;}
    public boolean active(){return false;} // RenderLoop owns continuous/finite scheduling.
    public void advance(long now,float speed){
        if(last>=0){
            double delta=Math.max(0,(now-last)/1_000_000_000d);
            seconds+=delta;
            // Original color time is elapsed time; only shape motion slows down.
            shapeSeconds+=Math.min(.05,delta)*Math.max(0,Math.min(1,speed));
        }
        last=now;update((float)shapeSeconds);
    }
    private static float wave(float x,float amplitude,float frequency,float phase,float center){
        return (float)(amplitude*Math.sin(frequency*x+phase)+center);
    }
    private static float mixWave(float x,float t,double modulation){
        return .5f+.5f*wave(x,(float)(Math.sin(modulation*t)*.20000000298023224+.8),
            (float)((Math.sin(t*.22+444)*.20000000298023224+.8)*.0029088822193443775),t*.6f+1.5707964f,0);
    }
    private void update(float t){
        float phase=t%30,blend=phase<5?phase/5:phase<25?1:(30-phase)/5;
        if(phase<5)blend=blend*blend*(3-2*blend);
        for(int p=0;p<5;p++){
            float x=splines[0].knots[p]*1080;
            // Dependency order is b,f,g,d,c,e, as in the original renderer.
            waves[0][p]=wave(x,(float)((Math.sin(t*.5)*.20000000298023224+.8)*-100),
                (float)((Math.sin(t*.3)*.4000000059604645+.6)*.005817764438688755),t+1.5707964f,300);
            waves[4][p]=wave(x,(float)((Math.sin(t*.2)*.20000000298023224+.8)*-525),
                (float)((Math.sin(t*.32)*.20000000298023224+.8)*.0029088822193443775),t*.6f+1.5707964f,1375);
            waves[5][p]=wave(x,(float)((Math.sin(t*.3)*.20000000298023224+.8)*-100),
                (float)((Math.sin(t*.3)*.4000000059604645+.6)*.005817764438688755),-t,2050);
            waves[2][p]=waves[0][p]+100+(waves[4][p]-waves[0][p]-300)*mixWave(x,t,.4);
            waves[1][p]=waves[0][p]+50+(waves[2][p]-waves[0][p]-100)*mixWave(x,t,.2);
            waves[3][p]=waves[2][p]+100+(waves[4][p]-waves[2][p]-200)*mixWave(x,t,.3);
            for(int c=0;c<6;c++)points[c][p]=(initial[c][p]+(waves[c][p]-initial[c][p])*blend)/2340f;
        }
        for(int c=0;c<6;c++)splines[c].interpolate(points[c]);
    }
}
