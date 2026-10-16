package org.aliveclean;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import de.robv.android.xposed.*;
import java.lang.reflect.Method;

/** Let the native wake remove Display-off before collecting the authenticated exit transition. */
final class ColorOsUnlockHandoff {
    private static final long MAX_WAIT_MS=200;
    private static final ThreadLocal<AnimationScope> authenticatedAnimation=new ThreadLocal<>();
    private final ClassLoader loader;
    private final ThreadLocal<Integer> authenticatedUser=new ThreadLocal<>();
    private final ThreadLocal<Boolean> fingerprintResume=new ThreadLocal<>();
    private final ThreadLocal<Pending> scheduling=new ThreadLocal<>();
    private final Object lock=new Object();
    private volatile Object mediator,binder;
    private Handler handler,workHandler;
    private Method scheduleWork;
    private Class<?> workClass;
    private Runnable releaseBoost;
    private Pending pending;
    private Pending preparing;
    private Pending replaying;
    private Pending animationToken;
    private long generation,wakeSerial,held,replayed,timedOut,cancelled,duplicates,lastWaitMs,animationFlagsRestored;
    private String lastRelease="none";
    private boolean installed,mediatorReady,serviceReady;

    private static final class Pending {
        final Object mediator,receiver;
        final int user,show;
        final long generation,wakeSerial,started;
        Runnable timeout,work;
        boolean queued,boostOwned,animationClaimed;
        int flags;
        Pending(Object mediator,Object receiver,int user,int show,long generation,long wakeSerial){
            this.mediator=mediator;this.receiver=receiver;this.user=user;this.show=show;
            this.generation=generation;this.wakeSerial=wakeSerial;started=SystemClock.uptimeMillis();
        }
    }

    private static final class AnimationScope {
        final ColorOsUnlockHandoff owner;
        final Pending request;
        AnimationScope(ColorOsUnlockHandoff owner,Pending request){this.owner=owner;this.request=request;}
    }

    static boolean inAuthenticatedAnimation(){
        AnimationScope scope=authenticatedAnimation.get();
        if(scope==null)return false;
        synchronized(scope.owner.lock){
            return scope.owner.animationToken==scope.request&&scope.request.generation==scope.owner.generation;
        }
    }

