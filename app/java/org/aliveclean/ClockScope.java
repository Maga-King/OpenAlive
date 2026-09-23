package org.aliveclean;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.reflect.Method;
import java.util.Map;

/** Optional exclusion of a separate launcher copy; never gates tracking on UI-state IDs. */
final class ClockScope {
    private final View root;
    private final Method container,material;
    private Method sceneMap,single,dual,clockTime,image,transitionAnchor,animationView,layoutEngine,followProgress,baseline;
    private boolean mapResolved,entryResolved,clockResolved,imageResolved,animationResolved,progressResolved;
    private Object clockContainer;
    private View launcher,launcherDual;
    ClockScope(View root){
        this.root=root;container=method(root.getClass(),"getClockContainer");
        material=method(root.getClass(),"getShellMaterialContainer");
    }
    void refresh(){
        launcher=null;launcherDual=null;clockContainer=null;if(container==null)return;
        try{
            Object host=container.invoke(root);if(host==null)return;
            if(!mapResolved){
                sceneMap=method(host.getClass(),"getSceneViewMap");
                transitionAnchor=method(host.getClass(),"getActiveLayoutTransitionSceneAnchor$KeyguardPersonalityClocks_release");
                layoutEngine=method(host.getClass(),"getLayoutTransitionEngine");
                baseline=method(host.getClass(),"getBaselineSingleClockView");
                mapResolved=true;
            }
            if(sceneMap==null)return;
            Object value=sceneMap.invoke(host);if(!(value instanceof Map))return;
            clockContainer=host;
            Map<?,?> entries=(Map<?,?>)value;View candidate=null,candidateDual=null;
            for(Map.Entry<?,?> entry:entries.entrySet())if("UNLOCK".equals(String.valueOf(entry.getKey()))){
                candidate=entryView(entry.getValue(),false);candidateDual=entryView(entry.getValue(),true);break;
            }
            if(candidate==null&&candidateDual==null)return;
            // Compare actual leaves, not the common mount: the native root mounts
            // distinct launcher/keyguard clocks as siblings, including during OneShot.
            for(Map.Entry<?,?> entry:entries.entrySet())if(!"UNLOCK".equals(String.valueOf(entry.getKey()))){
                if(candidate!=null&&entryView(entry.getValue(),false)==candidate)candidate=null;
                if(candidateDual!=null&&entryView(entry.getValue(),true)==candidateDual)candidateDual=null;
            }
            launcher=candidate;launcherDual=candidateDual;
        }catch(ReflectiveOperationException|RuntimeException ignored){} // Preserve the v28 path on unknown hosts.
    }
    private View entryView(Object entry,boolean useDual)throws ReflectiveOperationException{
        if(entry==null)return null;
        if(!entryResolved){single=method(entry.getClass(),"getSingleClockView");dual=method(entry.getClass(),"getDualClockView");entryResolved=true;}
        Method getter=useDual?dual:single;if(getter==null)return null;
        Object holder=getter.invoke(entry);if(holder==null)return null;
        if(useDual)return holder instanceof View?(View)holder:null;
        return timeView(holder);
    }
    private View timeView(Object holder)throws ReflectiveOperationException{
        if(holder==null)return null;
        // Digital SingleClockView extends Object. Only its ClockTimeView is a View.
        // Base clocks may expose a View directly; retain that compatibility path.
        if(!clockResolved){clockTime=method(holder.getClass(),"getClockTimeView");clockResolved=true;}
        Object view=clockTime==null?holder:clockTime.invoke(holder);
        return view instanceof View?(View)view:null;
    }
    boolean excludes(View view){return (launcher!=null&&within(view,launcher))||(launcherDual!=null&&within(view,launcherDual));}
    boolean hasSceneMap(){return clockContainer!=null&&sceneMap!=null;}
    View visibleTime(int uiState,int clockSize){
        if(!hasSceneMap())return null;
        try{
            // SystemUI renders its baseline instance even when the requested
            // size is SMALL. The other scene entries can be hidden mirrors.
            View view=baseline==null?null:timeView(baseline.invoke(clockContainer));
            if(visibleClock(view))return view;
            view=targetTime(uiState,clockSize);
            if(visibleClock(view))return view;
            Object value=sceneMap.invoke(clockContainer);if(!(value instanceof Map))return null;
            View candidate=null;
            for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()){
                if("UNLOCK".equals(String.valueOf(entry.getKey())))continue;
                view=entryView(entry.getValue(),false);
                if(!visibleClock(view))continue;
                if(candidate!=null&&candidate!=view)return null;
                candidate=view;
            }
            return candidate;
        }catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
    private boolean visibleClock(View view){return view!=null&&!excludes(view)&&visibleWithin(view,root);}
    View targetTime(int uiState,int clockSize){
        // SceneKt.resolveScene: panoramic AOD uses the selected keyguard layout;
        // workshop AOD has its own entry. Never choose the first visible copy.
        String scene=uiState==3?"AOD":uiState==5?(clockSize==0?"KEYGUARD_SMALL":clockSize==2?"KEYGUARD_IMMERSED":"KEYGUARD_BIG"):null;
        if(scene==null||clockContainer==null||sceneMap==null)return null;
        try{
            Object value=sceneMap.invoke(clockContainer);if(!(value instanceof Map))return null;
            for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet())if(scene.equals(String.valueOf(entry.getKey()))){
                View view=entryView(entry.getValue(),false);return view!=null&&!excludes(view)?view:null;
            }
        }catch(ReflectiveOperationException|RuntimeException ignored){}
        return null;
    }
    boolean allowsPluginFallback(){
        // DigitalClockImpl ignores the requested scene while a follow-hand
        // transition is active and measures its animationView instead.
        if(clockContainer==null||transitionAnchor==null)return true;
        try{
            Object anchor=transitionAnchor.invoke(clockContainer);if(anchor==null)return true;
            Object engine=layoutEngine==null?null:layoutEngine.invoke(clockContainer);
            if(engine!=null){
                if(!progressResolved){followProgress=method(engine.getClass(),"getFollowHandProgress");progressResolved=true;}
                if(followProgress!=null&&((Number)followProgress.invoke(engine)).floatValue()<=0f)return true;
            }
            if(!animationResolved){animationView=method(anchor.getClass(),"getAnimationView");animationResolved=true;}
            if(animationView==null)return launcher==null&&launcherDual==null;
            return !excludes(timeView(animationView.invoke(anchor)));
        }catch(ReflectiveOperationException|RuntimeException ignored){return launcher==null&&launcherDual==null;}
    }
    View artwork(){
        if(material==null)return null;
        try{
            Object value=material.invoke(root);if(!(value instanceof ViewGroup))return null;
            ViewGroup host=(ViewGroup)value;
            if(!imageResolved){image=method(host.getClass(),"getMaterialImageView$KeyguardPersonalityClocks_release");imageResolved=true;}
            Object bitmap=image==null?null:image.invoke(host);
            if(bitmap instanceof View&&visibleWithin((View)bitmap,host))return (View)bitmap;
            return coe(host,host);
        }catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
    private static View coe(View view,View host){
        if(view.getClass().getSimpleName().equals("COETextureView")&&visibleWithin(view,host))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=coe(group.getChildAt(i),host);if(found!=null)return found;}}
        return null;
    }
    private static Method method(Class<?> type,String name){try{return type.getMethod(name);}catch(NoSuchMethodException e){return null;}}
    private static boolean within(View view,View root){
        for(View current=view;current!=null;){if(current==root)return true;ViewParent p=current.getParent();current=p instanceof View?(View)p:null;}
        return false;
    }
    private static boolean visibleWithin(View view,View root){
        float alpha=1;
        for(View current=view;current!=null;){
            if(current.getVisibility()!=View.VISIBLE)return false;
            alpha*=current.getAlpha();if(!(alpha>.01f))return false;
            if(current==root)return true;
            ViewParent p=current.getParent();current=p instanceof View?(View)p:null;
        }
        return false;
    }
}
