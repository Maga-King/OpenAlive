package org.aliveclean;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.FloatProperty;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;

/** Android animator and matrix port of the official photo scenes. Owned by the EGL looper. */
final class SceneMotion {
    // Packet layout shared with native/renderer.rs. Matrices retain official coordinates here.
    static final int COUNT=60, LENS=3, GLASS=13, GROUND=19, FULL=21, MASK=23,
            PHOTO_MATRIX=25, DECOR_MATRIX=41;
    // Lens: blur, scale, distortion, decorator alpha/angle, x/y, speed, ripple, photo angle.
    final float[] values=new float[COUNT];
    private final ByteBuffer packet=ByteBuffer.allocateDirect(COUNT*4).order(ByteOrder.nativeOrder());
    private final FloatBuffer floats=packet.asFloatBuffer();
    private final SceneMatrices matrices;
    private final int aod,lock,home;
    private final boolean textureTracks;
    private final float expandedScale;
    private final Random random=new Random();
    private AnimatorSet animation;
    private ObjectAnimator positionX,positionY;
    private float positionStartX,positionStartY;
    private long lastFrame;
    private float aodX,aodY=.335f;
    private int mode;
    private static final Interpolator DEFAULT=new AccelerateDecelerateInterpolator();
    private static final Interpolator LINEAR=new LinearInterpolator();
    private static final Interpolator EASE=path(.33f,0,.67f,1);
    private static final Interpolator EXPAND=path(.13f,.04f,0,1);
    private static final Interpolator COLLAPSE=path(.37f,.04f,.04f,1);
    private static final Interpolator DISTORT=path(0,.5f,0,1);
    private static final Interpolator ROTATE=path(.17f,0,.67f,1);
    private static final FloatProperty<SceneMotion>[] PROPERTIES=properties();

    SceneMotion(int width,int height,int mode,int aod,int lock,int home) {
        this(width,height,mode,aod,lock,home,true);
    }
    SceneMotion(int width,int height,int mode,int aod,int lock,int home,boolean textureTracks) {
        this.textureTracks=textureTracks;
        this.mode=mode;this.aod=aod;this.lock=lock;this.home=home;
        expandedScale="2.33".equals(new DecimalFormat("#.##").format((double)height/width))?1.3f:1.24f;
        matrices=new SceneMatrices(width,height);
        values[mode]=1;
        System.arraycopy(lensState(mode),0,values,LENS,10);
        values[LENS+9]=-360;
        System.arraycopy(glassState(mode),0,values,GLASS,6);
        values[GROUND]=values[GROUND+1]=mode==1?0:1;
        values[FULL]=values[FULL+1]=mode==0?1:0;
        values[58]=1; // Lock/launcher composition enabled for the current independent selections.
        values[59]=1; // Packet ABI version.
    }

