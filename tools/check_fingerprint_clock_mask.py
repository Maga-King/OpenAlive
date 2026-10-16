"""Host checks for fingerprint clock-leaf masking; Android hook timing needs device checks."""
from pathlib import Path
import os, subprocess
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/fingerprint-clock-mask-host'
JAVA=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17' if os.name=='nt' else '/usr/lib/jvm/java-17-openjdk-amd64'))/'bin'
EXE='.exe' if os.name=='nt' else ''
STUBS={
'android/view/ViewParent.java': 'package android.view; public interface ViewParent {}',
'android/view/ViewTreeObserver.java': '''package android.view;
import java.util.*;
public class ViewTreeObserver {
 public interface OnPreDrawListener{boolean onPreDraw();}
 public final List<OnPreDrawListener> listeners=new ArrayList<>();
 public void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}
 public void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}
 public boolean isAlive(){return true;}
 public void draw(){for(OnPreDrawListener l:new ArrayList<>(listeners))l.onPreDraw();}
}''',
'android/view/View.java': '''package android.view;
import java.util.*;
import de.robv.android.xposed.XposedHelpers;
public class View implements ViewParent {
 public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public ViewParent parent;private float alpha=1;private Object tag;public boolean attached=true;
 public final List<OnAttachStateChangeListener> attachments=new ArrayList<>();
 private final ViewTreeObserver observer=new ViewTreeObserver();
 public void setAlpha(float a){alpha=XposedHelpers.beforeAlpha(this,a);}public float getAlpha(){return alpha;}
 public Object getTag(){return tag;}public void setTag(Object v){tag=v;}
 public boolean isAttachedToWindow(){return attached;}public ViewParent getParent(){return parent;}
 public ViewTreeObserver getViewTreeObserver(){return observer;}
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attachments.add(l);}
 public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){attachments.remove(l);}
 public void detach(){attached=false;for(OnAttachStateChangeListener l:new ArrayList<>(attachments))l.onViewDetachedFromWindow(this);}
}''',
'android/view/ViewGroup.java': '''package android.view;
import java.util.*;
public class ViewGroup extends View{
 private final List<View> children=new ArrayList<>();
 public void addView(View v){children.add(v);v.parent=this;}public void removeView(View v){children.remove(v);v.parent=null;}
 public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}
}''',
'de/robv/android/xposed/XC_MethodHook.java': '''package de.robv.android.xposed;
public class XC_MethodHook{
 protected void beforeHookedMethod(MethodHookParam p){}
 public static class MethodHookParam{public Object thisObject;public Object[] args;}
 public class Unhook{public void unhook(){if(XposedHelpers.hook==XC_MethodHook.this)XposedHelpers.hook=null;}}
}''',
'de/robv/android/xposed/XposedHelpers.java': '''package de.robv.android.xposed;
public class XposedHelpers{
 public static XC_MethodHook hook;
 public static XC_MethodHook.Unhook findAndHookMethod(Class<?> t,String n,Object... args){hook=(XC_MethodHook)args[args.length-1];return hook.new Unhook();}
 public static float beforeAlpha(Object view,float value){if(hook==null)return value;XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=view;p.args=new Object[]{value};hook.beforeHookedMethod(p);return (Float)p.args[0];}
}''',
'org/aliveclean/FingerprintClockMaskTest.java': '''package org.aliveclean;
import android.view.*;
import de.robv.android.xposed.XposedHelpers;
public class FingerprintClockMaskTest{
 static int checks;
 public static class ClockTimeView extends View{}
 public static class DateMessageView extends View{}
 public static class TextTimeTextView extends View{}
 public static class TextDateInformationView extends ViewGroup{final View date=new View();public TextDateInformationView(){addView(date);}public View getLocalDate(){return date;}}
 static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void equal(float a,float b,String s){check(Math.abs(a-b)<0.0001f,s+": "+a+" != "+b);}
 public static void main(String[] args){
  FingerprintClockMask mask=new FingerprintClockMask();ViewGroup root=new ViewGroup(),scope=new ViewGroup();root.addView(scope);
  ClockTimeView time=new ClockTimeView();DateMessageView date=new DateMessageView();View widget=new View();scope.addView(time);scope.addView(date);scope.addView(widget);
  time.setAlpha(.8f);date.setAlpha(.6f);mask.bind(root,scope);
  equal(time.getAlpha(),0,"time hidden immediately");equal(date.getAlpha(),0,"date hidden immediately");equal(widget.getAlpha(),1,"widget intact");equal(scope.getAlpha(),1,"root intact");
  check(root.getViewTreeObserver().listeners.size()==1,"one active predraw listener");mask.bind(root,scope);check(root.getViewTreeObserver().listeners.size()==1,"reconcile reuses listener");
  time.setAlpha(.4f);date.setAlpha(0);equal(time.getAlpha(),0,"native update remains masked");
  root.getViewTreeObserver().draw();mask.clear();equal(time.getAlpha(),.4f,"restores latest native value");equal(date.getAlpha(),0,"preserves native zero written while masked");
  check(root.getViewTreeObserver().listeners.isEmpty()&&root.attachments.isEmpty()&&XposedHelpers.hook==null,"exit removes listeners and alpha hook");
  time.setAlpha(.7f);equal(time.getAlpha(),.7f,"native writes pass through after exit");mask.clear();equal(time.getAlpha(),.7f,"repeat exit harmless");
  mask.bind(root,scope);scope.removeView(time);ClockTimeView next=new ClockTimeView();next.setAlpha(.9f);scope.addView(next);root.getViewTreeObserver().draw();
  equal(time.getAlpha(),.7f,"replaced leaf restored");equal(next.getAlpha(),0,"new leaf masked on predraw");
  root.detach();equal(next.getAlpha(),.9f,"detach restores leaf");check(root.getViewTreeObserver().listeners.isEmpty()&&XposedHelpers.hook==null,"detach removes active hooks");
  root=new ViewGroup();scope=new ViewGroup();root.addView(scope);ViewGroup nativeContent=new ViewGroup();nativeContent.setTag("org.aliveclean.native_clock_content");View nativeFace=new View();nativeContent.addView(nativeFace);scope.addView(nativeContent);scope.addView(widget);
  mask.bind(root,scope);equal(nativeContent.getAlpha(),0,"custom clock content masked");equal(nativeFace.getAlpha(),1,"custom descendants not double masked");equal(widget.getAlpha(),1,"custom sibling widget retained");mask.clear();equal(nativeContent.getAlpha(),1,"custom content restored");
  scope=new ViewGroup();TextTimeTextView textTime=new TextTimeTextView();TextDateInformationView textDate=new TextDateInformationView();scope.addView(textTime);scope.addView(textDate);mask.bind(root,scope);
  equal(textTime.getAlpha(),0,"text time masked");equal(textDate.date.getAlpha(),0,"text local date masked");equal(textDate.getAlpha(),1,"date wrapper retained");mask.clear();equal(textDate.date.getAlpha(),1,"text date restored");
  check(XposedHelpers.hook==null,"final hook removed");System.out.println("Fingerprint clock mask: "+checks+" host checks passed");
 }
}'''
}
sources=[]
for name,source in STUBS.items():
 p=OUT/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf8');sources.append(p)
classes=OUT/'classes';classes.mkdir(parents=True,exist_ok=True)
sources.append(ROOT/'app/java/org/aliveclean/FingerprintClockMask.java')
subprocess.run([str(JAVA/('javac'+EXE)),'-encoding','UTF-8','-d',str(classes),*map(str,sources)],check=True)
subprocess.run([str(JAVA/('java'+EXE)),'-cp',str(classes),'org.aliveclean.FingerprintClockMaskTest'],check=True)
