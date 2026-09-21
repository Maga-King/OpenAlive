package org.aliveclean;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;
import java.util.*;

/** Real original controls, transactions and provider routing in the installed app. */
public final class EditorInstrumentation extends Instrumentation {
    private MainActivity activity;
    private OfficialEditor editor;
    private int checks;
    private final ArrayList<String> observations=new ArrayList<>();
    private SharedPreferences draft,applied;
    private Map<String,?> originalDraft,originalApplied;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    private View find(String id){return editor.root.findViewById(editor.root.getResources().getIdentifier(id,"id","com.flyme.systemuieditor"));}
    private void idle(){waitForIdleSync();SystemClock.sleep(450);waitForIdleSync();}
    private void click(View view){check(view!=null,"Missing control");runOnMainSync(()->check(view.performClick(),"Control has no action"));idle();}
    private View named(View root,String name){
        if(root.getContentDescription()!=null&&root.getContentDescription().toString().startsWith(name))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View result=named(group.getChildAt(i),name);if(result!=null)return result;}}
        return null;
    }
    private View option(String name){
        Object panel=field(editor,"visiblePanel");Object list=field(panel,"list");
        List<?> choices=(List<?>)field(panel,"choices");int index=-1;
        for(int i=0;i<choices.size();i++)if(name.equals(field(choices.get(i),"title")))index=i;
        check(index>=0,"Missing option "+name);final int position=index;
        runOnMainSync(()->{try{list.getClass().getMethod("scrollToPosition",int.class).invoke(list,position);}catch(Exception e){throw new RuntimeException(e);}});idle();
        return named(editor.root,name);
    }
    private void order(String... titles){
        List<?> choices=(List<?>)field(field(editor,"visiblePanel"),"choices");
        for(int i=0;i<titles.length;i++)check(titles[i].equals(field(choices.get(i),"title")),"Wrong action order at "+i);
    }
    private void rendered(int style){
        Object loop=field(activity,"renderer");Handler owner=(Handler)field(loop,"handler");
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);Throwable[] failure={null};
        long deadline=SystemClock.uptimeMillis()+5000;
        owner.post(new Runnable(){public void run(){try{
            MaskFrames masks=(MaskFrames)field(loop,"masks");
            if((masks==null||((Integer)field(masks,"uploaded"))<0)&&SystemClock.uptimeMillis()<deadline){owner.postDelayed(this,50);return;}
            check(((Long)field(loop,"scene"))!=0,"Native scene failed for "+style);
            FrameMotion motion=(FrameMotion)field(loop,"frameMotion");check(motion!=null&&motion.style.id==style,"Wrong effect pipeline "+style);
            check(masks!=null&&!masks.failed(),"Mask decoder failed for "+style);
            check(((Integer)field(masks,"uploaded"))>=0,"No mask reached GLES for "+style);
        }catch(Throwable t){failure[0]=t;}done.countDown();}});
        try{check(done.await(6,java.util.concurrent.TimeUnit.SECONDS),"GL thread did not respond");}catch(InterruptedException e){throw new RuntimeException(e);}
        if(failure[0]!=null)throw new AssertionError("Device render check",failure[0]);
    }
    private void scene(int scene){ViewGroup strip=(ViewGroup)((ViewGroup)find("tab_group")).getChildAt(0);click(strip.getChildAt(scene));check(((Integer)field(activity,"mode"))==scene,"Native tab did not change scene");}
    private Dialog dialog(){
        ArrayList<?> list=(ArrayList<?>)field(editor.dialogs,"opened");
        for(int i=list.size()-1;i>=0;i--){Dialog d=(Dialog)list.get(i);if(d.isShowing())return d;}
        throw new AssertionError("No dialog");
    }
    private ListView list(View view){
        if(view instanceof ListView)return (ListView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){ListView found=list(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    private void item(Dialog dialog,int position){
        ListView list=list(dialog.getWindow().getDecorView());check(list!=null,"Native dialog list missing");
        runOnMainSync(()->list.performItemClick(list.getChildAt(position-list.getFirstVisiblePosition()),position,list.getAdapter().getItemId(position)));idle();
    }
    private void sourceMenu(){
        scene(1);click(find("btn_wallpaper_picker"));
        Dialog source=dialog();check(source.getClass().getName().equals("z5.g"),"Not the original Flyme dialog");
        ListView list=list(source.getWindow().getDecorView());
        check("魅族壁纸".contentEquals(list.getAdapter().getItem(0).toString()),"Chinese ROM source label corrupted");
        check("自选照片".contentEquals(list.getAdapter().getItem(1).toString()),"Chinese custom source label corrupted");
        item(source,1);
        Dialog providers=dialog();ListView providersList=list(providers.getWindow().getDecorView());
        check(providersList.getCount()==3,"Not three photo sources");
        for(int i=0;i<3;i++)check(PhotoSources.LABELS[i].equals(providersList.getAdapter().getItem(i).toString()),"Provider label differs");
    }
    private void restore(){
        if(draft==null||originalDraft==null)return;
        SharedPreferences.Editor out=draft.edit().clear();
        for(Map.Entry<String,?> e:originalDraft.entrySet()){
            Object v=e.getValue();String k=e.getKey();
            if(v instanceof Integer)out.putInt(k,(Integer)v);else if(v instanceof Long)out.putLong(k,(Long)v);
            else if(v instanceof Float)out.putFloat(k,(Float)v);else if(v instanceof Boolean)out.putBoolean(k,(Boolean)v);
            else if(v instanceof String)out.putString(k,(String)v);
            else if(v instanceof Set)out.putStringSet(k,(Set<String>)v);
        }
        out.commit();
    }
    @Override public void onStart(){
        String failure=null;
        try{
            draft=getTargetContext().getSharedPreferences(SceneOptions.DRAFT,0);applied=getTargetContext().getSharedPreferences(SceneOptions.APPLIED,0);
            originalDraft=new HashMap<>(draft.getAll());originalApplied=new HashMap<>(applied.getAll());
            draft.edit().putInt("aod",1).putBoolean("frame_pair",true).commit();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            editor=(OfficialEditor)field(activity,"editor");check(editor!=null,"Editor initialization failed");
            check(editor.root.getClass().getName().equals("androidx.constraintlayout.widget.ConstraintLayout"),"Original activity layout missing");
            check(editor.preview.isAvailable(),"Preview surface not connected");
            check(find("aod_switch")==null,"AOD switch was not removed");
            for(int scene=0;scene<3;scene++)scene(scene);
            scene(0);click(find("aod_style"));
            check(field(editor,"visiblePanel")!=null,"Native sheet not shown");
            order("相框照片","调整取景","樱花示例","启航","留光");
            click(option("启航"));check(draft.getInt("aod",-1)==0,"Sphere style not selected");
            order("更换照片","启航","留光");
            String[] styles={"星月","轻启","山脉","银河"};
            for(int i=0;i<styles.length;i++){
                int id=i+2;click(option(styles[i]));check(draft.getInt("aod",-1)==id,"Photo style not selected "+id);
                check(new SceneOptions(draft).aod==id,"Photo style silently fell back "+id);
                rendered(id);
                runOnMainSync(()->editor.closePanel());idle();
                for(int scene:new int[]{1,2,0}){scene(scene);rendered(id);}
                click(find("aod_style"));
            }
            click(option("留光"));check(draft.getInt("aod",-1)==1,"Frame style not selected");
            rendered(1);
            order("相框照片","调整取景","樱花示例","启航","留光");
            check(option("调整取景")!=null,"Frame crop action missing");
            runOnMainSync(()->check(editor.closePanel(),"Back did not consume sheet"));idle();
            check(field(editor,"visiblePanel")==null,"Native sheet not dismissed");
            sourceMenu();runOnMainSync(()->dialog().dismiss());idle();
            Intent[] intercepted={null};
            ActivityMonitor monitor=new ActivityMonitor(){
                @Override public ActivityResult onStartActivity(Intent intent){intercepted[0]=new Intent(intent);return new ActivityResult(Activity.RESULT_CANCELED,null);}
            };
            addMonitor(monitor);
            try{
                for(int source=0;source<3;source++){
                    sourceMenu();intercepted[0]=null;item(dialog(),source);
                    check(intercepted[0]!=null,"Photo provider did not launch");
                    Intent route=intercepted[0];
                    if(source==PhotoSources.OPPO)check(Intent.ACTION_PICK.equals(route.getAction())&&"com.coloros.gallery3d".equals(route.getPackage()),"OPPO route wrong");
                    if(source==PhotoSources.GALLERY)check(Intent.ACTION_CHOOSER.equals(route.getAction()),"Generic gallery not chooser");
                    if(source==PhotoSources.FILES)check(Intent.ACTION_OPEN_DOCUMENT.equals(route.getAction())&&"com.android.documentsui".equals(route.getPackage()),"Native Android files route wrong");
                    check(!activity.isFinishing(),"Canceled picker closed editor");
                    observations.add("PHOTO_SOURCE "+source+" "+route.toUri(0));
                }
            }finally{removeMonitor(monitor);}
            // The frame photo and shared wallpaper must retain distinct destinations.
            scene(0);click(find("aod_style"));click(option("相框照片"));
            check(((Integer)field(activity,"importTarget"))==2,"Frame image would overwrite shared wallpaper");
            runOnMainSync(()->dialog().dismiss());idle();
            scene(2);click(find("btn_image_picker"));
            check(((Integer)field(activity,"importTarget"))==0,"Shared frame background routed to independent home");
            runOnMainSync(()->dialog().dismiss());idle();
            // Native gallery dialog must use the original controller as well.
            scene(1);click(find("btn_wallpaper_picker"));item(dialog(),0);
            check(dialog().getClass().getName().equals("z5.g"),"ROM gallery uses platform dialog");
            runOnMainSync(()->dialog().dismiss());idle();
            check(applied.getAll().equals(originalApplied),"Preview changed applied wallpaper");
            observations.add("PREVIEW_ONLY applied settings unchanged; draft restored after test");
        }catch(Throwable t){failure=android.util.Log.getStackTraceString(t);}
        finally{
            runOnMainSync(()->{if(activity!=null)activity.finish();restore();});
        }
        Bundle result=new Bundle();result.putString("stream","EDITOR_"+(failure==null?"OK":"FAILED")+" checks="+checks+"\n"+String.join("\n",observations)+"\n"+(failure==null?"":failure));
        finish(failure==null?-1:1,result);
    }
}
