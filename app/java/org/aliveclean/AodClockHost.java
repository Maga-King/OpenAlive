package org.aliveclean;

import android.animation.*;
import android.view.*;
import android.view.animation.PathInterpolator;
import java.lang.reflect.Method;
import java.util.*;

/** AOD ownership outlives its visible window, including the native AOD-to-OFF fade. */
final class AodClockHost {
    private ViewGroup root;
    private View scope,time,date;
    private AodClockView clock;
    private ValueAnimator fade,stockFade;
    private int generation;
    private boolean owning,holdingClock,holdingUnlock;
    private float stockAlpha;
    private final AodWidgetSpace widgets=new AodWidgetSpace();
    private final AodWidgetSpace notifications=new AodWidgetSpace();
    private View notificationStack;
    private final IdentityHashMap<View,Float> suppressed=new IdentityHashMap<>();
    private final IdentityHashMap<View,Float> written=new IdentityHashMap<>();
    private final Set<View> targets=Collections.newSetFromMap(new IdentityHashMap<View,Boolean>());
    private final Map<Class<?>,Method> artworkGetters=new HashMap<>(),dateGetters=new HashMap<>();
    private final ViewTreeObserver.OnPreDrawListener predraw=()->{
        if(root!=null){
            if(owning||holdingClock)maskContents();
            widgets.update(clockBottom());
            alignNotifications();
        }
        return true;
    };
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        public void onViewAttachedToWindow(View v){}
        public void onViewDetachedFromWindow(View v){hide();}
    };
    void widgets(View view,AodWidgetSpace.Bounds bounds){
        // Do not translate a parent shared with clock/artwork/notifications.
        if(view==null||root==null||view==root||descendant(time,view)||descendant(date,view)||descendant(clock,view)){widgets.clear();return;}
        widgets.bind(view,bounds);widgets.update(clockBottom());
    }
    boolean shown(){return clock!=null;}
    boolean ownsClock(){return owning;}
    boolean same(ViewGroup parent,View t,View d){return root==parent&&time==t&&date==d;}
    void show(ViewGroup parent,View t,View d){show(parent,t,d,null);}
    void show(ViewGroup parent,View t,View d,View clockScope){
        if(parent==null||!parent.isAttachedToWindow()||t==null||d==null||t==d||descendant(t,d)||descendant(d,t)){hide();return;}
        // A verified plugin scope may migrate into a SurfaceControlViewHost.
        // The AOD overlay itself must stay in the stable notification window.
        if(clockScope==null&&(!descendant(t,parent)||!descendant(d,parent))){hide();return;}
        attach(parent,t,d,clockScope);
    }
    static View nativeContent(View scope){
        if(scope==null)return null;
        View content=scope.findViewWithTag("org.aliveclean.native_clock_content");
        return content!=scope&&content instanceof ViewGroup&&descendant(content,scope)?content:null;
    }
    void showNative(ViewGroup parent,View content,View clockScope){
        if(parent==null||!parent.isAttachedToWindow()||content==null||content!=nativeContent(clockScope)){hide();return;}
        attach(parent,content,null,clockScope);
    }
    private void attach(ViewGroup parent,View t,View d,View clockScope){
        if(root==parent&&scope==clockScope&&clock!=null){
            cancelFade();cancelStockFade();holdingClock=false;holdingUnlock=false;stockAlpha=0;owning=true;time=t;date=d;maskContents();return;
        }
        hide();
        AodClockView next=new AodClockView(parent.getContext(),false);
        try{
            parent.addView(next,new ViewGroup.LayoutParams(-1,-1));
            next.active(true);next.refresh(System.currentTimeMillis());
            root=parent;clock=next;time=t;date=d;scope=clockScope;owning=true;stockAlpha=0;
            maskContents();
            parent.getViewTreeObserver().addOnPreDrawListener(predraw);parent.addOnAttachStateChangeListener(attachment);
        }catch(RuntimeException error){next.active(false);if(next.getParent()==parent)parent.removeView(next);hide();throw error;}
    }
    private void discover(View view){
        if("org.aliveclean.native_clock_content".equals(view.getTag())){target(view);return;}
        String name=view.getClass().getSimpleName();
        if(name.equals("ClockTimeView")||name.equals("DateMessageView")||name.equals("TextTimeTextView")){target(view);return;}
        if(name.equals("TextDateInformationView")){
            targetGetter(view,"getLocalDate",dateGetters);return;
        }
        if(name.equals("ShellMaterialContainer")){
            // The SDK's ShellMaterialView is a wrapper, not an Android View.
            // Target its actual ImageView, never the shared material container.
            targetGetter(view,"getMaterialImageView$KeyguardPersonalityClocks_release",artworkGetters);
            discoverCoe(view);return;
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)discover(group.getChildAt(i));}
    }
    private void targetGetter(View owner,String name,Map<Class<?>,Method> methods){
        Class<?> type=owner.getClass();
        try{
            if(!methods.containsKey(type)){Method method=null;try{method=type.getMethod(name);}catch(NoSuchMethodException ignored){}methods.put(type,method);}
            Method method=methods.get(type);if(method==null)return;
            Object result=method.invoke(owner);if(result instanceof View&&descendant((View)result,owner))target((View)result);
        }catch(ReflectiveOperationException ignored){} // Unknown material keeps native contents.
    }
    private void discoverCoe(View view){
        if(view.getClass().getSimpleName().equals("COETextureView")){target(view);return;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)discoverCoe(group.getChildAt(i));}
    }
    private void target(View view){
        // A material may own its own time/date. Mask each branch once so the
        // wake fade remains linear rather than multiplying parent/child alpha.
        for(View existing:targets)if(descendant(view,existing))return;
        Iterator<View> it=targets.iterator();while(it.hasNext())if(descendant(it.next(),view))it.remove();
        targets.add(view);
    }
    private void maskContents(){
        targets.clear();
        if(time!=null)target(time);
        if(date!=null)target(date);
        // Notifications can replace/reparent the baseline clock. Only inspect
        // this verified plugin subtree; preserve widgets and wallpaper depth.
        if(scope!=null)discover(scope);
        Iterator<Map.Entry<View,Float>> it=suppressed.entrySet().iterator();
        while(it.hasNext()){Map.Entry<View,Float> item=it.next();if(!targets.contains(item.getKey())){restore(item);written.remove(item.getKey());it.remove();}}
        for(View v:targets){
            float current=v.getTransitionAlpha();Float last=written.get(v);
            if(last==null||current!=last)suppressed.put(v,current);
            float next=suppressed.get(v)*stockAlpha;
            if(current!=next)v.setTransitionAlpha(next);
            written.put(v,next);
        }
    }
    void tick(long time){if(clock!=null)clock.tick(time);}
    void contentAlpha(float value){if(clock!=null){clock.setAlpha(Math.max(0,Math.min(1,value)));clock.active(value>0);}}
    private int clockBottom(){return !owning||clock==null||clock.getHeight()==0?0:clock.notificationTop();}
    int notificationTop(){
        int floor=clockBottom();
        return floor==0?0:Math.max(floor,widgets.bottom()==0?0:widgets.bottom()+Math.round(clock.getWidth()*.035f));
    }
    private void alignNotifications(){
        int floor=notificationTop();
        if(floor==0||root==null){notifications.restore();return;}
        if(notificationStack==null||!notificationStack.isAttachedToWindow()){
            int id=root.getResources().getIdentifier("notification_stack_scroller","id","com.android.systemui");
            View candidate=id==0?null:root.findViewById(id);
            notificationStack=candidate instanceof ViewGroup?candidate:null;
            notifications.bind(notificationStack,notificationStack==null?null:new AodNotificationBounds((ViewGroup)notificationStack));
        }
        // Preserve the platform's row size/stacking negotiation. Correct only
        // the dedicated stack's final placement, after actual widget occupancy.
        // Alignment can move upward too when a second widget row disappears.
        notifications.align(floor);
    }
    void leave(){leave(false);}
    // UNLOCK is announced before the native NormalUnlockAnim hides keyguard.
    // Keep only our existing clock-leaf mask through that interval. Wallpaper,
    // notifications, widgets and the launcher's own clock remain native.
    void leaveUnlocked(){
        if(owning||holdingClock){
            holdingUnlock=true;cancelStockFade();stockAlpha=0;maskContents();leave(true);
        }else leave(false);
    }
    void unlockFinished(){
        if(!holdingUnlock)return;
        releaseClock();if(clock==null)hide();
    }
    void leave(boolean waitForFrame){
        if(waitForFrame&&owning){owning=false;holdingClock=true;releaseNotificationSpace();}
        else if(!waitForFrame)releaseClock();
        if(clock==null){if(!holdingClock)hide();return;}
        if(fade!=null)return;
        if(clock.getAlpha()==0){removeFace();if(!holdingClock)hide();return;}
        final int token=++generation;
        fade=ValueAnimator.ofFloat(clock.getAlpha(),0);fade.setDuration(180);
        fade.setInterpolator(new PathInterpolator(.33f,0,.67f,1));
        fade.addUpdateListener(a->{if(token==generation)contentAlpha((float)a.getAnimatedValue());});
        fade.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(token==generation){fade=null;removeFace();if(!holdingClock)hide();}}});
        fade.start();
    }
    // Only a successfully submitted expanded wallpaper frame releases this gate.
    // No delay/timeout guesses at the duration of the photo animation.
    void frameReady(){
        if(!holdingClock||holdingUnlock||stockFade!=null)return;
        ValueAnimator animation=ValueAnimator.ofFloat(stockAlpha,1);stockFade=animation;
        animation.setDuration(167);animation.setInterpolator(new PathInterpolator(.33f,0,.67f,1));
        animation.addUpdateListener(a->{if(stockFade==a&&holdingClock){stockAlpha=(float)a.getAnimatedValue();maskContents();}});
        animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(stockFade==a){stockFade=null;releaseClock();if(clock==null)hide();}}});
        animation.start();
    }
    private void cancelStockFade(){if(stockFade!=null){ValueAnimator old=stockFade;stockFade=null;old.cancel();}}
    private void cancelFade(){generation++;if(fade!=null){ValueAnimator old=fade;fade=null;old.cancel();}}
    private void releaseClock(){
        owning=false;holdingClock=false;holdingUnlock=false;cancelStockFade();
        for(Map.Entry<View,Float> item:suppressed.entrySet())restore(item);
        suppressed.clear();written.clear();targets.clear();
        releaseNotificationSpace();
    }
    private void releaseNotificationSpace(){
        widgets.restore();
        notifications.restore();
    }
    private void removeFace(){if(clock!=null){clock.active(false);if(clock.getParent() instanceof ViewGroup)((ViewGroup)clock.getParent()).removeView(clock);clock=null;}}
    void hide(){
        widgets.clear();notifications.clear();
        cancelFade();ViewGroup previous=root;root=null;scope=time=date=null;notificationStack=null;
        if(previous!=null){if(previous.getViewTreeObserver().isAlive())previous.getViewTreeObserver().removeOnPreDrawListener(predraw);previous.removeOnAttachStateChangeListener(attachment);}
        removeFace();
        releaseClock();
    }
    // Native alpha is never changed or restored: it remains authoritative even
    // when the clock starts at zero and the platform animates it while masked.
    private void restore(Map.Entry<View,Float> item){Float last=written.get(item.getKey());if(last!=null&&item.getKey().getTransitionAlpha()==last)item.getKey().setTransitionAlpha(item.getValue());}
    private static boolean descendant(View child,View parent){
        if(parent==null)return false;
        for(View v=child;v!=null;){if(v==parent)return true;ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}
        return false;
    }
}
