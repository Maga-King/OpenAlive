package org.aliveclean;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Production Activity regression, restoring all draft preferences afterwards. */
public final class VivoEditorFlowProbe extends Instrumentation {
    interface Work{void run()throws Exception;}
    void main(Work work){Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void call(Object o,String name,Class<?>[] types,Object... args)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}
    void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    interface Ready{boolean yes()throws Exception;}
    void until(Ready ready,String message)throws Exception{long end=SystemClock.uptimeMillis()+15000;boolean[] ok={false};do{main(()->ok[0]=ready.yes());if(ok[0])return;SystemClock.sleep(40);}while(SystemClock.uptimeMillis()<end);throw new AssertionError(message);}
    void gl(Object loop,Work work)throws Exception{CountDownLatch latch=new CountDownLatch(1);Throwable[] error={null};((Handler)get(loop,"handler")).post(()->{try{work.run();}catch(Throwable e){error[0]=e;}finally{latch.countDown();}});check(latch.await(15,TimeUnit.SECONDS),"GL work timed out");if(error[0]!=null)throw new AssertionError(error[0]);}
    long step;
    void touch(View view,int action,float... xy){
        int n=xy.length/2;MotionEvent.PointerProperties[] p=new MotionEvent.PointerProperties[n];MotionEvent.PointerCoords[] c=new MotionEvent.PointerCoords[n];
        for(int i=0;i<n;i++){p[i]=new MotionEvent.PointerProperties();p[i].id=i;p[i].toolType=1;c[i]=new MotionEvent.PointerCoords();c[i].x=xy[2*i];c[i].y=xy[2*i+1];c[i].pressure=1;c[i].size=1;}
        MotionEvent e=MotionEvent.obtain(1000,1000+step++*16,action,n,p,c,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);view.dispatchTouchEvent(e);e.recycle();
    }
    void restore(SharedPreferences preferences,Map<String,?> saved){SharedPreferences.Editor e=preferences.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){
        String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);
    }e.commit();}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();VivoEditorActivity activity=null;Context context=getTargetContext();
        SharedPreferences editor=context.getSharedPreferences("vivo.editor",0),draft=context.getSharedPreferences(SceneOptions.DRAFT,0);
        Map<String,?> savedEditor=new HashMap<>(editor.getAll()),savedDraft=new HashMap<>(draft.getAll());Set<String> before=new HashSet<>(Arrays.asList(context.fileList()));
        StringBuilder report=new StringBuilder();boolean passed=false;
        try{
            for(int family:new int[]{1,3}){
                editor.edit().remove("family."+family).commit();
                activity=(VivoEditorActivity)startActivitySync(new Intent(context,VivoEditorActivity.class).putExtra("family",family).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final VivoEditorActivity a=activity;
                until(()->get(a,"runtime")!=null&&get(a,"apply")!=null&&((View)get(a,"preview")).getWidth()>0,"Editor did not load");
                File photo=new File(context.getFilesDir(),"vivo-flow-fixture.png");Bitmap b=Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888);b.eraseColor(0xff3366aa);try(OutputStream out=new FileOutputStream(photo)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
                main(()->a.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(photo))));
                until(()->!(Boolean)get(a,"busy")&&!((VivoOptions)get(a,"options")).photos.isEmpty(),"Import did not complete");
                main(()->{VivoOptions o=(VivoOptions)get(a,"options");check(o.json().equals(VivoOptions.parse(o.json()).json()),"Imported snapshot is not canonical");call(a,"editMask",new Class<?>[]{boolean.class,int.class},false,1);});
                until(()->get(a,"maskEditor")!=null&&((Dialog)get(get(a,"maskEditor"),"dialog")).isShowing(),"New photo drawing range failed without reopening");
                main(()->((VivoMaskEditor)get(a,"maskEditor")).close());
                main(()->call(a,"editMask",new Class<?>[]{boolean.class,int.class},true,2));
                until(()->get(a,"maskEditor")!=null&&((Boolean)get(get(a,"maskEditor"),"subject"))&&((Dialog)get(get(a,"maskEditor"),"dialog")).isShowing(),"New photo subject range failed");
                main(()->((VivoMaskEditor)get(a,"maskEditor")).close());
                Object loop=get(a,"renderer");Object[] identity={null};gl(loop,()->identity[0]=get(loop,"vivo"));check(identity[0]!=null,"No live scene");
                View view=(View)get(a,"preview");float x=view.getWidth()*.5f,y=view.getHeight()*.5f,span=view.getHeight()*.1f;
                main(()->{touch(view,0,x,y-span);touch(view,5|(1<<8),x,y-span,x,y+span);});
                long start=SystemClock.uptimeMillis();
                for(int i=1;i<=40;i++){final int n=i;main(()->touch(view,2,x+n,y-span*(1+n*.025f),x+n,y+span*(1+n*.025f)));SystemClock.sleep(16);}
                main(()->{touch(view,6|(1<<8),x+40,y-span*2,x+40,y+span*2);touch(view,1,x+40,y-span*2);});
                waitForIdleSync();gl(loop,()->{});SystemClock.sleep(100);gl(loop,()->check(get(loop,"vivo")==identity[0],"Saving crop rebuilt the entire scene"));
                main(()->check(((VivoOptions)get(a,"options")).zoom>1.2f,"Pinch did not update crop"));
                report.append("family ").append(family).append(": fresh import drawing+subject dialogs, continuous pinch and same scene after save OK; gesture+drain ms=").append(SystemClock.uptimeMillis()-start).append('\n');
                main(a::finish);activity=null;waitForIdleSync();
            }passed=true;report.append("VIVO_EDITOR_FLOW_OK\n");
        }catch(Throwable e){StringWriter w=new StringWriter();e.printStackTrace(new PrintWriter(w));report.append(w);}
        finally{if(activity!=null){final Activity a=activity;main(a::finish);}waitForIdleSync();restore(editor,savedEditor);restore(draft,savedDraft);
            for(String name:context.fileList())if(name.startsWith("vivo-")&&!before.contains(name))context.deleteFile(name);
        }
        result.putString("stream",report.toString());finish(passed?-1:0,result);
    }
}
