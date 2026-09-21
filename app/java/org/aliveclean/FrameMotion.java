package org.aliveclean;

import android.animation.*;
import android.opengl.Matrix;
import android.util.FloatProperty;
import android.view.animation.*;
import java.nio.*;
import java.util.ArrayList;

/** Independent photo-stack tracks. The stable sphere packet and curves are unchanged. */
final class FrameMotion {
    // blur, rotation, scale, content scale, x, y, video microseconds, photo alpha.
    final float[] values=new float[8];
    final float[] ratios=new float[3];
    final PhotoStyle style;
    private final float aspect;
    private final int width,height;
    private final boolean paired;
    private int mode;
    private AnimatorSet animation;
    private ObjectAnimator positionX,positionY;
    private float anchorX,anchorY=.315f,positionStartX,positionStartY;
    private final float[] data=new float[36],model=new float[16],content=new float[16],scratch=new float[16];
    private final ByteBuffer packet=ByteBuffer.allocateDirect(36*4).order(ByteOrder.nativeOrder());
    private final FloatBuffer floats=packet.asFloatBuffer();
    private static final Interpolator DEFAULT=new AccelerateDecelerateInterpolator(),LINEAR=new LinearInterpolator(),
        EASE=new PathInterpolator(.33f,0,.67f,1);
    private static final FloatProperty<FrameMotion>[] PROPERTIES=properties();
    FrameMotion(int width,int height,int scene){this(width,height,scene,false);}
    FrameMotion(int width,int height,int scene,boolean paired){this(width,height,scene,paired,PhotoStyle.get(1));}
    FrameMotion(int width,int height,int scene,boolean paired,PhotoStyle style){this.width=width;this.height=height;this.paired=paired&&style.id==1;this.style=style;aspect=(float)height/width;mode=scene;System.arraycopy(state(scene),0,values,0,8);ratios[scene]=1;}
    private float[] state(int scene){return scene==0?new float[]{style.blur,style.rotation,style.scale,style.content,anchorX,anchorY,0,1}:new float[]{0,0,2.35f,aspect/2.35f,0,0,style.endMicros,scene==1&&!paired?1:0};}
    @SuppressWarnings("unchecked") private static FloatProperty<FrameMotion>[] properties(){
        FloatProperty<FrameMotion>[] result=new FloatProperty[8];
        for(int i=0;i<8;i++){final int slot=i;result[i]=new FloatProperty<FrameMotion>("frameValue"+i){public Float get(FrameMotion m){return m.values[slot];}public void setValue(FrameMotion m,float v){m.values[slot]=slot==6?(long)v:v;}};}
        return result;
    }
    AnimatorSet prepare(int next,long commonBlendDuration){
        if(next<0||next>2)throw new IllegalArgumentException("scene "+next);
        if(animation!=null)animation.cancel();
        int tr=SceneMotion.transition(mode,next);float[] target=state(next);ArrayList<Animator> tracks=new ArrayList<>();
        for(int i=0;i<8;i++){
            long duration=400,delay=0;Interpolator curve=DEFAULT;
            PhotoStyle.Track spec=style.track(tr,i);
            if(spec!=null){duration=spec.duration;delay=spec.delay;curve=spec.curve;}
            if(i==6){duration=(long)Math.abs(values[i]-target[i])/1000;curve=LINEAR;}
            if(i==7&&tr==4){duration=800;curve=EASE;}
            ObjectAnimator a=ObjectAnimator.ofFloat(this,PROPERTIES[i],values[i],target[i]);
            a.setDuration(duration);a.setStartDelay(delay);a.setInterpolator(curve);tracks.add(a);
            if(i==4){positionX=a;positionStartX=values[i];}else if(i==5){positionY=a;positionStartY=values[i];}
        }
        float[] from=ratios.clone();ValueAnimator blend=ValueAnimator.ofFloat(0,1);
        blend.setDuration(tr<4||style.id!=1?Math.max(style.durations[tr],commonBlendDuration):commonBlendDuration);
        blend.setInterpolator(tr==2||tr==3?EASE:LINEAR);
        blend.addUpdateListener(a->{float t=(float)a.getAnimatedValue();for(int i=0;i<3;i++)ratios[i]=from[i]+((i==next?1:0)-from[i])*t;});
        tracks.add(blend);animation=new AnimatorSet();animation.playTogether(tracks);mode=next;return animation;
    }
    void change(int next,SceneMotion common){
        if(next==mode)return;
        // A paired photo has the same full-screen endpoint on lock and home.
        // Fingerprint KEYGUARD -> UNLOCK must not restart an in-flight expansion.
        if(paired&&mode!=0&&next!=0){mode=next;return;}
        boolean interrupted=active();int previous=mode;
        AnimatorSet a=prepare(next,common.blendDuration(SceneMotion.transition(mode,next)));
        if(previous==0&&!interrupted)a.setStartDelay(150);a.start();
    }
    void finish(){if(animation!=null)animation.end();}
    void pause(boolean paused){if(animation!=null){if(paused)animation.pause();else animation.resume();}}
    boolean active(){return animation!=null&&animation.isStarted();}
    boolean expanded(){return mode!=0&&ratios[0]<.00001f&&Math.abs(values[1])<.00001f&&Math.abs(values[2]-2.35f)<.00001f&&Math.abs(values[5])<.00001f&&values[6]>=style.endMicros-1;}
    void followClock(float x,float y){
        if(style.id==1)return;
        anchorX=x;anchorY=y;
        if(mode!=0)return;
        if(!active()){values[4]=x;values[5]=y;return;}
        if(positionX!=null){positionX.setFloatValues(positionStartX,x);if(animation.getCurrentPlayTime()>=positionX.getTotalDuration())values[4]=x;}
        if(positionY!=null){positionY.setFloatValues(positionStartY,y);if(animation.getCurrentPlayTime()>=positionY.getTotalDuration())values[5]=y;}
    }
    void close(){if(animation!=null)animation.cancel();}
    void ratios(SceneMotion common){
        System.arraycopy(ratios,0,common.values,0,3);
        // The official chromakey shader explicitly provides this branch to avoid
        // a full-screen ghost when a third state interrupts an AOD transition.
        common.values[58]=0;
        if(paired&&mode!=0){common.values[mode]=ratios[1]+ratios[2];common.values[mode==1?2:1]=0;}
    }
    ByteBuffer packet(){
        // Official model T(x*w,-y*h) S(scale) R(angle). The original square mesh
        // has side min(w,h); projection is orthographic with top-positive Y.
        Matrix.setIdentityM(model,0);Matrix.translateM(model,0,width*values[4],-height*values[5],0);
        Matrix.scaleM(model,0,values[2],values[2],1);Matrix.rotateM(model,0,values[1],0,0,1);
        Matrix.setIdentityM(scratch,0);Matrix.scaleM(scratch,0,2f/width,-2f/height,1);
        Matrix.multiplyMM(data,0,scratch,0,model,0);
        float half=Math.min(width,height)/2f;Matrix.scaleM(data,0,half,half,1);
        // Original content transform: T(.5) S(aspect/content,1/content) T(-.5).
        Matrix.setIdentityM(content,0);Matrix.translateM(content,0,.5f,.5f,0);
        Matrix.scaleM(content,0,aspect/values[3],1/values[3],1);Matrix.translateM(content,0,-.5f,-.5f,0);
        System.arraycopy(content,0,data,16,16);data[32]=values[7];data[33]=values[0];data[34]=values[6];data[35]=1;
        floats.position(0);floats.put(data);packet.position(0);return packet;
    }
}
