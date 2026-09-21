package org.aliveclean;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Looper;
import dalvik.system.DexClassLoader;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.json.*;

/** Runs only via app_process. Loads the unmodified official APK as a numerical oracle. */
public final class MotionParity {
    static ClassLoader reference;
    static Object unsafe;
    static Method allocate;
    static Context context;
    static int checks;
    static float maxError;
    static final String[] METHODS={"f","p","N","O","C","K"};
    static final int[][] PAIRS={{0,1},{1,0},{0,2},{2,0},{1,2},{2,1}};
    static final long[] TIMES={0,1,12,24,33,100,183,425,470,649,650,700,800,850,950,1000,1300,1500,3840};
    static Class<?> type(String name)throws Exception{return reference.loadClass(name);}
    static Object bare(String name)throws Exception{return allocate.invoke(unsafe,type(name));}
    static Field field(Class<?> c,String name)throws Exception{while(c!=null){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
    static Object get(Object o,String name)throws Exception{return field(o.getClass(),name).get(o);}
    static void set(Object o,String name,Object value)throws Exception{field(o.getClass(),name).set(o,value);}
    static Method method(Class<?> c,String name,Class<?>... args)throws Exception{while(c!=null){try{Method m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m;}catch(NoSuchMethodException e){c=c.getSuperclass();}}throw new NoSuchMethodException(name);}
    static void equal(float actual,float expected,String label) {
        float error=Math.abs(actual-expected);maxError=Math.max(maxError,error);checks++;
        if(!Float.isFinite(actual)||error>0.00003f)throw new AssertionError(label+": actual="+actual+" reference="+expected);
    }
    static void seek(Animator a,long time){time=Math.min(time,a.getTotalDuration());if(a instanceof AnimatorSet)((AnimatorSet)a).setCurrentPlayTime(time);else if(a instanceof ValueAnimator)((ValueAnimator)a).setCurrentPlayTime(time);}
    static final class Oracle {
        final Object effect,config,controller;
        final String sceneType;
        final String[] properties;
        final int offset;
        Oracle(String family,int width,int height)throws Exception{
            boolean lens=family.equals("m0"),full=family.equals("l0"),ground=family.equals("x0");
            sceneType=family+"."+(full?"f":ground?"g":"e");
            effect=bare(family+"."+(full?"e":ground?"f":"d"));
            Class<?> configType=type(family+"."+(full?"d":ground?"e":"c"));
            config=lens?configType.getConstructor(String.class).newInstance(height/(float)width>2.325f&&height/(float)width<2.335f?"21:9":"20:9"):configType.getConstructor().newInstance();
            controller=type(family+"."+(full||ground?"b":"a")).getConstructor(effect.getClass(),configType,configType).newInstance(effect,config,config);
            Object env=type("com.meizu.flyme.sdkstage.wallpaper.n").getConstructor().newInstance();
            method(env.getClass(),"e",int.class,int.class).invoke(env,width,height);
            field(type("n0.c"),"j").set(effect,context);field(type("n0.c"),"n").set(effect,env);
            if(lens){set(effect,"N",type("a2.l").getConstructor().newInstance());set(effect,"O",type("a2.l").getConstructor().newInstance());}
            if(ground)set(effect,"B",new float[2]);
            properties=lens?new String[]{"x","y","z","B","C","D","E","F","A","G"}:
                    full?new String[]{"x","y"}:ground?new String[]{"w","x"}:new String[]{"w","x","y","z","A","B"};
            offset=lens?SceneMotion.LENS:full?SceneMotion.FULL:ground?SceneMotion.GROUND:SceneMotion.GLASS;
        }
        void initialize(int mode)throws Exception {
            Object state=get(config,new String[]{"a","b","c"}[mode]);
            method(type("z1.b"),"a",type("z1.e")).invoke(state,effect);
        }
        Animator transition(int tr)throws Exception {
            Object snapshot=type(sceneType).getConstructor(effect.getClass()).newInstance(effect);
            Object target=get(config,new String[]{"a","b","c"}[PAIRS[tr][1]]);
            return (Animator)method(controller.getClass(),METHODS[tr],type("z1.b"),type("z1.b")).invoke(controller,snapshot,target);
        }
        void compare(SceneMotion ours,String label)throws Exception{
            for(int i=0;i<properties.length;i++)equal(ours.values[offset+i],((Number)get(effect,properties[i])).floatValue(),label+" property="+properties[i]);
        }
    }
    static void animations()throws Exception {
        for(String family:new String[]{"m0","a1","w0","x0","l0"})for(int[] size:new int[][]{{1440,3168},{1080,2520}})for(int tr=0;tr<6;tr++){
            int aod=family.equals("l0")?101:0,lock=family.equals("a1")?1:family.equals("w0")?2:family.equals("x0")?3:0;
            SceneMotion ours=new SceneMotion(size[0],size[1],PAIRS[tr][0],aod,lock,6);
            Oracle oracle=new Oracle(family,size[0],size[1]);oracle.initialize(PAIRS[tr][0]);
            oracle.compare(ours,family+" initial");
            if(family.equals("m0")&&PAIRS[tr][0]==0){ours.values[12]=-217.25f;set(oracle.effect,"G",-217.25f);}
            Animator expected=oracle.transition(tr);
            AnimatorSet actual=ours.prepare(PAIRS[tr][1]);actual.setStartDelay(0);
            for(long time:TIMES){if(expected!=null)seek(expected,time);seek(actual,time);oracle.compare(ours,family+" transition="+tr+" time="+time);}
        }
    }
    static void interruptions()throws Exception {
        // Reverse while the aperture is still moving. Both sides start from live property snapshots.
        for(int elapsed:new int[]{0,12,24,100,470,649}){
            Oracle oracle=new Oracle("m0",1440,3168);oracle.initialize(0);set(oracle.effect,"G",-251.8f);
            SceneMotion ours=new SceneMotion(1440,3168,0,0,2,6);ours.values[12]=-251.8f;
            Animator expected=oracle.transition(0);AnimatorSet actual=ours.prepare(1);actual.setStartDelay(0);
            seek(expected,elapsed);seek(actual,elapsed);oracle.compare(ours,"before interrupt");
            expected=oracle.transition(1);actual=ours.prepare(0);actual.setStartDelay(0);
            for(long t:new long[]{0,12,24,100,649,650,1000}){seek(expected,t);seek(actual,t);oracle.compare(ours,"reverse at "+elapsed+" +"+t);}
        }
        SceneMotion m=new SceneMotion(1440,3168,0,0,0,6);
        if(m.prepare(1).getStartDelay()!=150)throw new AssertionError("Missing official wake delay");
        m=new SceneMotion(1440,3168,1,0,0,6);m.prepare(0).start();
        if(m.prepare(1).getStartDelay()!=0)throw new AssertionError("Interrupted animation added a wake delay");
        m.close();
    }
    static void matrices()throws Exception {
        for(int[] size:new int[][]{{96,192},{1440,3168},{1080,2520}}){
            Oracle oracle=new Oracle("m0",size[0],size[1]);
            for(float angle:new float[]{-539.9f,-360,-217.25f,-.01f,0,84})for(float y:new float[]{-.23f,0,.335f})for(float scale:new float[]{.22f,.57f,1.24f}){
                SceneMotion ours=new SceneMotion(size[0],size[1],0,0,0,6);
                ours.values[12]=angle;ours.values[8]=.13f;ours.values[9]=y;ours.values[4]=scale;ours.values[7]=-67;
                ours.packet();
                method(oracle.effect.getClass(),"D0",float.class,int.class,int.class,float.class,float.class).invoke(oracle.effect,angle,size[0],size[1],.13f,y);
                method(oracle.effect.getClass(),"C0",float.class,float.class,int.class,int.class,float.class,float.class).invoke(oracle.effect,scale*2,-67f,size[0],size[1],.13f,y);
                for(int matrix=0;matrix<2;matrix++){
                    Object wrapper=get(oracle.effect,matrix==0?"N":"O");float[] expected=(float[])method(wrapper.getClass(),"a").invoke(wrapper);
                    for(int i=0;i<16;i++)equal(ours.values[(matrix==0?25:41)+i],expected[i],"matrix "+matrix+" slot "+i);
                }
            }
        }
        Method nearest=method(type("m0.a"),"c0",float.class,float.class);
        for(float a:new float[]{-1000,-719.2f,-540.01f,-360,-181,-.1f,0,180,359,720.6f})
            equal(SceneMotion.nearestRotation(a,-360),(float)nearest.invoke(null,a,-360f),"nearest angle "+a);
    }
    static void pacing() {
        VsyncPacer p=new VsyncPacer();
        for(int hz:new int[]{30,60,90,120,144}){
            p.reset();int rendered=0;
            for(int i=0;i<hz;i++){long time=1000000000L+i*1000000000L/hz;
                if(p.due(time))rendered++;
                if(p.due(time))throw new AssertionError("Duplicate decoder callback submitted the same vsync");checks++;
            }
            if(rendered!=hz)throw new AssertionError("Display cadence capped "+hz+": "+rendered);checks++;
        }
        p.reset();if(!p.due(2000)||p.due(1999))throw new AssertionError("Out-of-order vsync");checks++;
        p.reset();if(!p.due(0))throw new AssertionError("New surface cadence");checks++;
    }
    static void movingClock() {
        SceneMotion moving=new SceneMotion(1440,3168,1,0,0,6);
        AnimatorSet actual=moving.prepare(0);actual.start();actual.pause();
        seek(actual,280);
        float scale=moving.values[SceneMotion.LENS+1];
        moving.followClock(.08f,.21f);
        if(actual.getCurrentPlayTime()!=280)throw new AssertionError("Clock retarget reset play time");
        equal(moving.values[SceneMotion.LENS+1],scale,"clock retarget preserves scale");
        SceneMotion expected=new SceneMotion(1440,3168,1,0,0,6);
        expected.aodPosition(.08f,.21f);AnimatorSet reference=expected.prepare(0);
        for(long t:new long[]{281,500,650,1000}){
            seek(actual,t);seek(reference,t);
            equal(moving.values[SceneMotion.LENS+5],expected.values[SceneMotion.LENS+5],"moving clock x");
            equal(moving.values[SceneMotion.LENS+6],expected.values[SceneMotion.LENS+6],"moving clock y");
            equal(moving.values[SceneMotion.LENS+1],expected.values[SceneMotion.LENS+1],"moving clock scale");
        }
        moving.finish();moving.followClock(-.04f,.24f);
        equal(moving.values[SceneMotion.LENS+5],-.04f,"stable clock x");
        equal(moving.values[SceneMotion.LENS+6],.24f,"stable clock y");
        moving.close();expected.close();
        SceneMotion settling=new SceneMotion(1440,3168,1,0,0,6);
        AnimatorSet tail=settling.prepare(0);tail.start();tail.pause();seek(tail,700);
        settling.followClock(.03f,.25f);
        equal(settling.values[SceneMotion.LENS+5],.03f,"finished position track follows clock x");
        equal(settling.values[SceneMotion.LENS+6],.25f,"finished position track follows clock y");
        seek(tail,900);settling.followClock(.04f,.26f);
        equal(settling.values[SceneMotion.LENS+6],.26f,"clock follows during remaining tracks");
        settling.finish();settling.advance(1);
        equal(settling.values[SceneMotion.LENS+6],.26f,"no position jump at set completion");settling.close();
    }
    static JSONArray samples()throws Exception {
        JSONArray out=new JSONArray();
        for(int aod:new int[]{-1,0,101})for(int lock=0;lock<6;lock++)for(int home=6;home<10;home++)for(int mode=0;mode<3;mode++){
            SceneMotion motion=new SceneMotion(96,192,mode,aod,lock,home);motion.packet();
            out.put(new JSONObject().put("styles",new JSONArray(new int[]{aod,lock,home})).put("mode",mode).put("values",new JSONArray(motion.values)));
        }
        // Capture actual Android values including both matrices for CPU GLES replay.
        SceneMotion m=new SceneMotion(96,192,0,0,0,6);m.values[12]=-217.25f;
        AnimatorSet a=m.prepare(1);a.setStartDelay(0);
        for(long t:new long[]{0,12,24,33,100,183,470,850,949,950,951,1000}){
            seek(a,t);m.packet();out.put(new JSONObject().put("styles",new JSONArray(new int[]{0,0,6})).put("mode",1).put("time",t).put("values",new JSONArray(m.values)));
        }
        return out;
    }
    public static void main(String[] args)throws Exception {
        try{
            Looper.prepareMainLooper();
            Class<?> u=Class.forName("sun.misc.Unsafe");Field singleton=u.getDeclaredField("theUnsafe");singleton.setAccessible(true);unsafe=singleton.get(null);allocate=u.getMethod("allocateInstance",Class.class);
            Class<?> activityThread=Class.forName("android.app.ActivityThread");Object at=activityThread.getMethod("systemMain").invoke(null);context=(Context)activityThread.getMethod("getSystemContext").invoke(at);
            reference=new DexClassLoader(args[0],"/data/local/tmp",null,MotionParity.class.getClassLoader());
            animations();interruptions();matrices();pacing();movingClock();
            System.out.println("MOTION_RESULT "+new JSONObject().put("checks",checks).put("max_error",maxError).put("frames",samples()));
            System.exit(0);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
}
