package org.aliveclean;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import de.robv.android.xposed.*;
import java.lang.ref.WeakReference;

/** Measure the current clock in display coordinates; do not move the system clock. */
final class ColorOsClockTracker {
    private final ClassLoader loader;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WeakReference<Object> keyguard=new WeakReference<>(null),classic=new WeakReference<>(null);
    private View root,timeView;
    private ClockScope scope;
    private final java.util.ArrayList<View> digits=new java.util.ArrayList<>();
    private final Rect digitBounds=new Rect();
    private final ClockInkBounds ink=new ClockInkBounds();
    private final Rect inkBounds=new Rect();
    private final Rect targetLayout=new Rect(),targetInk=new Rect();
    private final Rect visualAnchor=new Rect();
    private final java.util.ArrayList<View> targetDigits=new java.util.ArrayList<>();
    private Object plugin;
    private boolean active,workshop,failed;
    private int aodUiState;
    private final Rect bounds=new Rect(),last=new Rect();
    private final Point displaySize=new Point();
    private final Rect transitionBounds=new Rect();
    private int sceneMode=-1,transitionStable;
    private boolean aodTransition;
    private boolean aodAnchorLocked;
    private int officialClockSize=-1;
    private String targetAodState="";
    private int lastDisplay=-1,lastWidth,lastHeight;
    private final ViewTreeObserver.OnPreDrawListener predraw=()->{measure();return true;};
    ColorOsClockTracker(ClassLoader loader){this.loader=loader;}
    ClassLoader loader(){return loader;}
    void install(){
        capture("com.oplus.systemui.keyguard.clockstyle.KeyguardStyleClockControllerImpl",true);
        capture("com.oplus.systemui.aod.aodclock.off.AodClockLayout",false);
    }
    private void capture(String name,boolean keyguardClock){
        Class<?> type=XposedHelpers.findClassIfExists(name,loader);if(type==null)return;
        XposedBridge.hookAllConstructors(type,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(keyguardClock)keyguard=new WeakReference<>(p.thisObject);else classic=new WeakReference<>(p.thisObject);
                main.post(()->{if(active)attach();});
            }
        });
    }
    void scene(Context context,boolean selected,int mode,String state){
        int previous=sceneMode;sceneMode=mode;
        active=selected&&mode==0;workshop=state.equals("WORKSHOP_AOD")||state.equals("PANORAMIC_AOD");
        aodUiState=state.equals("WORKSHOP_AOD")?3:5;
        // The clock tree is rebuilt after the scene callback.  During a
        // lock/home -> AOD transition its first few bounds can describe the
        // moving handoff view rather than the settled AOD clock.  Require two
        // equal official samples before publishing a changed anchor.
        aodTransition=active&&previous!=0;
        if(mode!=0||aodTransition||!state.equals(targetAodState))officialClockSize=-1;
        if(mode!=0||aodTransition)aodAnchorLocked=false;
        targetAodState=state;
        transitionBounds.setEmpty();transitionStable=0;
        if(active){attach();main.postDelayed(this::attach,80);main.postDelayed(this::attach,350);}
        else detach();
    }
    private void attach(){
        if(!active)return;
        try{
            View candidate=null;
            if(workshop){
                Object controller=keyguard.get();
                if(controller==null){
                    Class<?> dependency=XposedHelpers.findClass("com.android.systemui.DependencyEx",loader);
                    Object helper=XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(dependency,"sDependency"),"getDependency",XposedHelpers.findClass("com.android.keyguard.OplusKeyguardDependencyEx",loader));
                    controller=XposedHelpers.callMethod(helper,"getKeyguardStyleClockController");keyguard=new WeakReference<>(controller);
                }
                if(controller!=null){
                    plugin=XposedHelpers.getObjectField(controller,"clockPlugin");
                    // Base clocks expose root 1; other loaded clock families may expose root 0.
                    if(plugin!=null)for(int id:new int[]{1,0,7}){
                        Object v=XposedHelpers.callMethod(plugin,"getView",id);
                        if(v instanceof View&&((View)v).isAttachedToWindow()){candidate=(View)v;break;}
                    }
                }
            }else{
                Object host=classic.get();if(host!=null){Object v=XposedHelpers.getObjectField(host,"mAodViewFromApk");candidate=v instanceof View?(View)v:host instanceof View?(View)host:null;}
            }
            if(candidate==null)return;
            if(root!=candidate){detach();root=candidate;scope=new ClockScope(root);last.setEmpty();root.getViewTreeObserver().addOnPreDrawListener(predraw);}
            scope.refresh();
            timeView=findTimeView(root,0);
            digits.clear();findDigits(root,0);
            if(timeView!=null){
                java.util.ArrayList<View> activeDigits=new java.util.ArrayList<>();
                findDigits(timeView,0,activeDigits);
                if(!activeDigits.isEmpty()){digits.clear();digits.addAll(activeDigits);}
            }
            measure();
        }catch(Throwable error){failure(error);}
    }
    private View findTimeView(View view,int depth){
        if(view==null||depth>16)return null;
        if(scope!=null&&scope.excludes(view))return null;
        String name=view.getClass().getSimpleName();
        if((name.equals("ClockTimeView")||name.equals("TimeLayout")||name.equals("TextTimeTextView"))&&view.getVisibility()==View.VISIBLE)return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findTimeView(group.getChildAt(i),depth+1);if(found!=null)return found;}}
        return null;
    }
    private void findDigits(View view,int depth){
        if(view==null||depth>16)return;
        if(view.getClass().getSimpleName().equals("DigitalTimeView")){digits.add(view);return;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)findDigits(group.getChildAt(i),depth+1);}
    }
    private void findDigits(View view,int depth,java.util.List<View> target){
        if(view==null||depth>16||(scope!=null&&scope.excludes(view)))return;
        if(view.getClass().getSimpleName().equals("DigitalTimeView")){target.add(view);return;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)findDigits(group.getChildAt(i),depth+1,target);}
    }
    private boolean validBounds(){
        return !bounds.isEmpty()&&bounds.width()<=displaySize.x&&bounds.height()<=displaySize.y/2
                &&bounds.left>=0&&bounds.top>=0&&bounds.right<=displaySize.x&&bounds.bottom<=displaySize.y;
    }
    private void measure(){
        if(!active||root==null||!root.isAttachedToWindow())return;
        try{
            Display display=root.getDisplay();if(display==null)return;
            display.getRealSize(displaySize);
            bounds.setEmpty();String source="digits";
            scope.refresh();
            if(aodTransition&&workshop&&!scope.allowsPluginFallback())return;
            boolean officialClock=workshop&&readOfficialClockRect(bounds);
            if(officialClock){source="official-clock";if(centerOnTargetInk(bounds))source="official-visual-center";}
            if(aodTransition&&workshop&&!officialClock)return;
            View artwork=scope.artwork();
            if(!officialClock){
                if(artwork!=null&&artwork.getGlobalVisibleRect(bounds)&&validBounds())source="artwork";
                else bounds.setEmpty();
            }
            boolean preferArtwork=validBounds();
            // Digital ClockTimeView fills the display; only the visible glyphs locate the time.
            for(View digit:digits)if(!officialClock&&!preferArtwork&&!scope.excludes(digit)&&digit.isShown()&&digit.getAlpha()>0.01f&&digit.getGlobalVisibleRect(digitBounds)){
                // Text digit containers use extra height while the font animator runs.
                // Its release moves their center although the painted text does not move.
                // Image/irregular digits retain the existing container measurement.
                try{
                    Object text=XposedHelpers.callMethod(digit,"getVisibleTextView");
                    if(text instanceof android.widget.TextView&&((View)text).isShown()){
                        float offset=XposedHelpers.getFloatField(text,"fontMetricsDrawOffsetY");
                        if(ink.measure((android.widget.TextView)text,offset,inkBounds)){
                            // Keep the established horizontal alignment; remove vertical font padding.
                            digitBounds.top=inkBounds.top;digitBounds.bottom=inkBounds.bottom;
                        }
                    }
                }catch(Throwable ignored){} // Unknown clock versions retain the original valid bounds.
                bounds.union(digitBounds);
            }
            if(!validBounds()&&timeView!=null&&!scope.excludes(timeView)){bounds.setEmpty();timeView.getGlobalVisibleRect(bounds);source="time";}
            if(!validBounds()&&workshop&&plugin!=null&&scope.allowsPluginFallback()){
                // In-process plugin call, only as a fallback for clock styles without a time view.
                bounds.setEmpty();source="plugin";
                // Empty Bundle defaults to KEYGUARD_SMALL in this plugin. Request
                // the actual AOD layout and the controller's non-launcher size.
                Bundle query=new Bundle();query.putInt("uiState",aodUiState);
                Object controller=keyguard.get();
                if(controller==null)return;
                query.putInt("clockSize",clockSize(controller));
                Object value=XposedHelpers.callMethod(plugin,"getClockVisibleRect",query);
                if(value instanceof Rect)bounds.set((Rect)value);
                else if(value instanceof Bundle){Object rect=((Bundle)value).getParcelable("visibleRect");if(rect instanceof Rect)bounds.set((Rect)rect);}
            }else if(!validBounds()&&!workshop){bounds.setEmpty();root.getGlobalVisibleRect(bounds);source="root";}
            if(!validBounds())return;
            if(aodTransition){
                if(transitionBounds.isEmpty()||!transitionBounds.equals(bounds)){
                    transitionBounds.set(bounds);transitionStable=1;
                }else transitionStable++;
                if(transitionStable<2)return;
                aodTransition=false;
            }
            // One AOD session has one official clock anchor.  SystemUI may
            // rebuild the clock tree again after the mask animation and
            // briefly expose the launcher/hand-off bounds; accepting that
            // second rectangle is the source of the end-of-transition jump.
            if(aodAnchorLocked)return;
            aodAnchorLocked=true;
            int displayId=display.getDisplayId();
            if(last.equals(bounds)&&lastDisplay==displayId&&lastWidth==displaySize.x&&lastHeight==displaySize.y)return;
            boolean first=last.isEmpty();
            last.set(bounds);lastDisplay=displayId;lastWidth=displaySize.x;lastHeight=displaySize.y;
            Bundle b=new Bundle();b.putInt("version",1);b.putInt("display",displayId);b.putInt("width",displaySize.x);b.putInt("height",displaySize.y);
            b.putInt("left",bounds.left);b.putInt("top",bounds.top);b.putInt("right",bounds.right);b.putInt("bottom",bounds.bottom);
            ColorOsBridge.send(2,b);
            if(first){if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Clock anchor="+bounds+" source="+source+" display="+displayId);}
        }catch(Throwable error){failure(error);}
    }
    private boolean readOfficialClockRect(Rect out){
        try{
            if(plugin==null)return false;
            Bundle query=new Bundle();query.putInt("uiState",aodUiState);
            Object controller=keyguard.get();if(controller==null)return false;
            query.putInt("clockSize",clockSize(controller));
            Object value=XposedHelpers.callMethod(plugin,"getClockVisibleRect",query);
            if(value instanceof Rect)out.set((Rect)value);
            else if(value instanceof Bundle){Object rect=((Bundle)value).getParcelable("visibleRect");if(rect instanceof Rect)out.set((Rect)rect);}
            return validBounds();
        }catch(Throwable ignored){return false;}
    }
    private int clockSize(Object controller)throws Throwable{
        if(officialClockSize<0)officialClockSize=((Number)XposedHelpers.callMethod(controller,"pluginClockSize")).intValue();
        return officialClockSize;
    }
    private boolean centerOnTargetInk(Rect destination){
        // The accepted native rectangle unions DigitalTimeView layout boxes.
        // Preserve its position/source, but remove the same vertical font
        // padding that the original glyph tracker removed after settling.
        View target=scope.targetTime(aodUiState,officialClockSize);
        if(target==null)return false;
        targetDigits.clear();findDigits(target,0,targetDigits);
        targetLayout.setEmpty();targetInk.setEmpty();
        try{
            for(View digit:targetDigits){
                if(digit.getVisibility()!=View.VISIBLE)continue;
                if(digit.getWidth()<=0||digit.getHeight()<=0)return false;
                Object text=XposedHelpers.callMethod(digit,"getVisibleTextView");
                if(!(text instanceof android.widget.TextView))return false;
                float offset=XposedHelpers.getFloatField(text,"fontMetricsDrawOffsetY");
                if(!ink.measureLayout((android.widget.TextView)text,offset,digit,inkBounds))return false;
                digitBounds.set(digit.getLeft(),digit.getTop(),digit.getRight(),digit.getBottom());
                targetLayout.union(digitBounds);
                inkBounds.offset(digit.getLeft(),digit.getTop());targetInk.union(inkBounds);
            }
            visualAnchor.set(destination);
            if(!ClockVisualAnchor.vertical(visualAnchor,targetLayout,targetInk)||visualAnchor.top<0||visualAnchor.bottom>displaySize.y||visualAnchor.height()>displaySize.y/2)return false;
            destination.set(visualAnchor);return true;
        }catch(Throwable ignored){return false;} // Image/unknown clocks keep their native anchor.
    }
    private void detach(){
        if(root!=null&&root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnPreDrawListener(predraw);
        root=null;scope=null;timeView=null;digits.clear();
    }
    private void failure(Throwable error){if(!failed){failed=true;XposedBridge.log("AliveClean: clock region unavailable: "+error);android.util.Log.w("AliveClean","Clock region unavailable",error);}}
}
