package org.aliveclean;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Original ColorOS spring, driving the actual bounds of the two original faces. */
final class NativeClockSceneTransition {
    private final FrameLayout parent;
    private final View root;
    private final NativeClockFaces faces;
    private View incoming,progressView;
    private final RectF from=new RectF(),to=new RectF(),localTo=new RectF();
    private Object spring;
    private Method abort;
    private ViewTreeObserver.OnPreDrawListener pending;
    private Runnable completed;
    private boolean running;

    NativeClockSceneTransition(View root,FrameLayout parent,NativeClockFaces faces){
        this.root=root;this.parent=parent;this.faces=faces;
        parent.setClipChildren(false);
        parent.setClipToPadding(false);
        if(root instanceof android.view.ViewGroup){
            ((android.view.ViewGroup)root).setClipChildren(false);
            ((android.view.ViewGroup)root).setClipToPadding(false);
        }
    }
    boolean running(){return running||pending!=null;}
    RectF capture(){
        RectF bounds=new RectF();faces.numberBounds(bounds);
        Matrix matrix=new Matrix();faces.active().transformMatrixToGlobal(matrix);matrix.mapRect(bounds);
        cancel();return bounds;
    }
    void start(View previous,RectF source,Runnable done){
        incoming=faces.active();completed=done;
        if(previous==incoming||source.isEmpty()||!root.isAttachedToWindow()) {finish();return;}
        from.set(source);
        // One visible clock moves between the measured endpoints. Drawing both
        // scenes with complementary alpha produces two overlapping digit sets.
        incoming.setAlpha(0);
        pending=()->{
            removePending();
            faces.numberBounds(localTo);
            Matrix matrix=new Matrix();incoming.transformMatrixToGlobal(matrix);matrix.mapRect(to,localTo);
            if(to.isEmpty()){finish();return true;}
            // Both endpoints must use the animated child's parent coordinates.
            // System editor previews scale their ancestors; screen-pixel deltas
            // applied directly as local translations overshoot in that host.
            Matrix parentMatrix=new Matrix(),inverse=new Matrix();
            parent.transformMatrixToGlobal(parentMatrix);
            if(!parentMatrix.invert(inverse)){finish();return true;}
            inverse.mapRect(from);inverse.mapRect(to);
            try {startSpring();}catch(Exception failure){
                NativeClockLoadState.failure("Clock scene spring",failure);finish();
            }
            return true;
        };
        parent.getViewTreeObserver().addOnPreDrawListener(pending);
        parent.requestLayout();parent.invalidate();
    }
    private void startSpring()throws Exception{
        Context plugin=root.getContext().createPackageContext("com.oplus.keyguard.personality.clocks",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader=plugin.getClassLoader();String ns="com.oplus.keyguard.clock.common.transition.";
        Class<?> type=loader.loadClass(ns+"SpringTransition"),property=loader.loadClass(ns+"AnimationProperty");
        Class<?> update=loader.loadClass(ns+"AnimationUpdateListener"),listener=loader.loadClass(ns+"AnimationListener");
        View progress=new View(root.getContext());progress.setAlpha(0);
        // Native TransitionDrawUpdater is driven by attachment/pre-draw. An
        // unattached dummy View queues the transition forever.
        progressView=progress;parent.getOverlay().add(progress);
        spring=type.getConstructor(View.class,loader.loadClass("x6.l")).newInstance(progress,null);
        abort=type.getMethod("abortTransToViewSilent");
        Object tick=Proxy.newProxyInstance(loader,new Class<?>[]{update},(p,m,a)->{
            if(m.getName().equals("onAnimationUpdate"))frame(((Number)a[0]).floatValue());return null;
        });
        Object end=Proxy.newProxyInstance(loader,new Class<?>[]{listener},(p,m,a)->{
            if(m.getName().equals("onAnimationEnd")&&running)finish();return null;
        });
        // Same zero-bounce / 0.3 response as the native AOD/notification profile.
        Object spec=property.getConstructor(String.class,long.class,android.view.animation.Interpolator.class,long.class,float.class,float.class,Float.class,update,listener,Float.class)
                .newInstance("OpenAliveClockScene",0L,new android.view.animation.LinearInterpolator(),0L,0f,.3f,.001f,tick,end,null);
        running=true;frame(0);
        type.getMethod("setAlpha",float.class,boolean.class,loader.loadClass(ns+"TransitionSet"),property,loader.loadClass(ns+"TransitionCustomAction"))
                .invoke(spring,1f,true,null,spec,null);
    }
    private void frame(float progress){
        if(incoming==null)return;
        float p=Math.max(0,Math.min(1,progress));
        // The incoming original renderer travels from the current glyph bounds,
        // including burn-in offsets, to its real laid-out lockscreen position.
        // A compact time row and a two-line artwork have different aspect ratios.
        // Height alone enlarges the wide artwork beyond both transition endpoints.
        float scale=Math.min(from.height()/Math.max(1,to.height()),from.width()/Math.max(1,to.width()));
        incoming.setPivotX(localTo.centerX());incoming.setPivotY(localTo.centerY());
        incoming.setScaleX(scale+(1-scale)*p);incoming.setScaleY(scale+(1-scale)*p);
        incoming.setTranslationX((from.centerX()-to.centerX())*(1-p));
        incoming.setTranslationY((from.centerY()-to.centerY())*(1-p));
        incoming.setAlpha(1);
    }
    void cancel(){
        removePending();running=false;
        if(spring!=null)try{abort.invoke(spring);}catch(Exception ignored){}
        spring=null;finish();
    }
    private void removePending(){
        if(pending!=null){if(parent.getViewTreeObserver().isAlive())parent.getViewTreeObserver().removeOnPreDrawListener(pending);pending=null;}
    }
    private void finish(){
        running=false;removePending();
        if(progressView!=null){parent.getOverlay().remove(progressView);progressView=null;}
        if(incoming!=null){incoming.setAlpha(1);incoming.setScaleX(1);incoming.setScaleY(1);incoming.setTranslationX(0);incoming.setTranslationY(0);incoming=null;}
        Runnable done=completed;completed=null;if(done!=null)done.run();
    }
}
