package org.aliveclean;

import static org.aliveclean.MotionParity.*;
import android.animation.*;
import android.content.Context;
import android.os.Looper;
import dalvik.system.DexClassLoader;
import java.lang.reflect.*;
import java.nio.*;
import org.json.*;

/** Differential checks against the original photo-stack property controller. */
public final class FrameParity {
    static final String[] FIELDS={"x","y","z","A","B","C","E","F"};
    static Object effect,config,controller;
    static int style=1;
    static void create(int width,int height,int mode)throws Exception{
        effect=bare("i0.d");config=type("i0.c").getConstructor(float.class).newInstance((float)height/width);
        controller=type("i0.a").getConstructor(type("i0.d"),type("i0.c"),type("i0.c")).newInstance(effect,config,config);
        Object parameters=type("j0.c").getMethod("a",int.class).invoke(null,style);
        method(controller.getClass(),"d0",type("j0.b")).invoke(controller,parameters);
        Object state=get(config,new String[]{"a","b","c"}[mode]);method(type("z1.b"),"a",type("z1.e")).invoke(state,effect);
        set(effect,"H",(float)height/width);set(effect,"M",type("a2.l").getConstructor().newInstance());set(effect,"N",type("a2.l").getConstructor().newInstance());
        Object env=type("com.meizu.flyme.sdkstage.wallpaper.n").getConstructor().newInstance();method(env.getClass(),"e",int.class,int.class).invoke(env,width,height);field(type("n0.c"),"n").set(effect,env);
    }
    static Animator transition(int tr)throws Exception{
        Object from=type("i0.e").getConstructor(type("i0.d")).newInstance(effect),to=get(config,new String[]{"a","b","c"}[PAIRS[tr][1]]);
        return (Animator)method(controller.getClass(),METHODS[tr],type("z1.b"),type("z1.b")).invoke(controller,from,to);
    }
    static void compare(FrameMotion ours,String label)throws Exception{
        for(int i=0;i<8;i++)equal(ours.values[i],((Number)get(effect,FIELDS[i])).floatValue(),label+" "+FIELDS[i]);
        method(effect.getClass(),"C0",float.class).invoke(effect,ours.values[3]);
        float[] expected=(float[])method(type("a2.l"),"a").invoke(get(effect,"N"));
        FloatBuffer packet=ours.packet().order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(int i=0;i<16;i++)equal(packet.get(16+i),expected[i],label+" content matrix "+i);
    }
    public static void main(String[] args)throws Exception{
        try{
            Looper.prepareMainLooper();Class<?> u=Class.forName("sun.misc.Unsafe");Field singleton=u.getDeclaredField("theUnsafe");singleton.setAccessible(true);unsafe=singleton.get(null);allocate=u.getMethod("allocateInstance",Class.class);
            Class<?> at=Class.forName("android.app.ActivityThread");context=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
            reference=new DexClassLoader(args[0],"/data/local/tmp",null,FrameParity.class.getClassLoader());
            long[] times={0,1,49,50,100,166,167,200,399,400,449,450,483,516,517,530,566,567,799,800,1000};
            for(style=1;style<=5;style++)for(int[] size:new int[][]{{96,192},{1440,3168}})for(int tr=0;tr<6;tr++){
                create(size[0],size[1],PAIRS[tr][0]);FrameMotion ours=new FrameMotion(size[0],size[1],PAIRS[tr][0],false,PhotoStyle.get(style));compare(ours,"initial");
                Animator expected=transition(tr);AnimatorSet actual=ours.prepare(PAIRS[tr][1],500);
                for(long t:times){seek(expected,t);seek(actual,t);compare(ours,"style="+style+" transition="+tr+" t="+t);}
            }
            for(style=1;style<=5;style++)for(int elapsed:new int[]{1,49,100,250,449}){
                create(1440,3168,0);FrameMotion ours=new FrameMotion(1440,3168,0,false,PhotoStyle.get(style));Animator expected=transition(0);Animator actual=ours.prepare(1,500);
                seek(expected,elapsed);seek(actual,elapsed);compare(ours,"pre-reverse");expected=transition(1);actual=ours.prepare(0,500);
                for(long t:times){seek(expected,t);seek(actual,t);compare(ours,"reverse="+elapsed+" t="+t);}
            }
            // A fingerprint wake may emit KEYGUARD and then UNLOCK while the
            // same photo is opening. Retargeting must preserve that trajectory.
            for(int elapsed:new int[]{0,50,150,350,500}){
                FrameMotion pair=new FrameMotion(1440,3168,0,true);
                SceneMotion common=new SceneMotion(1440,3168,0,-1,0,6);
                AnimatorSet opening=pair.prepare(1,400);seek(opening,elapsed);
                float[] before=pair.values.clone();pair.change(2,common);
                for(int i=0;i<8;i++)equal(pair.values[i],before[i],"paired retarget continuity "+i);
                Field current=FrameMotion.class.getDeclaredField("animation");current.setAccessible(true);
                if(current.get(pair)!=opening)throw new AssertionError("Fingerprint wake restarted frame expansion");checks++;
                pair.ratios(common);equal(common.values[1],0,"no intermediate lock layer");equal(common.values[58],0,"no three-scene ghost");
                seek(opening,567);pair.ratios(common);equal(common.values[2],1,"direct home endpoint");equal(pair.values[2],2.35f,"full-screen endpoint");
            }
            // Simulate a slow mask decoder while Android advances the animators.
            FrameMotion delayed=new FrameMotion(1440,3168,2,true);SceneMotion common=new SceneMotion(1440,3168,2,-1,0,6);
            AnimatorSet collapse=delayed.prepare(0,400);seek(collapse,50);
            FrameSample pending=new FrameSample();pending.capture(delayed,common,1);
            int maskIndex=pending.mask;byte[] savedEffect=new byte[pending.effect.remaining()],savedBase=new byte[pending.base.remaining()];
            pending.effect.duplicate().get(savedEffect);pending.base.duplicate().get(savedBase);
            seek(collapse,400);pending.capture(delayed,common,2);
            byte[] retainedEffect=new byte[savedEffect.length],retainedBase=new byte[savedBase.length];pending.effect.duplicate().get(retainedEffect);pending.base.duplicate().get(retainedBase);
            if(maskIndex!=pending.mask||!java.util.Arrays.equals(savedEffect,retainedEffect)||!java.util.Arrays.equals(savedBase,retainedBase))throw new AssertionError("Mask wait split geometry/texture sample");checks++;
            pending.clear();pending.capture(delayed,common,3);if(maskIndex==pending.mask)throw new AssertionError("Presented sample did not advance");checks++;
            // Completion belongs to the submitted sample, not a newer animator
            // state reached while the old sample waits on its RGB mask.
            FrameMotion waking=new FrameMotion(1440,3168,0,true);
            SceneMotion wakingCommon=new SceneMotion(1440,3168,0,-1,0,6);
            AnimatorSet wake=waking.prepare(1,400);seek(wake,100);
            FrameSample wakeSample=new FrameSample();wakeSample.capture(waking,wakingCommon,10);
            if(wakeSample.expanded)throw new AssertionError("Intermediate frame reported completion");checks++;
            seek(wake,800);if(!waking.expanded())throw new AssertionError("Missing expanded endpoint");checks++;
            wakeSample.capture(waking,wakingCommon,11);
            if(wakeSample.expanded)throw new AssertionError("Decoder wait acquired newer completion state");checks++;
            wakeSample.clear();wakeSample.capture(waking,wakingCommon,12);
            if(!wakeSample.expanded)throw new AssertionError("Final sample lost completion");checks++;
            AnimatorSet reverse=waking.prepare(0,400);seek(reverse,450);wakeSample.clear();wakeSample.capture(waking,wakingCommon,13);
            if(wakeSample.expanded)throw new AssertionError("AOD return reported lockscreen completion");checks++;
            // A full-screen frame releases the lock clock while a longer
            // texture reveal continues. Keep the actual submitted sample
            // authoritative if decoding stalls across that boundary.
            for(boolean paired:new boolean[]{false,true})for(int lockStyle=0;lockStyle<=3;lockStyle++){
                FrameMotion openingFrame=new FrameMotion(1440,3168,0,paired);
                SceneMotion textured=new SceneMotion(1440,3168,0,-1,lockStyle,6);
                AnimatorSet opening=openingFrame.prepare(1,textured.blendDuration(0));
                FrameSample sample=new FrameSample();
                seek(opening,516);sample.capture(openingFrame,textured,1);
                if(sample.expanded)throw new AssertionError("Clock released before photo expanded");checks++;
                seek(opening,567);
                if(!openingFrame.expanded())throw new AssertionError("Texture delayed lock clock style="+lockStyle);checks++;
                if(lockStyle!=0&&openingFrame.ratios[0]<=0)throw new AssertionError("Fixture lost unfinished texture blend");checks++;
                sample.capture(openingFrame,textured,2);
                if(sample.expanded)throw new AssertionError("Unsubmitted frame released clock");checks++;
                sample.clear();sample.capture(openingFrame,textured,3);
                if(!sample.expanded)throw new AssertionError("Expanded sample still waits for texture");checks++;
                openingFrame.prepare(0,textured.blendDuration(1));
                if(openingFrame.expanded())throw new AssertionError("Sleep reversal released clock");checks++;
            }
            JSONArray frames=new JSONArray();
            for(style=1;style<=5;style++)for(int tr=0;tr<6;tr++){
                FrameMotion f=new FrameMotion(96,192,PAIRS[tr][0],false,PhotoStyle.get(style));SceneMotion base=new SceneMotion(96,192,PAIRS[tr][0],-1,0,6);
                Animator a=f.prepare(PAIRS[tr][1],base.blendDuration(tr));
                for(long t:times){seek(a,t);f.ratios(base);float[] extra=new float[36];f.packet().order(ByteOrder.nativeOrder()).asFloatBuffer().get(extra);base.packet();frames.put(new JSONObject().put("style",style).put("transition",tr).put("time",t).put("properties",new JSONArray(f.values)).put("extra",new JSONArray(extra)).put("base",new JSONArray(base.values)));}
            }
            System.out.println("FRAME_RESULT "+new JSONObject().put("checks",checks).put("max_error",maxError).put("frames",frames));System.exit(0);
        }catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
}
