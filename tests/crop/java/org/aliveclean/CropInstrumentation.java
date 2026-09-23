package org.aliveclean;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Production crop host in an isolated test package; no wallpaper/user files. */
public final class CropInstrumentation extends Instrumentation {
    private FrameCropActivity activity;
    public static final class ClockTimeView extends View {
        int alphaWrites;
        ClockTimeView(Context c){super(c);}
        @Override public void setAlpha(float value){alphaWrites++;super.setAlpha(value);}
    }
    public static final class DateMessageView extends View {DateMessageView(Context c){super(c);}}
    public static final class TextTimeTextView extends View {TextTimeTextView(Context c){super(c);}}
    public static final class TextDateInformationView extends FrameLayout {
        final View date,extra;
        TextDateInformationView(Context c){super(c);date=new TextView(c);extra=new View(c);addView(date);addView(extra);}
        public View getLocalDate(){return date;}
    }
    public static final class ShellMaterialContainer extends FrameLayout {
        final ImageView art;
        ShellMaterialContainer(Context c){super(c);art=new ImageView(c);addView(art);}
        public ImageView getMaterialImageView$KeyguardPersonalityClocks_release(){return art;}
    }
    public static final class COETextureView extends View {COETextureView(Context c){super(c);}}
    private int checks;
    private boolean onlyClockHost;
    private final ArrayList<String> observations=new ArrayList<>();
    interface Step {void run() throws Exception;}
    private void main(Step step){
        Throwable[] error={null};runOnMainSync(()->{try{step.run();}catch(Throwable t){error[0]=t;}});
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private Object field(String name)throws Exception{Field f=FrameCropActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private Object invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=FrameCropActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    private FrameCrop current()throws Exception{return (FrameCrop)invoke("current",new Class<?>[0]);}
    private void near(float a,float b){if(Math.abs(a-b)>.0001f)throw new AssertionError(a+" != "+b);checks++;}
    private void same(FrameCrop a,FrameCrop b){near(a.x,b.x);near(a.y,b.y);near(a.size,b.size);near(a.angle,b.angle);}
    private SharedPreferences prefs(){return getTargetContext().getSharedPreferences(SceneOptions.DRAFT,0);}
    private File fixture(String name,int w,int h)throws Exception{
        File file=new File(getTargetContext().getFilesDir(),name);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.drawColor(Color.GREEN);Paint p=new Paint();p.setColor(Color.RED);canvas.drawRect(0,0,w/2f,h/2f,p);
        try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}return file;
    }
    private void open(String name,boolean fresh)throws Exception{
        activity=(FrameCropActivity)startActivitySync(new Intent(getTargetContext(),FrameCropActivity.class).putExtra("photo",name).putExtra("new_photo",fresh).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean[] ready={false};long deadline=SystemClock.uptimeMillis()+10000;
        while(!ready[0]&&SystemClock.uptimeMillis()<deadline){
            main(()->{ImageView p=(ImageView)field("photo");ready[0]=p!=null&&p.getWidth()>0&&p.getDrawable()!=null&&!activity.isFinishing();});
            if(!ready[0])SystemClock.sleep(50);
        }
        if(!ready[0]){
            String[] detail={""};main(()->{ImageView p=(ImageView)field("photo");detail[0]=" finishing="+activity.isFinishing()+" destroyed="+activity.isDestroyed()+" photo="+(p==null?"null":p.getWidth()+"x"+p.getHeight()+" drawable="+(p.getDrawable()!=null)+" attached="+p.isAttachedToWindow()+" window="+p.getWindowVisibility());});
            throw new AssertionError("Production crop did not open"+detail[0]);
        }
        waitForIdleSync();
        main(()->{
            ImageView p=(ImageView)field("photo");
            if(!p.getClass().getName().equals("com.flyme.systemuieditor.widget.view.photoview.PhotoView"))throw new AssertionError("Wrong control");
            if(p.getWidth()!=p.getHeight())throw new AssertionError("Not square");
            OfficialUi ui=(OfficialUi)field("ui");int id=getTargetContext().getResources().getIdentifier("host_sentinel","string",getTargetContext().getPackageName());
            if(!"host-resources-intact".equals(getTargetContext().getString(id)))throw new AssertionError("Resource namespace leaked");
            View done=activity.findViewById(ui.id("id","btn_apply"));int[] location=new int[2];done.getLocationOnScreen(location);
            WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();
            if(insets!=null&&location[1]<insets.getSystemWindowInsetTop())throw new AssertionError("Toolbar overlaps status bar");checks+=4;
        });
    }
    private void close(boolean save){main(()->{
        OfficialUi ui=(OfficialUi)field("ui");activity.findViewById(ui.id("id",save?"btn_apply":"btn_cancel")).performClick();
    });waitForIdleSync();long deadline=SystemClock.uptimeMillis()+5000;boolean[] destroyed={false};
        while(!destroyed[0]&&SystemClock.uptimeMillis()<deadline){main(()->destroyed[0]=activity.isDestroyed());if(!destroyed[0])SystemClock.sleep(50);}
        if(!destroyed[0])throw new AssertionError("Crop Activity did not finish");
    }
    private void clockContent()throws Exception{
        main(()->{
            OfficialClockFace face=new OfficialClockFace(getTargetContext(),true);
            TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            try{
                Calendar time=Calendar.getInstance();time.clear();time.set(2026,Calendar.SEPTEMBER,21,23,59);
                face.refresh(time.getTimeInMillis());String before=face.getContentDescription().toString();
                time.add(Calendar.MINUTE,1);face.refresh(time.getTimeInMillis());String after=face.getContentDescription().toString();
                if(before.equals(after)||!before.contains(":59")||!after.contains(":00"))throw new AssertionError("Clock failed midnight/minute rollover");checks++;
                int width=1440;
                for(int style=0;style<6;style++){
                    face.layoutStyle(style);face.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                    if(face.getMeasuredHeight()<1)throw new AssertionError("Empty official clock");
                    face.layout(0,0,width,face.getMeasuredHeight());Bitmap canvas=Bitmap.createBitmap(width,face.getHeight(),Bitmap.Config.ARGB_8888);face.draw(new Canvas(canvas));canvas.recycle();checks++;
                }
                face.active(true);face.active(false);face.active(true);face.active(false);
                int[] events={0};ClockUpdates ticks=new ClockUpdates(getTargetContext(),false,t->{events[0]++;});
                ticks.start();ticks.refresh(time.getTimeInMillis());ticks.stop();ticks.refresh(time.getTimeInMillis());
                if(events[0]!=2)throw new AssertionError("Clock tick lifecycle leaked");checks++;
                Field handler=ClockUpdates.class.getDeclaredField("main"),callback=ClockUpdates.class.getDeclaredField("minute");handler.setAccessible(true);callback.setAccessible(true);
                ticks.start();if(((Handler)handler.get(ticks)).hasCallbacks((Runnable)callback.get(ticks)))throw new AssertionError("Runtime clock scheduled a preview timer");ticks.stop();checks++;
                observations.add("Original clock: six layouts, minute/midnight rollover, lifecycle and no runtime polling timer passed");
            }finally{face.close();TimeZone.setDefault(previous);}
        });
    }
    private void aodClockHost()throws Exception{
        open("accepted.png",false);
        main(()->{
            FrameLayout decor=(FrameLayout)activity.getWindow().getDecorView();
            FrameLayout root=new FrameLayout(activity);decor.addView(root,new FrameLayout.LayoutParams(-1,-1));
            ClockTimeView time=new ClockTimeView(activity);View date=new View(activity),notifications=new View(activity);
            root.addView(time);root.addView(date);root.addView(notifications);time.setAlpha(.8f);date.setAlpha(.6f);
            AodClockHost host=new AodClockHost();
            try{
                host.show(root,time,date);if(!host.shown())throw new AssertionError("AOD clock not attached");
                near(time.getAlpha(),.8f);near(date.getAlpha(),.6f);near(notifications.getAlpha(),1);
                near(time.getTransitionAlpha(),0);near(date.getTransitionAlpha(),0);
                if(time.alphaWrites!=1)throw new AssertionError("AOD masking entered vendor alpha state machine");checks++;
                AodClockView clock=(AodClockView)root.getChildAt(3);
                root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,2400);
                LinearLayout content=(LinearLayout)clock.getChildAt(0);
                for(int i=0;i<content.getChildCount();i++){
                    TextView text=(TextView)content.getChildAt(i);if(text.getVisibility()==View.GONE)continue;
                    Paint.FontMetricsInt metrics=text.getPaint().getFontMetricsInt();
                    if(text.getHeight()<metrics.descent-metrics.ascent)throw new AssertionError("First AOD layout clipped text "+i+": height="+text.getHeight()+" expected="+(metrics.descent-metrics.ascent));checks++;
                }
                if(host.notificationTop()<=content.getY()+content.getHeight())throw new AssertionError("Notifications overlap AOD content");checks++;
                Calendar reference=Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));reference.clear();reference.set(2026,Calendar.SEPTEMBER,8,12,0);
                if(!AodCalendar.lunar(reference.getTimeInMillis(),"Asia/Shanghai").equals("丙午年七月廿七"))throw new AssertionError("Lunar reference mismatch");checks++;
                reference.set(2026,Calendar.FEBRUARY,17,12,0);
                if(!AodCalendar.lunar(reference.getTimeInMillis(),"Asia/Shanghai").equals("丙午年正月初一"))throw new AssertionError("Lunar new year mismatch");checks++;
                Calendar now=Calendar.getInstance();now.clear();now.set(2026,Calendar.SEPTEMBER,21,23,59);
                host.tick(now.getTimeInMillis());String before=clock.getContentDescription().toString();
                now.add(Calendar.MINUTE,1);host.tick(now.getTimeInMillis());String after=clock.getContentDescription().toString();
                if(before.equals(after)||!before.contains(":59")||!after.contains(":00"))throw new AssertionError("Independent AOD clock froze at midnight");checks++;
                int[] textChanges={0};android.text.TextWatcher watcher=new android.text.TextWatcher(){
                    public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                    public void onTextChanged(CharSequence s,int start,int before,int count){textChanges[0]++;}
                    public void afterTextChanged(android.text.Editable e){}
                };
                for(int i=0;i<content.getChildCount();i++)((TextView)content.getChildAt(i)).addTextChangedListener(watcher);
                clock.refresh(now.getTimeInMillis()+500);clock.refresh(now.getTimeInMillis()+1500);
                if(textChanges[0]!=0)throw new AssertionError("Unchanged minute rebuilt clock text");checks++;
                TimeZone previousZone=TimeZone.getDefault();
                try{
                    TimeZone.setDefault(TimeZone.getTimeZone(previousZone.getRawOffset()==0?"GMT+08:00":"UTC"));
                    clock.refresh(now.getTimeInMillis());
                    if(after.equals(clock.getContentDescription().toString()))throw new AssertionError("Timezone change reused stale minute");checks++;
                }finally{TimeZone.setDefault(previousZone);clock.refresh(now.getTimeInMillis());}
                if(!after.equals(clock.getContentDescription().toString()))throw new AssertionError("Timezone restoration lost date");checks++;
                float y=clock.getChildAt(0).getTranslationY();
                host.tick(now.getTimeInMillis()+60000);near(clock.getChildAt(0).getTranslationY(),y);near(clock.getScaleX(),1);
                Field update=AodClockView.class.getDeclaredField("updates");update.setAccessible(true);ClockUpdates ticks=(ClockUpdates)update.get(clock);
                Field handler=ClockUpdates.class.getDeclaredField("main"),minute=ClockUpdates.class.getDeclaredField("minute"),active=ClockUpdates.class.getDeclaredField("active");
                handler.setAccessible(true);minute.setAccessible(true);active.setAccessible(true);
                if(((Handler)handler.get(ticks)).hasCallbacks((Runnable)minute.get(ticks)))throw new AssertionError("AOD host added polling");checks++;
                time.setAlpha(.45f);root.getViewTreeObserver().dispatchOnPreDraw();near(time.getAlpha(),.45f);near(time.getTransitionAlpha(),0);
                host.contentAlpha(0);root.getViewTreeObserver().dispatchOnPreDraw();
                if(!host.shown())throw new AssertionError("AOD OFF discarded ownership");near(time.getTransitionAlpha(),0);near(date.getTransitionAlpha(),0);
                host.show(root,time,date);host.contentAlpha(1);near(clock.getAlpha(),1);near(time.getTransitionAlpha(),0);
                host.leave();near(time.getTransitionAlpha(),1);near(time.getAlpha(),.45f);
                if(host.ownsClock()||host.notificationTop()!=0)throw new AssertionError("Wake retained stock clock or notification ownership");checks++;
                host.show(root,time,date);host.contentAlpha(1);host.leave(true);
                Field fadeField=AodClockHost.class.getDeclaredField("fade"),stockField=AodClockHost.class.getDeclaredField("stockFade");
                fadeField.setAccessible(true);stockField.setAccessible(true);
                ((android.animation.ValueAnimator)fadeField.get(host)).end();
                root.getViewTreeObserver().dispatchOnPreDraw();near(time.getTransitionAlpha(),0);near(date.getTransitionAlpha(),0);
                if(host.shown()||host.notificationTop()!=0||stockField.get(host)!=null)throw new AssertionError("Custom fade completion released stock clock before frame submission");checks++;
                host.frameReady();android.animation.ValueAnimator reveal=(android.animation.ValueAnimator)stockField.get(host);
                if(reveal==null)throw new AssertionError("Submitted frame did not start stock fade");checks++;
                reveal.setCurrentPlayTime(80);float progress=time.getTransitionAlpha();
                if(progress<=0||progress>=1)throw new AssertionError("Stock clock did not fade independently");checks++;
                root.getViewTreeObserver().dispatchOnPreDraw();near(time.getTransitionAlpha(),progress);near(time.getAlpha(),.45f);
                reveal.end();near(time.getTransitionAlpha(),1);near(date.getTransitionAlpha(),1);
                if(host.shown())throw new AssertionError("Completed handoff retained overlay");checks++;
                host.show(root,time,date);host.leave(true);host.frameReady();
                android.animation.ValueAnimator interrupted=(android.animation.ValueAnimator)stockField.get(host);interrupted.setCurrentPlayTime(80);
                host.show(root,time,date);host.contentAlpha(1);interrupted.end();root.getViewTreeObserver().dispatchOnPreDraw();near(time.getTransitionAlpha(),0);
                if(!host.shown())throw new AssertionError("Stale wake callback removed reentered AOD");checks++;
                host.leave(true);host.leave(false);near(time.getTransitionAlpha(),1);near(date.getTransitionAlpha(),1);
                host.hide();host.show(root,time,date);host.contentAlpha(0);host.leave(true);
                near(time.getTransitionAlpha(),0);host.frameReady();((android.animation.ValueAnimator)stockField.get(host)).end();near(time.getTransitionAlpha(),1);
                if(host.shown())throw new AssertionError("Wake from AOD_OFF retained overlay");checks++;
                for(float visible:new float[]{0,1}){
                    host.show(root,time,date);host.contentAlpha(visible);host.leaveUnlocked();
                    android.animation.ValueAnimator customFade=(android.animation.ValueAnimator)fadeField.get(host);
                    if(customFade!=null)customFade.end();
                    // Both a late wallpaper frame and a repeated UNLOCK event
                    // must leave the native normal-unlock clock concealed.
                    host.frameReady();host.leaveUnlocked();root.getViewTreeObserver().dispatchOnPreDraw();
                    near(time.getTransitionAlpha(),0);near(date.getTransitionAlpha(),0);near(notifications.getAlpha(),1);
                    if(stockField.get(host)!=null||host.ownsClock()||host.notificationTop()!=0)throw new AssertionError("Direct unlock reused lock reveal");checks++;
                    host.unlockFinished();near(time.getTransitionAlpha(),1);near(date.getTransitionAlpha(),1);
                    if(host.shown())throw new AssertionError("Native unlock completion retained overlay");checks++;
                }
                host.show(root,time,date);host.leave(true);host.frameReady();
                android.animation.ValueAnimator cancelledReveal=(android.animation.ValueAnimator)stockField.get(host);
                cancelledReveal.setCurrentPlayTime(80);host.leaveUnlocked();cancelledReveal.end();
                near(time.getTransitionAlpha(),0);host.frameReady();near(time.getTransitionAlpha(),0);
                host.show(root,time,date);host.unlockFinished();near(time.getTransitionAlpha(),0);
                if(!host.ownsClock())throw new AssertionError("Stale unlock completion released new AOD");checks++;
                host.leaveUnlocked();host.leave(false);near(time.getTransitionAlpha(),1);
                host.unlockFinished();near(time.getTransitionAlpha(),1);
                host.show(root,time,date);host.contentAlpha(1);
                if(!host.shown())throw new AssertionError("Cancelled wake fade removed reentered AOD");checks++;
                host.hide();near(time.getAlpha(),.45f);near(date.getAlpha(),.6f);near(notifications.getAlpha(),1);
                if(host.shown()||root.getChildCount()!=3||active.getBoolean(ticks))throw new AssertionError("AOD clock leaked into lockscreen");checks++;
                host.show(root,time,date);host.show(root,time,date);if(root.getChildCount()!=4)throw new AssertionError("Duplicate AOD clock");checks++;
                host.hide();
                FrameLayout scope=new FrameLayout(activity);root.addView(scope);
                ClockTimeView compact=new ClockTimeView(activity);DateMessageView compactDate=new DateMessageView(activity);
                scope.addView(compact);scope.addView(compactDate);host.show(root,time,date,scope);
                root.getViewTreeObserver().dispatchOnPreDraw();near(compact.getTransitionAlpha(),0);near(compactDate.getTransitionAlpha(),0);near(notifications.getAlpha(),1);
                scope.removeView(compact);ClockTimeView replacement=new ClockTimeView(activity);scope.addView(replacement);
                root.getViewTreeObserver().dispatchOnPreDraw();near(replacement.getTransitionAlpha(),0);near(compact.getTransitionAlpha(),1);
                // Simulate the actual clock moving to another attached window
                // container. Our overlay remains under the stable shade root.
                FrameLayout external=new FrameLayout(activity);decor.addView(external);root.removeView(scope);external.addView(scope);
                AodClockView stable=(AodClockView)root.getChildAt(3);root.removeView(time);external.addView(time);
                host.show(root,time,date,scope);root.getViewTreeObserver().dispatchOnPreDraw();near(replacement.getTransitionAlpha(),0);
                if(stable.getParent()!=root)throw new AssertionError("Custom AOD clock migrated with stock surface");checks++;
                time.setAlpha(0);host.hide();near(time.getAlpha(),0);near(time.getTransitionAlpha(),1);
                time.setAlpha(.45f);near(replacement.getTransitionAlpha(),1);external.removeView(scope);external.removeView(time);root.addView(time,0);decor.removeView(external);
                host.hide();
                // Magazine artwork and time share outer containers with widgets.
                // Neither the shared containers nor the widgets may be masked.
                FrameLayout magazine=new FrameLayout(activity),timeZone=new FrameLayout(activity);
                ShellMaterialContainer materials=new ShellMaterialContainer(activity);
                root.addView(magazine);magazine.addView(materials);magazine.addView(timeZone);
                View art=materials.art,animatedArt=new COETextureView(activity),widgetA=new ImageView(activity),widgetB=new View(activity);
                TextTimeTextView textTime=new TextTimeTextView(activity);TextDateInformationView textDate=new TextDateInformationView(activity);
                materials.addView(animatedArt);materials.addView(widgetA);
                timeZone.addView(textTime);timeZone.addView(textDate);timeZone.addView(widgetB);
                widgetA.setAlpha(.7f);widgetB.setTranslationY(42);art.setAlpha(.6f);
                host.show(root,textTime,textDate.date,magazine);root.getViewTreeObserver().dispatchOnPreDraw();
                near(art.getTransitionAlpha(),0);near(animatedArt.getTransitionAlpha(),0);near(textTime.getTransitionAlpha(),0);near(textDate.date.getTransitionAlpha(),0);
                near(textDate.getTransitionAlpha(),1);near(textDate.extra.getTransitionAlpha(),1);
                near(magazine.getTransitionAlpha(),1);near(materials.getTransitionAlpha(),1);near(timeZone.getTransitionAlpha(),1);
                near(widgetA.getTransitionAlpha(),1);near(widgetA.getAlpha(),.7f);near(widgetB.getTransitionAlpha(),1);near(widgetB.getTranslationY(),42);
                host.contentAlpha(0);root.getViewTreeObserver().dispatchOnPreDraw();near(art.getTransitionAlpha(),0);near(widgetA.getTransitionAlpha(),1);
                host.contentAlpha(1);host.leave(true);((android.animation.ValueAnimator)fadeField.get(host)).end();host.frameReady();
                android.animation.ValueAnimator textReveal=(android.animation.ValueAnimator)stockField.get(host);textReveal.setCurrentPlayTime(80);
                near(art.getTransitionAlpha(),textTime.getTransitionAlpha());near(widgetA.getTransitionAlpha(),1);near(widgetB.getTransitionAlpha(),1);
                textReveal.end();near(art.getTransitionAlpha(),1);near(art.getAlpha(),.6f);near(textTime.getTransitionAlpha(),1);host.hide();root.removeView(magazine);
                observations.add("Magazine clock/art only: shared parents, widgets and widget transforms preserved during AOD, OFF and submitted-frame wake fade");
                host.show(root,time,time);if(host.shown())throw new AssertionError("Unsafe clock scope accepted");checks++;
                host.show(root,time,date);decor.removeView(root);
                if(host.shown())throw new AssertionError("Detached window retained AOD clock");near(time.getAlpha(),.45f);near(date.getAlpha(),.6f);checks++;
                EditorClockView editor=new EditorClockView(activity,null);editor.scene(true);if(editor.getVisibility()!=View.VISIBLE)throw new AssertionError("Missing AOD preview");
                editor.scene(false);if(editor.getVisibility()==View.VISIBLE)throw new AssertionError("Custom clock appeared on lock/home preview");checks++;
                observations.add("AOD-only clock: native tick without wallpaper frames, midnight, no polling, unchanged notifications, alpha restoration and detach cleanup passed");
            }finally{host.hide();if(root.getParent()==decor)decor.removeView(root);}
        });
        close(false);
    }
    private void aodWidgetSpacing()throws Exception{
        open("accepted.png",false);
        main(()->{
            FrameLayout decor=(FrameLayout)activity.getWindow().getDecorView();
            FrameLayout root=new FrameLayout(activity),widgetRoot=new FrameLayout(activity);
            View card=new View(activity),time=new View(activity),date=new View(activity);
            decor.addView(root,new FrameLayout.LayoutParams(-1,-1));
            root.addView(time);root.addView(date);root.addView(widgetRoot,new FrameLayout.LayoutParams(-1,-1));
            FrameLayout.LayoutParams cardParams=new FrameLayout.LayoutParams(800,220);cardParams.topMargin=400;
            card.setId(-0x3f6fdc3);widgetRoot.addView(card,cardParams);widgetRoot.setTranslationY(17);widgetRoot.setAlpha(.7f);
            AodWidgetSpace space=new AodWidgetSpace();AodClockHost host=new AodClockHost();
            AodWidgetSpace.Bounds bounds=new ColorOsWidgetBounds(widgetRoot);
            try{
                root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,2400);
                for(float scale:new float[]{1,.9f,1.2f}){
                    root.setScaleY(scale);space.bind(widgetRoot,bounds);
                    int floor=bounds.read().top+150;space.update(floor);
                    if(Math.abs(bounds.read().top-floor)>1)throw new AssertionError("Widget floor uses wrong parent scale");checks++;
                    float settled=widgetRoot.getTranslationY();
                    for(int i=0;i<80;i++)space.update(floor);
                    near(widgetRoot.getTranslationY(),settled);near(widgetRoot.getAlpha(),.7f);near(root.getScaleY(),scale);
                    space.update(floor-60);if(Math.abs(bounds.read().top-(floor-60))>1)throw new AssertionError("Widget floor did not shrink");checks++;
                    widgetRoot.setTranslationY(33);space.update(floor);space.restore();near(widgetRoot.getTranslationY(),33);
                    widgetRoot.setTranslationY(17);space.bind(widgetRoot,bounds);space.update(bounds.read().top-5);near(widgetRoot.getTranslationY(),17);
                    space.clear();
                }
                root.setScaleY(1);space.bind(widgetRoot,bounds);space.update(bounds.read().top+150);
                space.bind(widgetRoot,()->null);space.update(1600);near(widgetRoot.getTranslationY(),17);space.clear();
                host.show(root,time,date);host.widgets(widgetRoot,bounds);
                root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,2400);
                root.getViewTreeObserver().dispatchOnPreDraw();
                AodClockView face=(AodClockView)root.getChildAt(3);int floor=face.notificationTop();
                if(bounds.read().top<floor-1||host.notificationTop()<=bounds.read().bottom)throw new AssertionError("Clock/widgets/notifications overlap");checks++;
                near(face.getScaleX(),1);near(face.getScaleY(),1);
                host.leave(true);near(widgetRoot.getTranslationY(),17);host.hide();
                host.show(root,time,date);host.widgets(widgetRoot,bounds);
                root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,2400);root.getViewTreeObserver().dispatchOnPreDraw();
                host.leaveUnlocked();near(widgetRoot.getTranslationY(),17);host.unlockFinished();host.hide();
                host.show(root,time,date);host.widgets(root,bounds);near(root.getTranslationY(),0);host.hide();
                observations.add("Widget spacing: scaled parents, repeated traversals, external writes, missing bounds, clock/notification ordering and both wake paths passed");
            }finally{space.clear();host.hide();decor.removeView(root);}
        });
        close(false);
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);onlyClockHost=args!=null&&"true".equals(args.getString("onlyClockHost"));start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(onlyClockHost){
            fixture("accepted.png",600,400);aodClockHost();aodWidgetSpacing();
            result.putString("stream","CLOCK_HOST_OK checks="+checks+"\n"+String.join("\n",observations));finish(Activity.RESULT_OK,result);return;
        }
        for(int[] size:new int[][]{{800,1200},{1200,800},{800,800}}){
            String name="fixture-"+size[0]+"x"+size[1]+".png";fixture(name,size[0],size[1]);
            prefs().edit().clear().putString("photo","shared-wallpaper.png").putString("frame_photo",name).commit();
            open(name,false);FrameCrop[] selected={null};
            main(()->{
                ImageView p=(ImageView)field("photo");p.getClass().getMethod("setScale",float.class).invoke(p,2f);
                near(current().size,.5f);float before=current().x;long now=SystemClock.uptimeMillis();float center=p.getWidth()/2f;
                for(int i=0;i<3;i++){MotionEvent e=MotionEvent.obtain(now,now+i*16,i==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_MOVE,center+i*p.getWidth()/16f,center,0);p.dispatchTouchEvent(e);e.recycle();}
                if(current().x>=before)throw new AssertionError("Drag did not move crop");checks++;
                MotionEvent cancel=MotionEvent.obtain(now,now+48,MotionEvent.ACTION_CANCEL,center,center,0);p.dispatchTouchEvent(cancel);cancel.recycle();
                FrameCrop wanted=new FrameCrop(.46f,.55f,.52f,23).fit(size[0],size[1]);
                invoke("restore",new Class<?>[]{FrameCrop.class},wanted);same(current(),wanted);selected[0]=current();
            });
            close(true);SceneOptions saved=new SceneOptions(prefs());
            same(selected[0],new FrameCrop(saved.frameX,saved.frameY,saved.frameSize,saved.frameAngle));
            if(!saved.photo.equals("shared-wallpaper.png")||!saved.framePhoto.equals(name))throw new AssertionError("Wrong photo target");checks++;
            open(name,false);main(()->same(current(),selected[0]));close(false);
            observations.add(size[0]+"x"+size[1]+": original PhotoView zoom/drag, angle save/reopen and resource isolation passed");
        }
        Map<String,?> before=new HashMap<>(prefs().getAll());File cancelled=fixture("cancelled.png",400,600);
        open(cancelled.getName(),true);close(false);
        if(!before.equals(prefs().getAll()))throw new AssertionError("Cancel changed settings");
        if(cancelled.exists())throw new AssertionError("Cancel kept pending file");checks+=2;
        File accepted=fixture("accepted.png",600,400);open(accepted.getName(),true);close(true);
        if(!accepted.exists()||!new SceneOptions(prefs()).framePhoto.equals(accepted.getName()))throw new AssertionError("New photo was not retained");checks++;
        clockContent();aodClockHost();aodWidgetSpacing();
        result.putString("stream","CROP_OK checks="+checks+"\n"+String.join("\n",observations));finish(Activity.RESULT_OK,result);
    }catch(Throwable t){result.putString("stream",android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
