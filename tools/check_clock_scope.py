"""Offline scope regression with the APK's wrapper contract; no ADB or UI actions.

These minimal View doubles cover ownership and reflection only. They do not
validate Android geometry, animation timing or on-device visual behavior.
"""
from pathlib import Path
import hashlib,json,subprocess,os

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/clock-scope-check'
JAVA=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17' if os.name=='nt' else '/usr/lib/jvm/java-17-openjdk-amd64'))/'bin'
EXE='.exe' if os.name=='nt' else ''
STUBS={
'android/content/Context.java':'package android.content; public class Context {}',
'android/view/ViewParent.java':'package android.view; public interface ViewParent {}',
'android/view/View.java':'''package android.view;
import android.content.Context;
public class View implements ViewParent {
 public static final int VISIBLE=0,INVISIBLE=4,GONE=8;
 private final Context context; ViewParent parent; private int visibility;
 private float alpha=1,x,y;
 public View(Context c){context=c;} public Context getContext(){return context;}
 public ViewParent getParent(){return parent;}
 public int getVisibility(){return visibility;} public void setVisibility(int v){visibility=v;}
 public float getAlpha(){return alpha;} public void setAlpha(float v){alpha=v;}
 public float getTranslationX(){return x;} public void setTranslationX(float v){x=v;}
 public float getTranslationY(){return y;} public void setTranslationY(float v){y=v;}
}''',
'android/view/ViewGroup.java':'''package android.view;
public class ViewGroup extends View {
 private final java.util.ArrayList<View> children=new java.util.ArrayList<>();
 public ViewGroup(android.content.Context c){super(c);}
 public void addView(View v){children.add(v);v.parent=this;}
 public int getChildCount(){return children.size();} public View getChildAt(int i){return children.get(i);}
}''',
'android/widget/FrameLayout.java':'''package android.widget;
public class FrameLayout extends android.view.ViewGroup {public FrameLayout(android.content.Context c){super(c);}}''',
'android/widget/ImageView.java':'''package android.widget;
public class ImageView extends android.view.View {public ImageView(android.content.Context c){super(c);}}''',
'org/aliveclean/ClockScopeHostTest.java':'''package org.aliveclean;
public class ClockScopeHostTest {public static void main(String[] args){
 ClockScopeTest.run(new android.content.Context());System.out.println("CLOCK_SCOPE_OK checks="+ClockScopeTest.checks);
}}'''
}

# Check the fixture's crucial distinction against the local APK disassembly.
SMALI=ROOT/'research/clock-anchor-smali/com/oplus/keyguard/clock/digital/ui/view/SingleClockView.smali'
if SMALI.exists():
    text=SMALI.read_text(encoding='utf8')
    assert '\n.super Ljava/lang/Object;' in text
    assert '.method public final getClockTimeView()Lcom/oplus/keyguard/clock/digital/ui/view/ClockTimeView;' in text

sources=[]
for name,text in STUBS.items():
    p=OUT/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8');sources.append(p)
sources += [ROOT/'app/java/org/aliveclean/ClockScope.java',ROOT/'tests/android/org/aliveclean/ClockScopeTest.java']
(OUT/'classes').mkdir(parents=True,exist_ok=True)
subprocess.run([str(JAVA/('javac'+EXE)),'-encoding','UTF-8','-d',str(OUT/'classes'),*map(str,sources)],check=True)
result=subprocess.run([str(JAVA/('java'+EXE)),'-cp',str(OUT/'classes'),'org.aliveclean.ClockScopeHostTest'],capture_output=True,text=True)
print(result.stdout+result.stderr)
report={'passed':result.returncode==0,'output':result.stdout+result.stderr,
        'production_sha256':hashlib.sha256(sources[-2].read_bytes()).hexdigest(),
        'wrapper_evidence_sha256':hashlib.sha256(SMALI.read_bytes()).hexdigest() if SMALI.exists() else None}
(OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
result.check_returncode()
