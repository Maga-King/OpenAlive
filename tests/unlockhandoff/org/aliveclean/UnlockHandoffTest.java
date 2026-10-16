package org.aliveclean;
import android.os.Handler;
import android.os.SystemClock;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.*;

/** Exercise production state transitions; native UI/work bodies and Android queues are modeled. */
public final class UnlockHandoffTest {
    private static int checks;
    public static final class User {int id;public int getSelectedUserId(){return id;}}
    public static final class Task implements Runnable {Runnable body;public void run(){if(body!=null)body.run();}}
    public static final class IncompleteBinder {public void onScreenTurnedOn(){} public void onFinishedWakingUp(){}}
    public static final class IncompleteService {public final IncompleteBinder mBinder=new IncompleteBinder();}
    public static final class Mediator {
        public final User mSelectedUserInteractor=new User();
        public final Task mKeyguardGoingAwayRunnable=new Task();
        public final Handler mHandler=new Handler();
        public int mLastShowRequest=1;
        public boolean mGoingToSleep,mDeviceInteractive;
        public boolean mShowing=true;
        // Intentionally no panoramic-AOD field: a real OFF wake must be admitted.
        public void cancelKeyguardExitAnimation(){}
        public void finishSurfaceBehindRemoteAnimation(boolean cancelled){}
    }
    public static final class LazyMediator {
        final Mediator target;
        LazyMediator(Mediator target){this.target=target;}
        public Mediator get(){return target;}
    }
    public static final class AnimationController {
        final LazyMediator keyguardViewMediator;
        Runnable body;
        AnimationController(Mediator mediator){keyguardViewMediator=new LazyMediator(mediator);}
        public void notifyStartSurfaceBehindRemoteAnimation(){if(body!=null)body.run();}
    }
    public static final class Worker {
        static Handler queue;
        static boolean fail;
        public static void schedule(Runnable task){if(fail)throw new IllegalStateException("scheduler reflection failure");queue.post(task);}
    }
    public static final class Work implements Runnable {
        public int f$1;
        final Fixture fixture;
        Work(Fixture fixture,int flags){this.fixture=fixture;f$1=flags;}
        public void run(){
            try{fixture.wmsCalls++;fixture.executedFlags=f$1;if(fixture.duringWork!=null)fixture.duringWork.run();}
            finally{fixture.nativeReleases++;}
        }
    }
    private static final class Fixture {
        final ColorOsUnlockHandoff gate=new ColorOsUnlockHandoff(UnlockHandoffTest.class.getClassLoader());
        final Mediator mediator=new Mediator();
        final Handler worker=new Handler();
        int acquisitions,uiCalls,wmsCalls,nativeReleases,cancelReleases,executedFlags;
        Work lastWork;
        Runnable duringWork;
        Fixture()throws Exception{
            SystemClock.now=1000;XposedBridge.invocations=0;XposedBridge.beforeInvoke=null;XposedBridge.hooks.clear();ColorOsBridge.selected=true;
            Worker.queue=worker;Worker.fail=false;
            set(gate,"mediator",mediator);set(gate,"handler",mediator.mHandler);set(gate,"binder",new Object());
            set(gate,"workHandler",worker);set(gate,"scheduleWork",Worker.class.getDeclaredMethod("schedule",Runnable.class));
            set(gate,"releaseBoost",(Runnable)()->cancelReleases++);
            for(String name:new String[]{"installed","mediatorReady","serviceReady"})set(gate,name,true);
        }
        @SuppressWarnings("unchecked") void auth(boolean success,boolean fp)throws Exception{
            ((ThreadLocal<Integer>)get(gate,"authenticatedUser")).set(success?0:null);
            ((ThreadLocal<Boolean>)get(gate,"fingerprintResume")).set(fp);
        }
        Object prepare()throws Exception{return call(gate,"prepare",mediator,mediator.mKeyguardGoingAwayRunnable);}
        boolean duplicate()throws Exception{return (Boolean)call(gate,"duplicate",mediator.mKeyguardGoingAwayRunnable);}
        boolean outer(int flags)throws Exception{
            if(duplicate())return true;
            Object request=prepare();
            // Native outer run acquires its boost and submits UI immediately.
            acquisitions++;uiCalls++;lastWork=new Work(this,flags);
            boolean held=false;
            try{if(request!=null)held=(Boolean)call(gate,"defer",request,lastWork);}
            catch(RuntimeException expected){/* The production scheduler hook fails open. */}
            finally{if(get(gate,"preparing")==request)set(gate,"preparing",null);}
            if(!held)Worker.schedule(lastWork);
            return held;
        }
        void ready()throws Exception{call(gate,"releaseSoon","onScreenTurnedOn");}
        void cancel(String why)throws Exception{call(gate,"cancel",why);}
        void drain(){mediator.mHandler.advance(500);worker.runReady();}
        void balanced(String why){check(acquisitions==nativeReleases+cancelReleases,"boost balance: "+why);}
        void installOuterHook()throws Exception{
            set(gate,"mediator",null);set(gate,"mediatorReady",false);call(gate,"attachMediator",mediator);
        }
        void invokeOuterHook()throws Throwable{XposedBridge.invokeHookedMethod(Task.class.getDeclaredMethod("run"),mediator.mKeyguardGoingAwayRunnable);}
        AnimationController installAnimationHooks()throws Exception{
            call(gate,"installAnimationHooks",AnimationController.class,Mediator.class);
            return new AnimationController(mediator);
        }
    }
    private static void animate(AnimationController controller)throws Throwable{
        XposedBridge.invokeHookedMethod(AnimationController.class.getDeclaredMethod("notifyStartSurfaceBehindRemoteAnimation"),controller);
    }
    private static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static Object get(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Object call(Object object,String name,Object... args)throws Exception{
        for(Method m:object.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);try{return m.invoke(object,args);}catch(InvocationTargetException error){throw new RuntimeException(error.getCause());}}
        throw new NoSuchMethodException(name);
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Throwable{
        Fixture f=new Fixture();
        check(f.prepare()==null,"outside authentication scope passes through");
        f.auth(true,false);check(f.prepare()==null,"success without fingerprint resume passes through");
        f.auth(false,true);check(f.prepare()==null,"resume without successful authentication passes through");
        f.auth(true,true);ColorOsBridge.selected=false;check(f.prepare()==null,"unowned wallpaper passes through");ColorOsBridge.selected=true;
        f.mediator.mSelectedUserInteractor.id=1;check(f.prepare()==null,"different authenticated user passes through");f.mediator.mSelectedUserInteractor.id=0;
        f.mediator.mDeviceInteractive=true;check(f.prepare()==null,"interactive unlock passes through");f.mediator.mDeviceInteractive=false;
        f.mediator.mGoingToSleep=true;check(f.prepare()==null,"sleeping request passes through");f.mediator.mGoingToSleep=false;
        f.mediator.mShowing=false;check(f.prepare()==null,"hidden keyguard passes through");f.mediator.mShowing=true;
        check(f.outer(0x113),"OFF wake without panoramic flag is held");
        check(f.uiCalls==1&&f.acquisitions==1,"native UI and boost run immediately");
        check(f.wmsCalls==0&&f.worker.queued()==0,"only WMS submission is deferred");
        check(f.lastWork.f$1==0x111,"only NO_ANIMATION bit is cleared");
        f.auth(false,false);check(f.outer(0x113),"retry outside auth is coalesced");
        check(f.uiCalls==1&&f.acquisitions==1,"duplicate stops before native boost and UI");
        f.ready();f.ready();check(f.wmsCalls==0,"wake callback does not run WMS inline");
        f.mediator.mHandler.runReady();check(f.worker.queued()==1&&f.wmsCalls==0,"mediator schedules exactly one native worker");
        f.worker.runReady();f.drain();check(f.wmsCalls==1&&f.nativeReleases==1&&f.cancelReleases==0,"screen-ready executes original native work once");f.balanced("wake");

        for(int flags:new int[]{0,1,2,3,4,8,16,0x102,0xffff}){
            f=new Fixture();f.auth(true,true);f.outer(flags);f.drain();
            check(f.executedFlags==(flags&~2),"preserves other flags "+flags);f.balanced("flags "+flags);
        }
        f=new Fixture();f.auth(true,true);f.outer(2);
        f.mediator.mHandler.advance(199);check(f.worker.queued()==0,"no timeout before 200ms");
        f.mediator.mHandler.advance(1);check(f.worker.queued()==1&&f.wmsCalls==0,"200ms timeout submits to native worker");
        f.ready();f.mediator.mHandler.runReady();f.worker.runReady();f.drain();check(f.wmsCalls==1,"timeout and wake replay once");f.balanced("timeout");

        for(String reason:new String[]{"sleep","showKeyguard","handleBeforeUserSwitching"})for(boolean workerQueued:new boolean[]{false,true}){
            f=new Fixture();f.auth(true,true);f.outer(2);f.ready();if(workerQueued)f.mediator.mHandler.runReady();
            f.cancel(reason);f.cancel(reason);f.drain();
            check(f.wmsCalls==0&&f.cancelReleases==1,"cancel "+reason+" after worker queued="+workerQueued);f.balanced(reason);
        }
        for(int stale=0;stale<4;stale++){
            f=new Fixture();f.auth(true,true);f.outer(2);f.ready();f.mediator.mHandler.runReady();
            if(stale==0)f.mediator.mSelectedUserInteractor.id=1;
            if(stale==1)f.mediator.mLastShowRequest++;
            if(stale==2)f.mediator.mGoingToSleep=true;
            if(stale==3)f.mediator.mShowing=false;
            f.worker.runReady();f.drain();check(f.wmsCalls==0&&f.cancelReleases==1,"worker revalidates stale state "+stale);f.balanced("stale "+stale);
        }
        for(String gate:new String[]{"installed","mediatorReady","serviceReady"}){
            f=new Fixture();f.auth(true,true);set(f.gate,gate,false);check(!f.outer(2),"incomplete "+gate+" fails open");f.worker.runReady();check(f.executedFlags==2,"partial installation preserves native flags");f.balanced(gate);
        }
        f=new Fixture();f.auth(true,true);set(f.gate,"serviceReady",false);set(f.gate,"binder",null);call(f.gate,"attachService",new IncompleteService());
        check(!f.outer(2),"missing sleep callback fails open");f.worker.runReady();f.balanced("partial service");
        for(boolean throwsError:new boolean[]{false,true}){
            f=new Fixture();f.auth(true,true);f.mediator.mHandler.accepting=throwsError;f.mediator.mHandler.throwOnPost=throwsError;
            check(!f.outer(2),"mediator timeout queue failure fails open "+throwsError);check(get(f.gate,"pending")==null&&get(f.gate,"preparing")==null,"queue failure leaves no token");
            f.worker.runReady();check(f.executedFlags==2,"queue failure leaves native flags intact");f.balanced("queue rejection");
        }
        f=new Fixture();f.auth(true,true);Object preparing=f.prepare();check(f.duplicate(),"preparing token blocks reentrant outer before work capture");
        f.cancel("sleep");f.acquisitions++;f.uiCalls++;f.lastWork=new Work(f,2);
        check((Boolean)call(f.gate,"defer",preparing,f.lastWork),"capture after sleep discards stale work");set(f.gate,"preparing",null);
        check(f.cancelReleases==1&&f.worker.queued()==0,"stale preparation releases boost without WMS");f.balanced("stale prepare");

        f=new Fixture();f.auth(true,true);preparing=f.prepare();f.ready();f.acquisitions++;f.uiCalls++;f.lastWork=new Work(f,2);
        check((Boolean)call(f.gate,"defer",preparing,f.lastWork),"capture after wake ready succeeds");set(f.gate,"preparing",null);
        f.mediator.mHandler.runReady();check(f.worker.queued()==1,"wake between prepare and capture releases immediately");f.worker.runReady();f.balanced("ready race");
        f=new Fixture();f.auth(true,true);f.outer(2);final Fixture replay=f;
        f.duringWork=()->{try{check(replay.outer(2),"retry during work coalesces");}catch(Exception error){throw new RuntimeException(error);}};
        f.drain();check(f.wmsCalls==1&&f.acquisitions==1,"replay retry cannot reacquire boost");f.balanced("retry");

        for(int unavailable=0;unavailable<3;unavailable++){
            f=new Fixture();f.auth(true,true);f.outer(2);Worker.fail=true;
            if(unavailable==1)f.worker.accepting=false;if(unavailable==2)f.worker.throwOnPost=true;
            f.ready();f.drain();
            check(f.wmsCalls==(unavailable==0?1:0),"scheduler fallback result "+unavailable);
            check(get(f.gate,"pending")==null,"scheduler fallback leaves no pending token "+unavailable);f.balanced("scheduler fallback "+unavailable);
        }
        for(boolean throwsError:new boolean[]{false,true}){
            f=new Fixture();f.auth(true,true);f.outer(2);f.mediator.mHandler.accepting=throwsError;f.mediator.mHandler.throwOnPost=throwsError;
            f.ready();f.mediator.mHandler.accepting=true;f.mediator.mHandler.throwOnPost=false;f.drain();
            check(f.wmsCalls==1,"wake queue failure retains bounded timeout "+throwsError);f.balanced("wake queue");
        }
        f=new Fixture();f.auth(true,true);f.installOuterHook();final Fixture hooked=f;
        f.mediator.mKeyguardGoingAwayRunnable.body=()->{
            try{
                check(Thread.holdsLock(hooked.mediator),"actual outer hook serializes native body with cancellation");
                Object request=((ThreadLocal<?>)get(hooked.gate,"scheduling")).get();
                check(request!=null,"actual outer hook provides scoped capture token");
                hooked.acquisitions++;hooked.uiCalls++;hooked.lastWork=new Work(hooked,2);
                check((Boolean)call(hooked.gate,"defer",request,hooked.lastWork),"actual outer hook captures native work");
            }catch(Exception error){throw new RuntimeException(error);}
        };
        f.invokeOuterHook();check(f.uiCalls==1&&f.worker.queued()==0,"actual outer hook runs native UI immediately and holds only work");
        check(get(f.gate,"preparing")==null&&((ThreadLocal<?>)get(f.gate,"scheduling")).get()==null,"actual outer hook restores preparation and scope");
        f.auth(false,false);f.invokeOuterHook();check(f.uiCalls==1&&f.acquisitions==1,"actual hook duplicate skips native body");f.drain();f.balanced("actual outer hook");
        f=new Fixture();f.auth(true,true);f.installOuterHook();f.mediator.mKeyguardGoingAwayRunnable.body=()->{throw new IllegalStateException("native failure");};
        try{f.invokeOuterHook();throw new AssertionError("native failure swallowed");}catch(IllegalStateException expected){check(expected.getMessage().equals("native failure"),"actual outer hook preserves native failure");}
        check(get(f.gate,"preparing")==null&&((ThreadLocal<?>)get(f.gate,"scheduling")).get()==null,"actual outer hook restores scopes after native failure");

        f=new Fixture();AnimationController controller=f.installAnimationHooks();f.auth(true,true);
        controller.body=()->check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"authentication alone does not grant animation scope");
        animate(controller);f.outer(2);animate(controller);
        f.drain();f.auth(false,false);
        check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"submitted work does not create an ambient time window");
        controller.body=()->check(ColorOsUnlockHandoff.inAuthenticatedAnimation(),"matching callback inherits validated authentication across threads");
        final AnimationController crossThreadController=controller;
        final Throwable[] crossThreadError={null};
        Thread callback=new Thread(()->{try{animate(crossThreadController);check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"callback thread scope is restored");}catch(Throwable error){crossThreadError[0]=error;}});
        callback.start();callback.join();if(crossThreadError[0]!=null)throw crossThreadError[0];
        check(get(f.gate,"animationToken")==null,"animation consumes its request once");
        controller.body=()->check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"second callback cannot reuse authentication");animate(controller);

        f=new Fixture();controller=f.installAnimationHooks();f.auth(true,true);f.outer(2);f.drain();
        AnimationController unrelated=new AnimationController(new Mediator());
        unrelated.body=()->check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"wrong mediator is never authenticated");animate(unrelated);
        check(get(f.gate,"animationToken")!=null,"wrong mediator does not consume matching transition");
        controller.body=()->{
            check(ColorOsUnlockHandoff.inAuthenticatedAnimation(),"outer matching animation has scope");
            try{animate(unrelated);}catch(Throwable error){throw new RuntimeException(error);}
            check(ColorOsUnlockHandoff.inAuthenticatedAnimation(),"nested unrelated callback restores outer scope");
        };animate(controller);
        check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"nested callbacks leave no scope");

        for(int stale=0;stale<5;stale++){
            f=new Fixture();controller=f.installAnimationHooks();f.auth(true,true);f.outer(2);f.drain();
            if(stale==0)f.mediator.mSelectedUserInteractor.id++;
            if(stale==1)f.mediator.mLastShowRequest++;
            if(stale==2)f.mediator.mGoingToSleep=true;
            if(stale==3)ColorOsBridge.selected=false;
            if(stale==4)set(f.gate,"generation",1L);
            controller.body=()->check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"callback revalidates current transition provenance");animate(controller);
            check(get(f.gate,"animationToken")==null,"stale callback discards transition token "+stale);
        }
        for(String reason:new String[]{"sleep","showKeyguard","handleBeforeUserSwitching"}){
            f=new Fixture();controller=f.installAnimationHooks();f.auth(true,true);f.outer(2);f.drain();f.cancel(reason);
            controller.body=()->check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"lifecycle invalidation rejects callback");animate(controller);
            check(get(f.gate,"animationToken")==null,"cancel clears submitted transition "+reason);
        }
        for(String finish:new String[]{"cancelKeyguardExitAnimation","finishSurfaceBehindRemoteAnimation"}){
            f=new Fixture();controller=f.installAnimationHooks();f.auth(true,true);f.outer(2);f.drain();
            if(finish.equals("cancelKeyguardExitAnimation"))XposedBridge.invokeHookedMethod(Mediator.class.getDeclaredMethod(finish),f.mediator);
            else XposedBridge.invokeHookedMethod(Mediator.class.getDeclaredMethod(finish,boolean.class),f.mediator,false);
            controller.body=()->check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"native finish cancels pending authentication scope");animate(controller);
            check(get(f.gate,"animationToken")==null,"native finish clears submitted transition "+finish);
        }
        f=new Fixture();controller=f.installAnimationHooks();f.auth(true,true);f.outer(2);f.drain();
        controller.body=()->{check(ColorOsUnlockHandoff.inAuthenticatedAnimation(),"throwing native callback initially receives scope");throw new IllegalStateException("animation failure");};
        try{animate(controller);throw new AssertionError("callback failure swallowed");}catch(IllegalStateException expected){check(expected.getMessage().equals("animation failure"),"callback failure preserved");}
        check(!ColorOsUnlockHandoff.inAuthenticatedAnimation()&&get(f.gate,"animationToken")==null,"throwing callback restores scope and clears token");
        f=new Fixture();f.auth(true,true);f.outer(2);f.duringWork=()->{throw new IllegalStateException("work failure");};
        try{f.drain();throw new AssertionError("work failure swallowed");}catch(IllegalStateException expected){check(expected.getMessage().equals("work failure"),"native work failure preserved");}
        check(get(f.gate,"animationToken")==null,"failed native submission cannot authenticate later animation");f.balanced("failed work");
        f=new Fixture();controller=f.installAnimationHooks();f.auth(true,true);f.outer(2);f.drain();final Fixture cancelDuringAnimation=f;
        controller.body=()->{try{check(ColorOsUnlockHandoff.inAuthenticatedAnimation(),"scope initially active before sleep");cancelDuringAnimation.cancel("sleep");check(!ColorOsUnlockHandoff.inAuthenticatedAnimation(),"sleep invalidates even an active synchronous scope");}catch(Exception error){throw new RuntimeException(error);}};
        animate(controller);
        System.out.println("Unlock handoff: "+checks+" host state-machine checks passed (Android hook wiring/timing require device validation)");
    }
}
