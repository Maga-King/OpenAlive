package org.aliveclean;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.*;

/** A short fingerprint attempt masks clock leaves, independently of native transition alpha. */
final class FingerprintClockMask {
    private View root,scope;
    private XC_MethodHook.Unhook alphaHook;
    private boolean writing;
    private final IdentityHashMap<View,Float> saved=new IdentityHashMap<>();
    private final Set<View> targets=Collections.newSetFromMap(new IdentityHashMap<View,Boolean>());
    private final ViewTreeObserver.OnPreDrawListener predraw=()->{apply();return true;};
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        public void onViewAttachedToWindow(View view){}
        public void onViewDetachedFromWindow(View view){clear();}
    };
    void bind(View root,View scope){
        if(root==null||scope==null||!root.isAttachedToWindow()){clear();return;}
        if(this.root!=root){
            clear();this.root=root;
            alphaHook=XposedHelpers.findAndHookMethod(View.class,"setAlpha",float.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    if(!writing&&saved.containsKey(param.thisObject)){
                        saved.put((View)param.thisObject,(Float)param.args[0]);param.args[0]=0f;
                    }
                }
            });
            root.getViewTreeObserver().addOnPreDrawListener(predraw);
            root.addOnAttachStateChangeListener(attachment);
        }
        this.scope=scope;apply();
    }
    private void discover(View view){
        if("org.aliveclean.native_clock_content".equals(view.getTag())){targets.add(view);return;}
        String type=view.getClass().getSimpleName();
        if(type.equals("ClockTimeView")||type.equals("DateMessageView")||type.equals("TextTimeTextView")){
            targets.add(view);return;
        }
        if(type.equals("TextDateInformationView")){
            try{
                Method method=view.getClass().getMethod("getLocalDate");Object date=method.invoke(view);
                if(date instanceof View&&descendant((View)date,view))targets.add((View)date);
            }catch(ReflectiveOperationException ignored){} // Unknown layouts retain their native contents.
            return;
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)discover(group.getChildAt(i));}
    }
    private void apply(){
        targets.clear();if(scope!=null)discover(scope);
        Iterator<Map.Entry<View,Float>> it=saved.entrySet().iterator();
        while(it.hasNext()){Map.Entry<View,Float> item=it.next();if(!targets.contains(item.getKey())){restore(item);it.remove();}}
        for(View view:targets){
            float current=view.getAlpha();
            if(!saved.containsKey(view)||current!=0)saved.put(view,current);
            if(current!=0)write(view,0);
        }
    }
    void clear(){
        View previous=root;root=scope=null;
        if(alphaHook!=null){alphaHook.unhook();alphaHook=null;}
        if(previous!=null){
            if(previous.getViewTreeObserver().isAlive())previous.getViewTreeObserver().removeOnPreDrawListener(predraw);
            previous.removeOnAttachStateChangeListener(attachment);
        }
        for(Map.Entry<View,Float> item:saved.entrySet())restore(item);
        saved.clear();targets.clear();
    }
    private void restore(Map.Entry<View,Float> item){
        // A newer native write wins, including one made after the last pre-draw.
        if(item.getKey().getAlpha()==0)write(item.getKey(),item.getValue());
    }
    private void write(View view,float alpha){writing=true;try{view.setAlpha(alpha);}finally{writing=false;}}
    private static boolean descendant(View child,View parent){
        for(View view=child;view!=null;){if(view==parent)return true;ViewParent next=view.getParent();view=next instanceof View?(View)next:null;}
        return false;
    }
}
