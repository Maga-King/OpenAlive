package org.aliveclean;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.LinkedHashMap;
import java.util.Map;

/** v28 tracking stays available even when rendered UI state is absent/stale. */
public final class ClockScopeTest {
    static int checks;
    // Digital SingleClockView extends Object in the supplied APK, not View.
    public static final class SingleClock {
        final View time;
        SingleClock(View time){this.time=time;}
        public View getClockTimeView(){return time;}
        public View getClockTimeViewMount(){return (View)time.getParent();}
    }
    public static final class Entry {
        final View view,dual;final SingleClock single;
        Entry(View view){this.view=view;dual=null;single=new SingleClock(view);}
        Entry(View view,boolean isDual){this.view=view;dual=view;single=null;}
        public SingleClock getSingleClockView(){return single;}
        public View getDualClockView(){return dual;}
    }
    public static final class Container extends FrameLayout {
        final Map<String,Entry> entries=new LinkedHashMap<>();
        Anchor anchor;
        final Engine engine=new Engine();
        Container(Context c){super(c);}
        public Map<String,Entry> getSceneViewMap(){return entries;}
        public Anchor getActiveLayoutTransitionSceneAnchor$KeyguardPersonalityClocks_release(){return anchor;}
        public Engine getLayoutTransitionEngine(){return engine;}
        View add(String key){FrameLayout view=new FrameLayout(getContext());addView(view);entries.put(key,new Entry(view));return view;}
    }
    public static final class Engine {
        float progress;
        public float getFollowHandProgress(){return progress;}
    }
    public static final class Anchor {
        final SingleClock view;Anchor(SingleClock view){this.view=view;}
        public SingleClock getAnimationView(){return view;}
    }
    public static final class Root extends FrameLayout {
        final Container container;
        Root(Context c){super(c);container=new Container(c);addView(container);}
        public Object getRenderedViewState(){throw new AssertionError("Tracking consulted rendered UI state");}
        public Container getClockContainer(){return container;}
    }
    public static final class BaseRoot extends FrameLayout {
        final FrameLayout container;
        BaseRoot(Context c){super(c);container=new FrameLayout(c);addView(container);}
        public Object getRenderedViewState(){return null;}
        public FrameLayout getClockContainer(){return container;}
    }
    public static final class Material extends FrameLayout {
        final ImageView photo;final View widget,animation;
        Material(Context c){super(c);photo=new ImageView(c);widget=new ImageView(c);animation=new COETextureView(c);addView(photo);addView(widget);addView(animation);}
        public ImageView getMaterialImageView$KeyguardPersonalityClocks_release(){return photo;}
    }
    public static final class COETextureView extends View {COETextureView(Context c){super(c);}}
    public static final class TextRoot extends FrameLayout {
        final Material material;final View time;
        TextRoot(Context c){super(c);material=new Material(c);time=new View(c);addView(material);addView(time);}
        public Material getShellMaterialContainer(){return material;}
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    static void run(Context c){
        Root root=new Root(c);ClockScope scope=new ClockScope(root);
        View desktop=root.container.add("UNLOCK");View desktopDigit=new View(c);((FrameLayout)desktop).addView(desktopDigit);
        for(String key:new String[]{"KEYGUARD_SMALL","KEYGUARD_BIG","KEYGUARD_IMMERSED","AOD"})root.container.add(key);
        scope.refresh();check(scope.excludes(desktopDigit),"Known desktop descendant included");
        check(!scope.excludes(root.container),"Shared mount excluded with desktop copy");
        root.container.anchor=new Anchor(root.container.entries.get("UNLOCK").single);
        check(scope.allowsPluginFallback(),"Inactive follow-hand incorrectly blocked native AOD fallback");
        root.container.engine.progress=.4f;
        check(!scope.allowsPluginFallback(),"Plugin fallback reintroduced launcher animation view");
        root.container.anchor=new Anchor(root.container.entries.get("KEYGUARD_BIG").single);
        check(scope.allowsPluginFallback(),"Keyguard animation fallback rejected");
        root.container.anchor=null;check(scope.allowsPluginFallback(),"Normal clock fallback rejected");
        for(String key:new String[]{"KEYGUARD_SMALL","KEYGUARD_BIG","KEYGUARD_IMMERSED","AOD"}){
            View clock=root.container.entries.get(key).view;check(!scope.excludes(clock),"AOD clock blocked");
            clock.setTranslationX(17);clock.setTranslationY(29);scope.refresh();check(!scope.excludes(clock),"Clock movement blocked");
            root.setAlpha(0);scope.refresh();check(!scope.excludes(clock),"Global fade blocked v28 geometry path");root.setAlpha(1);
        }
        // A shared launcher/AOD view is not a separate desktop copy.
        root.container.entries.put("KEYGUARD_BIG",new Entry(desktop));scope.refresh();
        check(!scope.excludes(desktopDigit),"Shared clock accidentally excluded");
        root.container.entries.clear();scope.refresh();check(!scope.excludes(desktopDigit),"Stale exclusion retained after reload");
        FrameLayout dual=new FrameLayout(c);View dualDigit=new View(c);dual.addView(dualDigit);root.container.addView(dual);
        root.container.entries.put("UNLOCK",new Entry(dual,true));scope.refresh();
        check(scope.excludes(dualDigit),"Dual desktop clock included");
        View replacement=root.container.add("UNLOCK");scope.refresh();
        check(!scope.excludes(dualDigit)&&scope.excludes(replacement),"Desktop replacement retained stale exclusion");
        BaseRoot base=new BaseRoot(c);ClockScope basic=new ClockScope(base);basic.refresh();
        check(!basic.excludes(base.container),"Unknown/base clock lost original fallback");
        View legacy=new FrameLayout(c);ClockScope old=new ClockScope(legacy);old.refresh();
        check(!old.excludes(legacy)&&old.artwork()==null,"Legacy clock rejected");
        TextRoot text=new TextRoot(c);ClockScope magazine=new ClockScope(text);magazine.refresh();
        check(magazine.artwork()==text.material.photo,"Text style did not prefer artwork over time/widget");
        text.material.photo.setVisibility(View.GONE);
        check(magazine.artwork()==text.material.animation,"Animated artwork not tracked");
        text.material.animation.setAlpha(0);
        check(magazine.artwork()==null,"Widget used as artwork fallback");
        check(!magazine.excludes(text.time),"Clock fallback unavailable when artwork absent");
        text.material.photo.setVisibility(View.VISIBLE);text.material.photo.setTranslationY(73);
        check(magazine.artwork()==text.material.photo&&magazine.artwork().getTranslationY()==73,"Artwork position frozen");
        check(text.material.widget.getAlpha()==1&&text.material.widget.getTranslationY()==0,"Tracking mutated widget");
    }
}