    private static Interpolator path(float a,float b,float c,float d) {return new PathInterpolator(a,b,c,d);}
    @SuppressWarnings("unchecked") private static FloatProperty<SceneMotion>[] properties() {
        FloatProperty<SceneMotion>[] result=new FloatProperty[COUNT];
        for(int i=0;i<COUNT;i++) {
            final int slot=i;
            result[i]=new FloatProperty<SceneMotion>("sceneValue"+i) {
                public Float get(SceneMotion scene){return scene.values[slot];}
                public void setValue(SceneMotion scene,float value){scene.values[slot]=value;}
            };
        }
        return result;
    }
    private float[] lensState(int scene) {
        if(scene==0)return new float[]{22,.22f,9,1,0,aodX,aodY,1,0,Float.NaN};
        return new float[]{scene==1?0:16,expandedScale,0,0,-149,0,0,0,1.24f,-360};
    }
    private float[] glassState(int scene) {
        float width=scene==1?(lock==2?.05f:.04f):(lock==2?.072f:.076f);
        return new float[]{scene==0?.87f:1,width,width,scene==1?0:1,
                scene==1?.03f:0,scene==1&&lock==2?.007f:0};
    }
    static int transition(int from,int to) {
        if(from==0)return to==1?0:2;
        if(to==0)return from==1?1:3;
        return from==1?4:5;
    }
    static float nearestRotation(float current,float target) {
        float distance=Math.floorMod((int)(target-current),360);
        return target+(distance>=180?360-distance:-distance);
    }
    private ObjectAnimator track(int slot,float target,long duration,Interpolator curve) {
        ObjectAnimator animator=ObjectAnimator.ofFloat(this,PROPERTIES[slot],values[slot],target);
        animator.setDuration(duration);animator.setInterpolator(curve);return animator;
    }
    private void lens(ArrayList<Animator> tracks,int tr,int next) {
        float[] target=lensState(next);
        long[] ms={1000,1000,1000,1000,1000,1000,1000,1000,1000,1000};
        Interpolator[] curves={DEFAULT,DEFAULT,DEFAULT,DEFAULT,DEFAULT,DEFAULT,DEFAULT,DEFAULT,DEFAULT,DEFAULT};
        long rippleDelay=0;
        float angleStart=values[LENS+9];
        if(tr==0||tr==2) {
            ms[0]=183;curves[0]=EASE;ms[3]=33;curves[3]=EASE;
            ms[1]=ms[5]=ms[6]=tr==0?950:1000;
            curves[1]=curves[5]=curves[6]=tr==0?EXPAND:path(0,0,0,1);
            ms[2]=tr==0?800:900;curves[2]=DISTORT;
            ms[8]=tr==0?950:1000;curves[8]=EASE;
            ms[9]=24;curves[9]=ROTATE;ms[7]=0;
            angleStart=nearestRotation(angleStart,target[9]);
        } else if(tr==1||tr==3) {
            for(int i=0;i<8;i++)ms[i]=650;
            curves[0]=path(.17f,0,.83f,1);curves[3]=EASE;
            curves[4]=path(.37f,.02f,.04f,1);
            curves[1]=curves[5]=curves[6]=COLLAPSE;curves[2]=EASE;
            rippleDelay=650;ms[8]=0;
        } else if(tr==4) {
            ms[0]=3840;curves[0]=EASE;
            ms[1]=ms[5]=ms[6]=650;curves[1]=curves[5]=curves[6]=EXPAND;
            ms[2]=600;curves[2]=DISTORT;ms[8]=650;curves[8]=EASE;
            ms[9]=24;curves[9]=ROTATE;
        } else {
            ms[0]=0;curves[0]=EASE;curves[8]=EASE;
            ms[9]=24;curves[9]=ROTATE;ms[7]=0;
        }
        // Match the official builder's order, including its idle angle animator for AOD.
        for(int i:new int[]{0,1,2,8,3,4,5,6,7,9}) {
            if(Float.isNaN(target[i])) {tracks.add(ValueAnimator.ofFloat(0,1));continue;}
            ObjectAnimator animator;
            if(i==9){animator=ObjectAnimator.ofFloat(this,PROPERTIES[LENS+9],angleStart,target[9]);animator.setDuration(ms[i]);animator.setInterpolator(curves[i]);}
            else animator=track(LENS+i,target[i],ms[i],curves[i]);
            if(i==5){positionX=animator;positionStartX=values[LENS+5];}
            if(i==6){positionY=animator;positionStartY=values[LENS+6];}
            if(i==8)animator.setStartDelay(rippleDelay);
            tracks.add(animator);
        }
    }
    private void glass(ArrayList<Animator> tracks,int tr,int next) {
        float[] target=glassState(next);
        for(int i=0;i<6;i++) {
            long ms;Interpolator curve;
            if(tr==0||tr==5) {
                ms=i==3?(tr==0&&lock==2?470:425):850;curve=path(.23f,.09f,.16f,1);
            } else if(tr==2) {ms=200;curve=path(.23f,.09f,.16f,1);}
            else if(tr==3) {ms=i==0?800:200;curve=path(.1f,0,.3f,1);}
            else {ms=i<4?1500:700;curve=i==0?path(.22f,.66f,.15f,1):path(.48f,.22f,.46f,1);}
            tracks.add(track(GLASS+i,target[i],ms,curve));
        }
    }
    private void ground(ArrayList<Animator> tracks,int tr,int next) {
        final float first=values[GROUND],second=values[GROUND+1],target=next==1?0:1;
        ValueAnimator animator=ValueAnimator.ofFloat(0,1);
        animator.setDuration(tr==0||tr==5?1500:200);animator.setInterpolator(LINEAR);
        animator.addUpdateListener(a->{float t=(float)a.getAnimatedValue();values[GROUND]=first+(target-first)*t;values[GROUND+1]=second+(target-second)*t;});
        if(tr==1||tr==4)animator.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator a){if(values[GROUND+1]>.999){values[MASK]=random.nextFloat()*100;values[MASK+1]=random.nextFloat()*100;}}
        });
        tracks.add(animator);
    }
    private void full(ArrayList<Animator> tracks,int tr,int next) {
        if(tr>3)return; // Official full-AOD controller has no lock/home animator.
        for(int i=0;i<2;i++) {
            long ms;Interpolator curve;
            if(tr==0){ms=250;curve=i==0?path(.4f,0,.2f,1):path(.78f,.01f,.89f,.57f);}
            else if(tr==1){ms=1300;curve=path(.19f,1.91f,.47f,1.02f);}
            else if(tr==2){ms=250;curve=path(.4f,0,.2f,1);}
            else {ms=800;curve=path(.25f,.1f,.25f,1);}
            tracks.add(track(FULL+i,next==0?1:0,ms,curve));
        }
    }
    long blendDuration(int tr) {
        long[] a=aod==0?new long[]{950,650,300,650,650,300}:
                aod==101?new long[]{250,1200,350,350,300,300}:new long[]{400,400,400,400,400,400};
        long[] l=lock==0?new long[]{250,250,250,250,400,400}:
                lock>=4?new long[]{250,250,250,250,500,500}:
                lock==3?new long[]{1500,200,200,200,200,1500}:new long[]{850,200,200,200,500,850};
        long[] h={250,250,250,250,500,500};
        return tr<2?Math.max(a[tr],l[tr]):tr<4?Math.max(a[tr],h[tr]):Math.max(l[tr],h[tr]);
    }
    // Also used by the on-device differential test to seek the real Android animators.
    AnimatorSet prepare(int next) {
        if(next<0||next>2)throw new IllegalArgumentException("scene "+next);
        boolean interrupted=animation!=null&&animation.isStarted();
        if(!active())lastFrame=0;
        if(animation!=null)animation.cancel();
        int previous=mode,tr=transition(previous,next);
        ArrayList<Animator> tracks=new ArrayList<>();
        if(aod==0)lens(tracks,tr,next);else if(aod==101)full(tracks,tr,next);
        if(textureTracks){if(lock==1||lock==2)glass(tracks,tr,next);else if(lock==3)ground(tracks,tr,next);}
        final float[] source={values[0],values[1],values[2]};
        ValueAnimator blend=ValueAnimator.ofFloat(0,1);
        blend.setDuration(blendDuration(tr));blend.setInterpolator(tr==2||tr==3?EASE:LINEAR);
        blend.addUpdateListener(a->{float t=(float)a.getAnimatedValue();for(int i=0;i<3;i++)values[i]=source[i]+((next==i?1:0)-source[i])*t;});
        tracks.add(blend);
        animation=new AnimatorSet();animation.playTogether(tracks);
        if(previous==0&&!interrupted)animation.setStartDelay(150);
        mode=next;return animation;
    }
    void change(int next){if(mode!=next)prepare(next).start();}
    /** Switch composition owner without creating a second texture timeline. */
    void preserveMode(int next){mode=next;}
    void finish(){if(animation!=null)animation.end();}
    boolean active(){return animation!=null&&animation.isStarted()||aod==0&&mode==0;}
    boolean transitionActive(){return animation!=null&&animation.isStarted();}
    void pause(boolean paused){lastFrame=0;if(animation!=null){if(paused)animation.pause();else animation.resume();}}
    void close(){if(animation!=null)animation.cancel();lastFrame=0;}
    void advance(long frameNanos) {
        advance(frameNanos,1f);
    }
    void advance(long frameNanos,float speed) {
        if(lastFrame!=0&&frameNanos>=lastFrame&&aod==0&&values[LENS+7]>0)
            values[LENS+9]=(values[LENS+9]-((frameNanos-lastFrame)/1_000_000_000f*40f)*values[LENS+7]*speed)%360f;
        lastFrame=frameNanos;
        if(mode==0&&(animation==null||!animation.isStarted())){values[LENS+5]=aodX;values[LENS+6]=aodY;}
    }
    void aodPosition(float x,float y){aodX=x;aodY=y;if(mode==0&&(animation==null||!animation.isStarted())){values[LENS+5]=x;values[LENS+6]=y;}}
    void followClock(float x,float y){
        aodPosition(x,y);
        if(mode==0&&animation!=null&&animation.isStarted()){
            // Keep the original play time/curve, retarget only the moving ColorOS anchor.
            if(positionX!=null){positionX.setFloatValues(positionStartX,x);if(animation.getCurrentPlayTime()>=positionX.getTotalDuration())values[LENS+5]=x;}
            if(positionY!=null){positionY.setFloatValues(positionStartY,y);if(animation.getCurrentPlayTime()>=positionY.getTotalDuration())values[LENS+6]=y;}
        }
    }
    ByteBuffer packet() {
        matrices.write(values);
        floats.position(0);floats.put(values);packet.position(0);return packet;
    }
}