    ColorOsUnlockHandoff(ClassLoader loader){this.loader=loader;}
    void install(){
        try{
            Class<?> nativeMediator=type("com.android.systemui.keyguard.KeyguardViewMediator");
            Class<?> animationController=type("com.android.systemui.keyguard.KeyguardUnlockAnimationController");
            Class<?> service=type("com.android.systemui.keyguard.KeyguardService");
            Class<?> auth=type("com.oplus.keyguard.OplusKeyguardUpdateMonitorExImpl");
            Class<?> rapid=type("com.oplus.keyguard.utils.KeyguardRapidUnlockUtils");
            Class<?> workQueue=type("com.oplusos.keyguard.utils.KeyguardThreadUtil");
            workClass=type("com.android.systemui.keyguard.KeyguardViewMediator$$ExternalSyntheticLambda2");
            workClass.getDeclaredField("$r8$classId");workClass.getDeclaredField("f$0");workClass.getDeclaredField("f$1");
            scheduleWork=workQueue.getDeclaredMethod("runOnWorkThread",Runnable.class);
            workHandler=(Handler)XposedHelpers.callStaticMethod(workQueue,"getWorkHandler");
            if(workHandler==null)throw new IllegalStateException("No native work handler");
            Class<?> scheduler=type("com.oplusos.systemui.common.sched.OplusSysUiSchedMgr");
            Class<?> eventType=type(scheduler.getName()+"$UafSceneEvent");
            Object event=XposedHelpers.getStaticObjectField(eventType,"KEYGUARD_GOING_AWAY");
            Object manager=XposedHelpers.callStaticMethod(scheduler,"getInstance");
            scheduler.getDeclaredMethod("releaseSysResource",eventType);
            releaseBoost=()->XposedHelpers.callMethod(manager,"releaseSysResource",event);
            XposedBridge.hookMethod(scheduleWork,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    Pending request=scheduling.get();Object work=p.args[0];
                    if(request==null||!workClass.isInstance(work))return;
                    try{
                        if(integer(work,"$r8$classId")==4&&XposedHelpers.getObjectField(work,"f$0")==request.receiver
                                &&defer(request,(Runnable)work))p.setResult(null);
                    }catch(Throwable error){failure("work capture",error);}
                }
            });
            // Both scopes must be present: successful native FP authentication AND
            // the resume call reached after the vendor's user/family/security gates.
            if(XposedBridge.hookAllMethods(auth,"onFpAuthenticationSucceeded",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    p.setObjectExtra("openalivePreviousUser",authenticatedUser.get());
                    authenticatedUser.remove();
                    if(!ColorOsBridge.ownsWallpaper()||p.args.length!=3)return;
                    try{authenticatedUser.set((Integer)XposedHelpers.callMethod(p.args[0],"getUserId"));}
                    catch(Throwable error){failure("authentication scope",error);}
                }
                @Override protected void afterHookedMethod(MethodHookParam p){
                    Integer previous=(Integer)p.getObjectExtra("openalivePreviousUser");
                    if(previous==null)authenticatedUser.remove();else authenticatedUser.set(previous);
                }
            }).isEmpty())throw new NoSuchMethodException("onFpAuthenticationSucceeded");
            XposedHelpers.findAndHookMethod(rapid,"resumeActivityForBiometricsUnlock",Context.class,boolean.class,int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    p.setObjectExtra("openalivePreviousResume",fingerprintResume.get());
                    fingerprintResume.set(authenticatedUser.get()!=null&&(Boolean)p.args[1]&&(Integer)p.args[2]==1);
                }
                @Override protected void afterHookedMethod(MethodHookParam p){
                    Boolean previous=(Boolean)p.getObjectExtra("openalivePreviousResume");
                    if(previous==null)fingerprintResume.remove();else fingerprintResume.set(previous);
                }
            });
            XposedBridge.hookAllConstructors(nativeMediator,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable())attachMediator(p.thisObject);}
            });
            XposedBridge.hookAllConstructors(service,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable())attachService(p.thisObject);}
            });
            XC_MethodHook invalidate=new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){if(p.thisObject==mediator)cancel(p.method.getName());}
            };
            XposedHelpers.findAndHookMethod(nativeMediator,"showKeyguard",android.os.Bundle.class,invalidate);
            XposedHelpers.findAndHookMethod(nativeMediator,"handleBeforeUserSwitching",int.class,Runnable.class,invalidate);
            installAnimationHooks(animationController,nativeMediator);
            XposedHelpers.findAndHookMethod(nativeMediator,"dump",java.io.PrintWriter.class,String[].class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){
                    synchronized(lock){((java.io.PrintWriter)p.args[0]).println("OpenAlive unlock handoff: stage=window-submit installed="+(installed&&mediatorReady&&serviceReady)
                            +" waiting="+(pending!=null)+" held="+held+" replayed="+replayed+" timeouts="+timedOut
                            +" cancelled="+cancelled+" duplicates="+duplicates+" animationFlagsRestored="+animationFlagsRestored
                            +" last="+lastRelease+" waitMs="+lastWaitMs);}
                }
            });
            synchronized(lock){installed=true;}
        }catch(Throwable error){failure("installation",error);}
    }

    private void installAnimationHooks(Class<?> controller,Class<?> nativeMediator)throws ReflectiveOperationException{
        controller.getDeclaredField("keyguardViewMediator");
        if(XposedBridge.hookAllMethods(controller,"notifyStartSurfaceBehindRemoteAnimation",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                p.setObjectExtra("openalivePreviousAnimation",authenticatedAnimation.get());
                authenticatedAnimation.remove();
                try{
                    Pending request=claimAnimation(p.thisObject);
                    if(request!=null){
                        p.setObjectExtra("openaliveAnimationRequest",request);
                        authenticatedAnimation.set(new AnimationScope(ColorOsUnlockHandoff.this,request));
                    }
                }catch(Throwable error){failure("animation scope",error);}
            }
            @Override protected void afterHookedMethod(MethodHookParam p){
                Pending request=(Pending)p.getObjectExtra("openaliveAnimationRequest");
                synchronized(lock){if(request!=null&&animationToken==request)animationToken=null;}
                AnimationScope previous=(AnimationScope)p.getObjectExtra("openalivePreviousAnimation");
                if(previous==null)authenticatedAnimation.remove();else authenticatedAnimation.set(previous);
            }
        }).isEmpty())throw new NoSuchMethodException("notifyStartSurfaceBehindRemoteAnimation");
        XC_MethodHook finish=new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                if(p.thisObject==mediator)synchronized(lock){animationToken=null;}
            }
        };
        XposedHelpers.findAndHookMethod(nativeMediator,"cancelKeyguardExitAnimation",finish);
        XposedHelpers.findAndHookMethod(nativeMediator,"finishSurfaceBehindRemoteAnimation",boolean.class,finish);
    }

    private Pending claimAnimation(Object controller){
        // Dagger Lazy is not the mediator itself. Match its resolved instance before
        // consuming the token so an unrelated callback cannot steal this request.
        Object target=XposedHelpers.callMethod(XposedHelpers.getObjectField(controller,"keyguardViewMediator"),"get");
        synchronized(target){synchronized(lock){
            Pending request=animationToken;
            if(request==null||request.mediator!=target||request.animationClaimed)return null;
            if(request.generation!=generation||user(target)!=request.user
                    ||integer(target,"mLastShowRequest")!=request.show||bool(target,"mGoingToSleep")
                    ||!ColorOsBridge.ownsWallpaper()){
                animationToken=null;return null;
            }
            request.animationClaimed=true;return request;
        }}
    }

    private void attachMediator(Object target){
        try{
            synchronized(lock){if(mediator!=null)return;}
            Object task=XposedHelpers.getObjectField(target,"mKeyguardGoingAwayRunnable");
            Handler queue=(Handler)XposedHelpers.getObjectField(target,"mHandler");
            if(queue==null)throw new IllegalStateException("No mediator handler");
            Method run=task.getClass().getDeclaredMethod("run");
            // Resolve all required fields now; an unsupported ROM keeps native behavior.
            user(target);integer(target,"mLastShowRequest");bool(target,"mGoingToSleep");
            bool(target,"mShowing");bool(target,"mDeviceInteractive");
            synchronized(lock){mediator=target;handler=queue;}
            XposedBridge.hookMethod(run,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(p.thisObject!=task)return;
                    synchronized(target){
                        Pending request;
                        try{
                            // A duplicate must stop before native boost acquisition/UI setup.
                            if(duplicate(task)){p.setResult(null);return;}
                            request=prepare(target,task);if(request==null)return;
                        }catch(Throwable error){failure("prepare",error);return;}
                        Pending previous=scheduling.get();scheduling.set(request);
                        // Run immediately on the caller's original thread, under the
                        // same lock as sleep/show cancellation. Only the nested WMS
                        // work scheduler is held; native UI setup is never postponed.
                        try{p.setResult(XposedBridge.invokeOriginalMethod(p.method,p.thisObject,p.args));}
                        catch(Throwable error){p.setThrowable(error);}
                        finally{
                            synchronized(lock){if(preparing==request)preparing=null;}
                            if(previous==null)scheduling.remove();else scheduling.set(previous);
                        }
                    }
                }
            });
            synchronized(lock){mediatorReady=true;}
        }catch(Throwable error){failure("mediator",error);}
    }

    private void attachService(Object service){
        try{
            Object target=XposedHelpers.getObjectField(service,"mBinder");
            synchronized(lock){if(binder!=null)return;}
            for(String name:new String[]{"onScreenTurnedOn","onFinishedWakingUp"}){
                XposedHelpers.findAndHookMethod(target.getClass(),name,new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam p){
                        if(p.thisObject==target&&!p.hasThrowable())releaseSoon(p.method.getName());
                    }
                });
            }
            XposedHelpers.findAndHookMethod(target.getClass(),"onStartedGoingToSleep",int.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){if(p.thisObject==target)cancel("sleep");}
            });
            synchronized(lock){binder=target;serviceReady=true;}
        }catch(Throwable error){failure("wake callbacks",error);}
    }

    private boolean duplicate(Object receiver){
        synchronized(lock){
            if(preparing!=null&&preparing.receiver==receiver||pending!=null&&pending.receiver==receiver
                    ||replaying!=null&&replaying.receiver==receiver){duplicates++;return true;}
            return false;
        }
    }
    private Pending prepare(Object target,Object receiver){
        synchronized(lock){
            Integer authenticated=authenticatedUser.get();
            if(!installed||!mediatorReady||!serviceReady||!ColorOsBridge.ownsWallpaper()||authenticated==null
                    ||!Boolean.TRUE.equals(fingerprintResume.get())||!bool(target,"mShowing")
                    ||bool(target,"mDeviceInteractive")||bool(target,"mGoingToSleep")||user(target)!=authenticated)return null;
            // The native successful-FP resume branch already excludes bouncer actions.
            // OFF may have panoramicFp=false; that is not an authentication failure.
            if(preparing!=null||pending!=null||replaying!=null)return null;
            preparing=new Pending(target,receiver,authenticated,integer(target,"mLastShowRequest"),generation,wakeSerial);
            return preparing;
        }
    }
    private boolean defer(Pending next,Runnable work){
        synchronized(lock){
            if(next.generation!=generation){next.boostOwned=true;cleanup(next);cancelled++;return true;}
            if(pending!=null)return false;
            next.work=work;
            next.flags=integer(work,"f$1");
            next.timeout=()->release(next,"timeout");pending=next;
            try{if(!handler.postDelayed(next.timeout,MAX_WAIT_MS)){pending=null;return false;}}
            catch(RuntimeException error){if(pending==next)pending=null;throw error;}
            try{
                // This exact native work uses bit 2 only for wake-and-unlock with
                // an unrecognized ambient wallpaper. Preserve every other flag and
                // all earlier native bouncer/side-fingerprint/animation-level gates.
                if((next.flags&2)!=0){XposedHelpers.setIntField(work,"f$1",next.flags&~2);animationFlagsRestored++;}
            }catch(Throwable error){handler.removeCallbacks(next.timeout);pending=null;failure("animation flags",error);return false;}
            preparing=null;
            next.boostOwned=true;
            if(next.wakeSerial!=wakeSerial)try{handler.post(()->release(next,"wake-already-ready"));}
            catch(RuntimeException error){failure("ready queue",error);} // The bounded timeout still owns this request.
            held++;return true;
        }
    }

    private void releaseSoon(String reason){
        synchronized(lock){
            wakeSerial++;
            Pending next=pending;
            if(next!=null)try{handler.post(()->release(next,reason));}catch(RuntimeException error){failure("wake queue",error);}
        }
    }
    private void release(Pending request,String reason){
        synchronized(request.mediator){
            synchronized(lock){
                if(pending!=request||request.queued)return;
                request.queued=true;handler.removeCallbacks(request.timeout);
                lastWaitMs=SystemClock.uptimeMillis()-request.started;lastRelease=reason;
                if("timeout".equals(reason))timedOut++;
            }
            // Only the original WMS work is delayed. Native going-away UI setup
            // and its captured flags have already run at their original time.
            try{XposedBridge.invokeOriginalMethod(scheduleWork,null,new Object[]{(Runnable)()->execute(request)});}
            catch(Throwable error){
                failure("work schedule",error);
                // The native scheduler uses this same queue. A reflection failure
                // must not repeat 200ms timers or run WMS work on the UI thread.
                synchronized(lock){
                    if(pending==request)try{
                        if(!workHandler.post(()->execute(request)))throw new IllegalStateException("Native work queue closed");
                    }catch(Throwable unavailable){
                        pending=null;cancelled++;cleanup(request);failure("work queue unavailable",unavailable);
                    }
                }
            }
        }
    }
    private void execute(Pending request){
        synchronized(request.mediator){
            synchronized(lock){
                if(pending!=request)return;
                boolean valid=true,validated=false;
                try{valid=request.generation==generation&&user(request.mediator)==request.user
                        &&integer(request.mediator,"mLastShowRequest")==request.show
                        &&!bool(request.mediator,"mGoingToSleep")&&bool(request.mediator,"mShowing");validated=true;}
                catch(Throwable error){failure("validate",error);} // Native work retains its own user validation.
                pending=null;
                if(!valid){cancelled++;lastRelease="stale-"+lastRelease;cleanup(request);return;}
                replaying=request;animationToken=validated?request:null;request.boostOwned=false;replayed++;
            }
            boolean submitted=false;
            try{request.work.run();submitted=true;} // Already on the original KeyguardWorkerThread.
            finally{synchronized(lock){
                if(replaying==request)replaying=null;
                if(!submitted&&animationToken==request)animationToken=null;
            }}
        }
    }
    private void cleanup(Pending request){
        if(!request.boostOwned)return;request.boostOwned=false;
        try{releaseBoost.run();}catch(Throwable error){failure("boost cleanup",error);}
    }
    private void cancel(String reason){
        Object target;synchronized(lock){target=mediator;}
        if(target==null)return;
        // Serialize cancellation with the validated native replay, in the same
        // mediator -> local-lock order used above. Never hold local-lock while waiting.
        synchronized(target){synchronized(lock){
            generation++;animationToken=null;if(pending!=null){Pending old=pending;handler.removeCallbacks(old.timeout);pending=null;cancelled++;lastRelease=reason;cleanup(old);}
        }}
    }
    private int user(Object target){return (Integer)XposedHelpers.callMethod(XposedHelpers.getObjectField(target,"mSelectedUserInteractor"),"getSelectedUserId");}
    private static boolean bool(Object target,String field){return XposedHelpers.getBooleanField(target,field);}
    private static int integer(Object target,String field){return XposedHelpers.getIntField(target,field);}
    private Class<?> type(String name){return XposedHelpers.findClass(name,loader);}
    private static void failure(String where,Throwable error){ColorOsBridge.failure("unlock handoff "+where,error);}
}
