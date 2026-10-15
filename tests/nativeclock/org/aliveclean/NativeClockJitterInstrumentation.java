package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Real-window scene interruption regression; never selects or persists a system clock. */
public final class NativeClockJitterInstrumentation extends Instrumentation {
    private static final String TRACE_FILE="native-clock-jitter.txt";
    private static final long TIMEOUT_MS=5000;
    private final StringBuilder trace=new StringBuilder();
    private final StringBuilder summary=new StringBuilder();

    @Override public void onCreate(Bundle args){super.onCreate(args);start();}

    @Override public void onStart(){
        Bundle result=new Bundle();ClockTestActivity activity=null;Harness current=null;Throwable failed=null;
        int frames=0,layouts=0;
        try{
            Context app=getTargetContext();
            Bundle launch=new Bundle();launch.putString("stream","test activity launch\n");sendStatus(1,launch);
            activity=(ClockTestActivity)startActivitySync(new Intent(app,ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final ClockTestActivity host=activity;
            main(()->{host.setShowWhenLocked(true);host.setTurnScreenOn(true);});
            for(String id:new String[]{"org.aliveclean.clock.hyperos.doodle.1",NativeFlymeClockPlugin.ID,NativeVivoClockStyles.all(app)[0].id}){
                Harness[] created={null};
                main(()->created[0]=new Harness(app,host,id));current=created[0];
                current.runScenarios();
                frames+=current.frames;layouts+=current.layouts;
                final Harness done=current;main(done::close);current=null;
            }
        }catch(Throwable error){failed=error;}
        finally{
            if(current!=null){final Harness done=current;try{main(done::close);}catch(Throwable cleanup){if(failed==null)failed=cleanup;else failed.addSuppressed(cleanup);}}
            if(activity!=null){final ClockTestActivity host=activity;try{main(host::finish);}catch(Throwable cleanup){if(failed==null)failed=cleanup;else failed.addSuppressed(cleanup);}}
            try(OutputStream out=getTargetContext().openFileOutput(TRACE_FILE,Context.MODE_PRIVATE)){
                out.write(trace.toString().getBytes(StandardCharsets.UTF_8));
            }catch(Throwable output){if(failed==null)failed=output;else failed.addSuppressed(output);}
        }
        if(failed==null){
            result.putString("stream",summary+"NATIVE_CLOCK_JITTER_OK styles=3 frames="+frames+" layouts="+layouts+" trace="+TRACE_FILE+"\n");
            finish(-1,result);
        }else{
            // Keep Binder output bounded; the private trace retains every callback and draw.
            int start=Math.max(0,trace.length()-18000);
            result.putString("stream",summary+"NATIVE_CLOCK_JITTER_FAILED trace="+TRACE_FILE+"\n"+trace.substring(start)+android.util.Log.getStackTraceString(failed));
            finish(0,result);
        }
    }

    private interface MainStep{void run()throws Throwable;}
    private void main(MainStep step)throws Throwable{
        Throwable[] failure={null};
        runOnMainSync(()->{try{step.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw failure[0];
    }

    private final class Harness{
        final String id;
        final ClockTestActivity host;
        final NativeOriginalClockPlugin clock;
        final Handler handler=new Handler(Looper.getMainLooper());
        final ViewTreeObserver.OnDrawListener draws=this::draw;
        final int screenWidth,screenHeight;
        String stage="attach";
        Throwable failure;
        Metrics latest,baseline,strictContentBaseline,expectedNaturalBaseline;
        Endpoint[] envelope;
        int frames,layouts,movingFrames,phaseFrames,phaseMoving,firstDrawHeightCap=-1;
        boolean redirected=true,closed,expectNoWidgets=true;
        Rect lastRect;
        Rect redirectSource,redirectTarget;
        boolean firstRedirectDraw;
        final java.util.HashSet<String> clipRisks=new java.util.HashSet<>();

        Harness(Context app,ClockTestActivity host,String id)throws Exception{
            this.id=id;this.host=host;
            screenWidth=app.getResources().getDisplayMetrics().widthPixels;
            screenHeight=app.getResources().getDisplayMetrics().heightPixels;
            clock=(NativeOriginalClockPlugin)new NativeClockProvider(app,app.getAssets(),null).apply(id);
            clock.test((name,args)->{
                if("onClockLayoutCalculated".equals(name))layout(args);
                return null;
            },true);
            rows(0,0);clock.apply("onWidgetsPluginReady",null);
            Bundle tick=new Bundle();tick.putLong("time",System.currentTimeMillis());clock.apply("setTime",tick);
            scene(2,1,false);
            // The installed KeyguardClockPluginContainer gives this root the
            // full display height. wrap_content clips moving digits at the new
            // compact endpoint in our test FrameLayout before the spring ends.
            host.content.addView(clock.root,new FrameLayout.LayoutParams(-1,-1));
            clock.root.getViewTreeObserver().addOnDrawListener(draws);
            note("display="+screenWidth+"x"+screenHeight+" rows=0");
        }

        void runScenarios()throws Throwable{
            // Visit every face before freezing metrics: the stock lyrics crop is
            // discovered from its first real pre-draw, rather than a guessed height.
            endpoint("warm-large",2,1);endpoint("warm-small",2,0);
            endpoint("warm-lyrics",2,2);endpoint("warm-aod-small",5,0);
            main(()->{check();require(latest!=null,"no onClockLayoutCalculated callbacks");baseline=latest;note("frozen "+baseline);});
            ancestorTransforms();
            Endpoint big=endpoint("endpoint-large",2,1);
            Endpoint small=endpoint("endpoint-small",2,0);
            Endpoint lyrics=endpoint("endpoint-lyrics",2,2);
            Endpoint aod=endpoint("endpoint-aod-small",5,0);

            // Run the integer-sentinel reproduction first so an unfixed build
            // reports the metric fault before any separate transition failure.
            main(()->{
                phase("widget-row-race-card1-origin0-probe");baseline=null;expectNoWidgets=false;
                note("request cardOccupiedRows=1 originCardOccupiedRows=0; require bigCur>smallHeight");
                clock.apply("onWidgetsPluginReady",null);rows(1,0);check();
                rows(0,0);expectNoWidgets=true;check();baseline=latest;
                note("asymmetric widget rows satisfy SystemUI height ordering; restored rows=0");
            });
            expectedHeights(big,small,lyrics,aod);

            // Ordinary complete transitions establish that spring frames actually
            // execute in this attached window, with the full endpoints awaited.
            snap(big);animate("complete-large-to-small",small,big,small);
            animate("complete-small-to-lyrics",lyrics,small,lyrics);
            animate("complete-lyrics-to-large",big,lyrics,big);
            animate("complete-large-to-lyrics",lyrics,big,lyrics);

            main(()->{
                phase("lyrics-large-small-same-frame",lyrics,small);
                int before=frames;
                scene(2,1,true);require(clock.sceneAnimating(),"large redirect did not queue an animation");
                scene(2,0,true);
                require(frames==before,"an unexpected draw occurred inside the same main runnable");
                note("size2->size1->size0 dispatched without an intervening draw");
            });
            settled();assertFinal(small);completed(true);

            snap(lyrics);
            redirectAfter8ms("lyrics-large-small-8ms",lyrics,big,small);
            settled();assertFinal(small);completed(true);

            // Interrupt after at least one animated frame, while lyrics entry is
            // still moving; leave lyrics for large, then redirect to small at 8 ms.
            snap(big);
            main(()->{phase("interrupt-lyrics-entry",big,lyrics,small);scene(2,2,true);});
            awaitMoving();
            main(()->{
                require(clock.sceneAnimating(),"lyrics entry completed before interruption");
                note("interrupting drawn lyrics entry rect="+lastRect);
                scheduleSmallAfterLarge();
            });
            settled();assertFinal(small);completed(true);

            snap(aod);
            animate("wake-state5-small-to-state2-small",small,aod,small);

            // Reverse an already drawn wake animation before it finishes, then
            // wake again in the same runnable. Final scene must still be lock small.
            snap(aod);
            main(()->{phase("interrupt-small-wake",aod,small);scene(2,0,true);});
            awaitMoving();
            main(()->{require(clock.sceneAnimating(),"wake completed before interruption");scene(5,0,true);scene(2,0,true);});
            settled();assertFinal(small);completed(true);

            // During lyrics exit the native widget plugin can restore current
            // rows before original rows. SystemUI substitutes INT_MAX - top if
            // bigCur == 0, bigMin > bigCur, or bigCur <= small clockHeight.
            // Reproduce that callback ordering without a real widget or settings.
            snap(lyrics);
            main(()->{
                phase("widget-row-race-card1-origin0",lyrics);
                baseline=null;expectNoWidgets=false;
                note("request cardOccupiedRows=1 originCardOccupiedRows=0; require bigCur>smallHeight to avoid SystemUI integer sentinel");
                clock.apply("onWidgetsPluginReady",null);rows(1,0);check();
                require(latest!=null,"widget row fixture produced no layout callback");
                baseline=latest;note("frozen asymmetric rows "+baseline);
                scene(2,1,true);scene(2,0,true);
                // Neither the undisplayed large face nor its widget extent is
                // part of the visible source of this same-frame redirect.
                envelope=new Endpoint[]{lyrics,small};
            });
            settled();assertFinal(small);completed(true);
            snap(lyrics);
            redirectAfter8ms("widget-row-race-lyrics-large-small-8ms",lyrics,big,small);
            settled();assertFinal(small);completed(true);
            main(()->{
                Bundle saved=clock.apply("getStyleData",null);
                require(saved!=null&&saved.getString("styleData","").contains(id),"scene changes lost selected style");
                summary.append(id).append(" OK frames=").append(frames).append(" layouts=").append(layouts).append('\n');
            });
        }

        void expectedHeights(Endpoint big,Endpoint small,Endpoint lyrics,Endpoint aod)throws Throwable{
            snap(big);final Metrics[] natural={null};
            main(()->{
                phase("expected-natural-endpoints");rows(0,0);check();
                natural[0]=latest;expectedNaturalBaseline=latest;
                note("expected protocol natural endpoints "+latest+" adaptable="+(latest.bigMinContent<latest.bigContent));
            });
            Metrics ref=natural[0];
            int middle=ref.bigTop+ref.bigMin+(ref.big-ref.bigMin)/2;
            int lower=ref.bigTop+ref.bigMin+(ref.big-ref.bigMin)/4;
            android.graphics.Bitmap[] wallpaper={null};
            try{
                budget("expected-intmax-natural",Integer.MAX_VALUE);assertFinal(big);
                Endpoint fitted=fit("expected-middle",middle,ref,big,0);
                budget("expected-intmax-restores-natural",Integer.MAX_VALUE);assertFinal(big);
                fit("expected-middle-repeat",middle,ref,big,0);assertFinal(fitted);

                // Lyrics itself must retain the native lyrics geometry while a
                // new budget is cached for the next large-clock frame.
                snap(lyrics);budget("expected-cache-during-lyrics",lower);assertFinal(lyrics);
                returnFromLyrics("expected-cached-lyrics-return",lower,ref);
                Endpoint cached=assertBudget("expected-cached-large",lower,ref,big,0);
                budget("expected-cached-intmax-restore",Integer.MAX_VALUE);assertFinal(big);
                snap(lyrics);budget("expected-cache-intmax-during-lyrics",Integer.MAX_VALUE);assertFinal(lyrics);
                returnFromLyrics("expected-cached-intmax-return",Integer.MAX_VALUE,ref);assertFinal(big);
                runningExpectedRedirect(ref,big,lyrics,cached,lower);
                budget("expected-running-redirect-reset",Integer.MAX_VALUE);assertFinal(big);

                // Small and AOD faces must ignore a pending large-clock budget.
                fit("expected-middle-before-small",middle,ref,big,0);
                snap(small);budget("expected-small-unchanged",lower);assertFinal(small);
                animate("expected-small-to-aod",aod,small,aod);
                budget("expected-aod-unchanged",middle);assertFinal(aod);
                animate("expected-aod-to-small",small,aod,small);
                snap(lyrics);budget("expected-after-aod-cache",lower);assertFinal(lyrics);
                returnFromLyrics("expected-after-aod-return",lower,ref);assertFinal(cached);

                // The same content budget plus one widget extent must produce
                // the same visual height. Natural content endpoints never absorb
                // either widget occupancy or the currently adapted height.
                main(()->{
                    phase("expected-widget-rows-one");baseline=null;expectedNaturalBaseline=null;expectNoWidgets=false;
                    rows(1,0);check();
                    require(ref.sameBigContent(latest),"widget rows changed natural content endpoints baseline={"+ref+"} actual={"+latest+"}");
                    require(latest.widgets>0&&latest.small>ref.small,"one-row fixture did not add widget extent: "+latest);
                    require(latest.big-ref.big==latest.widgets&&latest.bigMin-ref.bigMin==latest.widgets,
                            "widget extent did not shift both natural total endpoints equally: baseline={"+ref+"} actual={"+latest+"}");
                    baseline=latest;expectedNaturalBaseline=latest;
                });
                final Metrics[] withWidgets={null};main(()->withWidgets[0]=latest);
                fit("expected-widget-budget",middle+withWidgets[0].widgets,withWidgets[0],big,1);assertFinal(fitted);
                budget("expected-widget-intmax-restore",Integer.MAX_VALUE);assertFinal(big);
                main(()->{
                    phase("expected-widget-rows-zero");baseline=null;expectedNaturalBaseline=null;
                    rows(0,0);expectNoWidgets=true;check();
                    require(ref.sameEndpoints(latest),"removing widgets failed to restore natural endpoints baseline={"+ref+"} actual={"+latest+"}");
                    baseline=ref;expectedNaturalBaseline=ref;
                });
                fit("expected-zero-rows-budget",middle,ref,big,0);assertFinal(fitted);

                if(id.startsWith(NativeVivoClockStyles.PREFIX)){
                    main(()->{
                        wallpaper[0]=android.graphics.Bitmap.createBitmap(64,96,android.graphics.Bitmap.Config.ARGB_8888);
                        android.graphics.Canvas canvas=new android.graphics.Canvas(wallpaper[0]);
                        android.graphics.Paint paint=new android.graphics.Paint();
                        paint.setShader(new android.graphics.LinearGradient(0,0,64,96,0xff164d75,0xffe99b72,android.graphics.Shader.TileMode.CLAMP));
                        canvas.drawRect(0,0,64,96,paint);
                        Bundle sample=new Bundle();sample.putParcelable("wallpaperBitmap",wallpaper[0]);clock.apply("setWallpaperBitmap",sample);
                    });
                    for(int mode:new int[]{7,8}){
                        main(()->{
                            phase("expected-vivo-material-mode="+mode);
                            Bundle material=new Bundle();material.putInt("mode",mode);material.putInt("color",0xffffffff);
                            clock.apply("openAliveEditColor",material);
                        });
                        fit("expected-vivo-material-middle-mode="+mode,middle,ref,big,0);awaitMaterial();assertFinal(fitted);
                        snap(lyrics);budget("expected-vivo-material-lyrics-mode="+mode,lower);assertFinal(lyrics);
                        returnFromLyrics("expected-vivo-material-return-mode="+mode,lower,ref);awaitMaterial();assertFinal(cached);
                        budget("expected-vivo-material-intmax-mode="+mode,Integer.MAX_VALUE);awaitMaterial();assertFinal(big);
                    }
                }
                main(()->note("expected protocol OK: natural endpoints stable; adaptable="+(ref.bigMinContent<ref.bigContent)));
            }finally{
                main(()->{
                    expectedNaturalBaseline=null;baseline=null;firstDrawHeightCap=-1;envelope=null;
                    redirectSource=null;redirectTarget=null;firstRedirectDraw=false;
                    if(wallpaper[0]!=null){
                        Bundle plain=new Bundle();plain.putInt("mode",2);plain.putInt("color",0xffffffff);clock.apply("openAliveEditColor",plain);
                        clock.apply("setWallpaperBitmap",null);wallpaper[0].recycle();
                    }
                    expected(Integer.MAX_VALUE);rows(0,0);expectNoWidgets=true;
                    scene(2,1,false);forceContentLayout();baseline=ref;
                });
            }
            settled();assertFinal(big);
        }
        void expected(int bottom){
            note("onClockExpectedHeightChange expectedClockHeight="+bottom);
            Bundle args=new Bundle();args.putInt("expectedClockHeight",bottom);clock.apply("onClockExpectedHeightChange",args);
        }
        void budget(String label,int bottom)throws Throwable{
            main(()->{phase(label);expected(bottom);forceContentLayout();});settled();
        }
        Endpoint fit(String label,int bottom,Metrics reference,Endpoint natural,int occupied)throws Throwable{
            budget(label,bottom);return assertBudget(label,bottom,reference,natural,occupied);
        }
        Endpoint assertBudget(String label,int bottom,Metrics reference,Endpoint natural,int occupied)throws Throwable{
            Endpoint[] out={null};
            main(()->{
                rows(occupied,0);check();
                require(reference.sameEndpoints(latest),"expected height fed back into natural min/current endpoints baseline={"+reference+"} actual={"+latest+"}");
                int wanted=contentBudget(reference,bottom),actual=clock.apply(7).getHeight();Rect rect=visibleRect();
                require(Math.abs(actual-wanted)<=2,"adapted content height does not honor expected bottom="+bottom+" wanted="+wanted+" actual="+actual+" natural={"+reference+"} rect="+rect);
                if(wanted<natural.height-2){
                    require(actual<natural.height,"adaptable middle budget did not shrink large clock natural="+natural.height+" actual="+actual);
                    require(rect.width()<=natural.rect.width()+2&&rect.height()<=natural.rect.height()+2
                            &&(rect.width()<natural.rect.width()-1||rect.height()<natural.rect.height()-1),
                            "adapted layout did not shrink visible digits natural="+natural.rect+" actual="+rect);
                }
                out[0]=new Endpoint(label,2,1,clock.apply(8),rect,clock.root.getPaddingTop(),actual);
                assertFinalNow(out[0]);note("expected budget OK wanted="+wanted+" actual="+actual+" natural="+reference);
            });return out[0];
        }
        int contentBudget(Metrics reference,int bottom){
            return (int)Math.max(reference.bigMinContent,Math.min((long)reference.bigContent,(long)bottom-reference.bigTop-reference.widgets));
        }
        void returnFromLyrics(String label,int bottom,Metrics reference)throws Throwable{
            main(()->{
                phase(label);firstDrawHeightCap=contentBudget(reference,bottom)+2;
                scene(2,1,false);forceContentLayout();
            });settled();
            main(()->require(firstDrawHeightCap==-1,"lyrics return did not sample its first draw"));
        }
        void awaitMaterial()throws Throwable{
            long deadline=SystemClock.uptimeMillis()+TIMEOUT_MS;boolean[] applied={false};
            do{
                main(()->{check();clock.root.postInvalidateOnAnimation();applied[0]=clock.materialApplied();});
                if(applied[0]){main(()->note("Vivo material remains applied at current expected height"));return;}
                SystemClock.sleep(16);
            }while(SystemClock.uptimeMillis()<deadline);
            main(()->{throw error("Vivo material missing after expected-height adaptation/scene return: "+transform());});
        }

        void runningExpectedRedirect(Metrics reference,Endpoint big,Endpoint lyrics,Endpoint fitted,int smaller)throws Throwable{
            budget("expected-running-redirect-prepare",Integer.MAX_VALUE);snap(lyrics);
            main(()->{
                phase("expected-running-redirect-enter",lyrics,big);
                probeMarker("BEGIN","min="+reference.bigMinContent+" cur="+reference.bigContent+" expected="+smaller);
                scene(2,1,true);
            });
            try{
                // Wait for real spring output, rather than redirecting a scene
                // which has only queued its first pre-draw.
                awaitMoving();awaitDrawFrame(phaseFrames+3,true);
                int[] budgets={smaller,Integer.MAX_VALUE,smaller};
                Endpoint[] targets={fitted,big,fitted};
                for(int index=0;index<budgets.length;index++){
                    final int step=index;int[] before={0};
                    main(()->{
                        check();require(clock.sceneAnimating(),"expected redirect #"+step+" missed the running transition");
                        stage="expected-running-redirect-step="+step+"-bottom="+budgets[step];envelope=null;
                        require(lastRect!=null,"expected redirect has no preceding rendered bounds");
                        redirectSource=new Rect(lastRect);redirectTarget=new Rect(targets[step].rect);firstRedirectDraw=true;
                        before[0]=frames;
                        note("redirect boundary sourceFrame="+frames+" source="+redirectSource+" target="+redirectTarget);
                        expected(budgets[step]);forceContentLayout();
                    });
                    // Each next command follows a separately rendered frame;
                    // coalescing three requests in one runnable hides this bug.
                    awaitDrawFrame(before[0]+1,false);
                }
                settled();assertBudget("expected-running-redirect-final",smaller,reference,big,0);assertFinal(fitted);
                main(()->probeMarker("OK","final="+lastRect+" metrics="+latest));
            }catch(Throwable error){
                main(()->probeMarker("FAILED",error.toString()));throw error;
            }finally{
                main(()->{redirectSource=null;redirectTarget=null;firstRedirectDraw=false;});
            }
        }
        void probeMarker(String status,String detail){
            String message="EXPECTED_REDIRECT_PROBE_"+status+" "+id+" "+detail;
            note(message);summary.append(message).append('\n');
            Bundle output=new Bundle();output.putString("stream",message+"\n");sendStatus(1,output);
        }
        void awaitDrawFrame(int wanted,boolean moving)throws Throwable{
            long deadline=SystemClock.uptimeMillis()+TIMEOUT_MS;boolean[] ready={false};
            do{
                main(()->{
                    check();clock.root.postInvalidateOnAnimation();
                    ready[0]=frames>=wanted;
                    if(ready[0]&&moving)require(clock.sceneAnimating(),"transition finished before the required animated frame="+wanted);
                });
                if(ready[0])return;
                SystemClock.sleep(8);
            }while(SystemClock.uptimeMillis()<deadline);
            main(()->{throw error("timeout waiting for rendered frame="+wanted+" actual="+frames+" rect="+lastRect);});
        }
        void checkRedirectFrame(Rect rect){
            if(redirectSource==null||redirectTarget==null)return;
            int[] source={redirectSource.left,redirectSource.top,redirectSource.right,redirectSource.bottom};
            int[] target={redirectTarget.left,redirectTarget.top,redirectTarget.right,redirectTarget.bottom};
            int[] actual={rect.left,rect.top,rect.right,rect.bottom};
            float density=clock.root.getResources().getDisplayMetrics().density;
            double slack=Math.max(4,2*density),remaining=0,jump=0;
            for(int edge=0;edge<4;edge++){
                remaining=Math.max(remaining,Math.abs((long)target[edge]-source[edge]));
                jump=Math.max(jump,Math.abs((long)actual[edge]-source[edge]));
                require(actual[edge]>=Math.min(source[edge],target[edge])-slack&&actual[edge]<=Math.max(source[edge],target[edge])+slack,
                        "expected redirect frame moved outside source-to-target edge="+edge+" source="+redirectSource+" target="+redirectTarget+" actual="+rect+" "+transform());
            }
            if(firstRedirectDraw){
                // A redirected spring can advance during the same pre-draw.
                // Allow that progress, while rejecting an immediate size reset.
                double allowed=Math.max(4*density,remaining*.35+4);
                note("redirect first draw source="+redirectSource+" target="+redirectTarget+" actual="+rect+" maxEdgeJump="+jump+" allowed="+allowed);
                require(jump<=allowed,"one-frame digit jump after expected change jump="+jump+" allowed="+allowed+" source="+redirectSource+" target="+redirectTarget+" actual="+rect);
                firstRedirectDraw=false;
            }
        }

        void diagnoseClipping(Rect ink){
            // Geometry only: this reports possible clipping by View properties;
            // it cannot establish which pixels reached the display.
            for(View child=clock.apply(8);child!=null;){
                Rect explicit=child.getClipBounds();
                if(explicit!=null)clipRisk("clipBounds",child,new android.graphics.RectF(explicit),ink);
                android.view.ViewParent next=child.getParent();
                if(!(next instanceof android.view.ViewGroup))break;
                android.view.ViewGroup parent=(android.view.ViewGroup)next;
                // clipChildren clips the drawing of this immediate child to
                // the child's bounds, including any overflowing descendants.
                if(parent.getClipChildren())clipRisk("clipChildren parent="+parent.getClass().getName(),child,
                        new android.graphics.RectF(0,0,child.getWidth(),child.getHeight()),ink);
                if(parent.getClipToPadding()&&(parent.getPaddingLeft()!=0||parent.getPaddingTop()!=0||parent.getPaddingRight()!=0||parent.getPaddingBottom()!=0))
                    clipRisk("clipToPadding",parent,new android.graphics.RectF(parent.getPaddingLeft(),parent.getPaddingTop(),parent.getWidth()-parent.getPaddingRight(),parent.getHeight()-parent.getPaddingBottom()),ink);
                child=parent;
            }
        }
        void clipRisk(String kind,View owner,android.graphics.RectF local,Rect ink){
            Matrix matrix=new Matrix();owner.transformMatrixToGlobal(matrix);matrix.mapRect(local);
            if(ink.left>=local.left-2&&ink.top>=local.top-2&&ink.right<=local.right+2&&ink.bottom<=local.bottom+2)return;
            String key=stage+" "+kind+" "+owner.getClass().getName();
            if(clipRisks.add(key))note("GEOMETRY_CLIP_RISK kind="+kind+" owner="+owner.getClass().getName()+" ink="+ink+" globalClip="+local
                    +" testHost="+(owner==clock.root||owner==host.content)+" (geometry only; pixel verification required)");
        }

        void ancestorTransforms()throws Throwable{
            final Metrics[] reference={null};
            main(()->{
                phase("ancestor-transform-baseline");
                require(expectNoWidgets&&baseline!=null&&baseline.widgets==0,"ancestor fixture requires stable zero-widget content metrics");
                reference[0]=baseline;strictContentBaseline=baseline;
                note("exact content baseline "+baseline);
            });
            try{
                // Only an ancestor's drawing transform changes. Width specs,
                // style, time, widget rows and each face's own transform stay
                // fixed. Repeated measures must not gain or lose even one px.
                for(float[] transform:new float[][]{{.9f,.375f,-.625f},{.95f,-.75f,.25f},{1f,.125f,-.875f},{.99f,.625f,.375f}}){
                    for(int repeat=0;repeat<3;repeat++)for(int size:new int[]{1,0}){
                        final int pass=repeat;
                        main(()->{
                            phase("ancestor-scale="+transform[0]+"-translate="+transform[1]+","+transform[2]+"-pass="+pass+"-size="+size);
                            host.content.setScaleX(transform[0]);host.content.setScaleY(transform[0]);
                            host.content.setTranslationX(transform[1]);host.content.setTranslationY(transform[2]);
                            scene(2,size,false);forceContentLayout();
                        });
                        settled();
                        main(()->{
                            // A bounds-preserving layout need not emit a layout
                            // callback. Request fresh metrics after that actual
                            // traversal so cached earlier metrics cannot pass.
                            int before=layouts;rows(0,0);check();
                            require(layouts>before,"ancestor fixture produced no fresh layout metrics");
                            require(reference[0].sameContent(latest),"ancestor transform changed content height by at least one pixel baseline={"+reference[0]+"} actual={"+latest+"}");
                            note("exact content OK activeHeight="+clock.apply(8).getMeasuredHeight()+" "+latest);
                        });
                    }
                }
            }finally{
                // Also restore on an assertion failure; these are properties of
                // this disposable Activity's container, never system settings.
                main(()->{
                    strictContentBaseline=null;envelope=null;
                    host.content.setScaleX(1);host.content.setScaleY(1);
                    host.content.setTranslationX(0);host.content.setTranslationY(0);
                    forceContentLayout();note("restored ancestor scale=1 translate=0,0");
                });
            }
            main(()->phase("ancestor-transform-restored"));settled();
            main(()->{
                rows(0,0);check();
                require(reference[0].sameContent(latest),"content height failed to restore exactly baseline={"+reference[0]+"} actual={"+latest+"}");
                note("ancestor transform fixture OK; exact baseline retained");
            });
        }
        void forceContentLayout(){
            clock.apply(8).forceLayout();clock.clockContainer.forceLayout();
            clock.root.forceLayout();clock.root.requestLayout();
        }

        Endpoint endpoint(String label,int state,int size)throws Throwable{
            main(()->{phase(label);scene(state,size,false);});settled();
            Endpoint[] out={null};
            main(()->{
                check();Rect rect=visibleRect();
                out[0]=new Endpoint(label,state,size,clock.apply(8),rect,clock.root.getPaddingTop(),clock.clockContainer.getHeight());
                assertFinalNow(out[0]);note("endpoint "+out[0]);
            });
            return out[0];
        }
        void snap(Endpoint endpoint)throws Throwable{
            main(()->{phase("prepare-"+endpoint.label);scene(endpoint.state,endpoint.size,false);});
            settled();assertFinal(endpoint);
        }
        void animate(String label,Endpoint target,Endpoint... bounds)throws Throwable{
            main(()->{phase(label,bounds);scene(target.state,target.size,true);});
            settled();assertFinal(target);completed(true);
        }
        void redirectAfter8ms(String label,Endpoint... bounds)throws Throwable{
            main(()->{phase(label,bounds);scheduleSmallAfterLarge();});
        }
        void scheduleSmallAfterLarge(){
            redirected=false;long dispatched=SystemClock.uptimeMillis();scene(2,1,true);
            handler.postAtTime(()->{
                if(closed)return;
                try{note("redirect size1->size0 elapsed_ms="+(SystemClock.uptimeMillis()-dispatched));scene(2,0,true);redirected=true;}
                catch(Throwable error){fail(error);}
            },dispatched+8);
        }
        void scene(int state,int size,boolean animate){
            note("state="+state+" size="+size+" animate="+animate);
            Bundle args=new Bundle();args.putInt("uiState",state);args.putInt("clockSize",size);args.putBoolean("isAnim",animate);
            clock.apply("onClockStateChanged",args);
        }
        void rows(int occupied,int original){
            Bundle rows=new Bundle();rows.putInt("cardOccupiedRows",occupied);rows.putInt("originCardOccupiedRows",original);
            rows.putInt("displayWidgetRows",occupied);rows.putInt("displayWidgetCols",4);
            clock.apply("onWidgetDataRowChanged",rows);
        }
        void phase(String name,Endpoint... bounds){
            check();stage=name;envelope=bounds;phaseFrames=frames;phaseMoving=movingFrames;redirected=true;
            note("begin endpoints="+java.util.Arrays.toString(bounds));
        }
        void settled()throws Throwable{await(false);}
        void awaitMoving()throws Throwable{await(true);}
        void await(boolean moving)throws Throwable{
            long deadline=SystemClock.uptimeMillis()+TIMEOUT_MS;boolean[] ready={false};
            do{
                main(()->{
                    check();clock.root.postInvalidateOnAnimation();
                    ready[0]=moving?movingFrames>phaseMoving&&clock.sceneAnimating():
                            redirected&&!clock.sceneAnimating()&&frames>=phaseFrames+3;
                });
                if(ready[0])return;
                SystemClock.sleep(16);
            }while(SystemClock.uptimeMillis()<deadline);
            main(()->{throw error("timeout waiting for "+(moving?"a drawn animation":"settled scene")+" frames="+(frames-phaseFrames)+" movingFrames="+(movingFrames-phaseMoving)+" rect="+lastRect+" metrics="+latest+" "+transform());});
        }
        void completed(boolean expectMotion)throws Throwable{
            main(()->{
                require(frames>phaseFrames,"no rendered frames were sampled");
                if(expectMotion)require(movingFrames>phaseMoving,"no spring frames were drawn; cannot validate animation");
                note("OK frames="+(frames-phaseFrames)+" movingFrames="+(movingFrames-phaseMoving)+" rect="+lastRect);
            });
        }

        void layout(Bundle args){
            try{
                layouts++;Metrics value=new Metrics(args);latest=value;
                note("layout#"+layouts+" "+value);
                for(int height:new int[]{value.small,value.big,value.bigMin,value.bigContent,value.bigMinContent,value.immersed})
                    require(height>0&&height<screenHeight,"onClockLayoutCalculated height outside (0,"+screenHeight+"): "+value);
                require(value.big>value.small&&value.bigMin<=value.big,
                        "onClockLayoutCalculated would trigger SystemUI INTMAX sentinel: bigCur="+value.big+" bigMin="+value.bigMin+" smallClockHeight="+value.small+"; "+value);
                if(expectNoWidgets)require(value.widgets==0,"zero-row fixture unexpectedly reserves widgets: "+value);
                require(value.widgets>=0&&value.big==value.bigContent+value.widgets&&value.bigMin==value.bigMinContent+value.widgets,
                        "big current/min/content/widget heights disagree: "+value);
                if(strictContentBaseline!=null)require(strictContentBaseline.sameContent(value),
                        "ancestor transform changed content height by at least one pixel baseline={"+strictContentBaseline+"} actual={"+value+"} hostScale="+host.content.getScaleX()+","+host.content.getScaleY()+" hostTranslation="+host.content.getTranslationX()+","+host.content.getTranslationY());
                if(expectedNaturalBaseline!=null)require(expectedNaturalBaseline.sameEndpoints(value),
                        "expected height changed natural endpoints baseline={"+expectedNaturalBaseline+"} actual={"+value+"}");
                if(baseline!=null)require(baseline.near(value),"onClockLayoutCalculated changed during scene transition baseline={"+baseline+"} actual={"+value+"}");
            }catch(Throwable error){fail(error);}
        }
        void draw(){
            if(closed)return;
            try{
                frames++;if(clock.sceneAnimating())movingFrames++;
                Rect rect=visibleRect();lastRect=new Rect(rect);
                note("draw#"+frames+" moving="+clock.sceneAnimating()+" visibleRect="+rect+" "+transform());
                checkRedirectFrame(rect);diagnoseClipping(rect);
                if(firstDrawHeightCap>=0){
                    require(clock.apply(7).getHeight()<=firstDrawHeightCap,
                            "first lyrics-return frame ignored cached expected height cap="+firstDrawHeightCap+" actual="+clock.apply(7).getHeight()+" rect="+rect);
                    note("first lyrics-return frame honored cached content cap="+firstDrawHeightCap);firstDrawHeightCap=-1;
                }
                require(clock.root.isAttachedToWindow()&&clock.root.isShown(),"clock is not attached to a visible Activity window");
                View active=clock.apply(8);Matrix matrix=new Matrix();active.transformMatrixToGlobal(matrix);
                float[] values=new float[9];matrix.getValues(values);
                for(float value:values)require(Float.isFinite(value),"non-finite global matrix: "+matrix+" rect="+rect);
                for(float value:new float[]{active.getAlpha(),active.getScaleX(),active.getScaleY(),active.getTranslationX(),active.getTranslationY()})
                    require(Float.isFinite(value),"non-finite face transform: "+transform()+" rect="+rect);
                require(active.getAlpha()>0&&active.getScaleX()>0&&active.getScaleY()>0,"empty/inverted rendered clock: "+transform()+" rect="+rect);
                if(envelope!=null&&envelope.length>0){
                    long maxWidth=0,maxHeight=0;int left=Integer.MAX_VALUE,top=Integer.MAX_VALUE,right=Integer.MIN_VALUE,bottom=Integer.MIN_VALUE;
                    for(Endpoint endpoint:envelope){
                        maxWidth=Math.max(maxWidth,(long)endpoint.rect.right-endpoint.rect.left);maxHeight=Math.max(maxHeight,(long)endpoint.rect.bottom-endpoint.rect.top);
                        left=Math.min(left,endpoint.rect.left);top=Math.min(top,endpoint.rect.top);right=Math.max(right,endpoint.rect.right);bottom=Math.max(bottom,endpoint.rect.bottom);
                    }
                    double slackX=maxWidth*.25+4,slackY=maxHeight*.25+4;
                    require((long)rect.right-rect.left<=maxWidth+slackX&&(long)rect.bottom-rect.top<=maxHeight+slackY,
                            "visibleRect grew beyond transition endpoints max="+maxWidth+"x"+maxHeight+" rect="+rect+" "+transform());
                    require(rect.left>=left-slackX&&rect.right<=right+slackX&&rect.top>=top-slackY&&rect.bottom<=bottom+slackY,
                            "visibleRect escaped transition endpoints union="+new Rect(left,top,right,bottom)+" rect="+rect+" "+transform());
                }
            }catch(Throwable error){fail(error);}
        }
        Rect visibleRect(){
            Bundle output=clock.apply("getClockVisibleRect",null);Rect rect=output==null?null:(Rect)output.getParcelable("visibleRect");
            require(rect!=null,"getClockVisibleRect returned no visibleRect");
            long width=(long)rect.right-rect.left,height=(long)rect.bottom-rect.top;
            require(width>0&&height>0&&width<=screenWidth+4L&&height<screenHeight,
                    "getClockVisibleRect unreasonable size="+width+"x"+height+" rect="+rect+" screen="+screenWidth+"x"+screenHeight);
            require(Math.abs((long)rect.left)<screenWidth*3L&&Math.abs((long)rect.right)<screenWidth*3L
                    &&Math.abs((long)rect.top)<screenHeight*3L&&Math.abs((long)rect.bottom)<screenHeight*3L,
                    "getClockVisibleRect extreme coordinates (possible integer saturation): "+rect);
            return rect;
        }
        void assertFinal(Endpoint expected)throws Throwable{main(()->assertFinalNow(expected));}
        void assertFinalNow(Endpoint expected){
            check();View active=clock.apply(8);Rect actual=visibleRect();
            require(!clock.sceneAnimating(),"final scene is still animating");
            require(active==expected.face,"wrong final scene expected="+expected+" actualFace="+active.getClass().getName());
            require(clock.clockContainer.getVisibility()==View.VISIBLE&&active.getVisibility()==View.VISIBLE,"final clock is hidden");
            require(Math.abs(active.getAlpha()-1)<.001&&Math.abs(active.getScaleX()-1)<.001&&Math.abs(active.getScaleY()-1)<.001
                    &&Math.abs(active.getTranslationX())<.01&&Math.abs(active.getTranslationY())<.01,"stale final transform: "+transform());
            require(Math.abs(actual.left-expected.rect.left)<=2&&Math.abs(actual.top-expected.rect.top)<=2
                    &&Math.abs(actual.right-expected.rect.right)<=2&&Math.abs(actual.bottom-expected.rect.bottom)<=2,
                    "wrong final visibleRect expected="+expected.rect+" actual="+actual);
            require(clock.root.getPaddingTop()==expected.top&&clock.clockContainer.getHeight()==expected.height,
                    "wrong final layout expected top/height="+expected.top+"/"+expected.height+" actual="+clock.root.getPaddingTop()+"/"+clock.clockContainer.getHeight());
            require(latest!=null,"no final layout metrics");
        }
        String transform(){
            View face=clock.apply(8);
            return face==null?"face=null":"face="+face.getClass().getSimpleName()+" measured="+face.getMeasuredWidth()+"x"+face.getMeasuredHeight()
                    +" scale="+face.getScaleX()+","+face.getScaleY()+" translate="+face.getTranslationX()+","+face.getTranslationY()+" alpha="+face.getAlpha();
        }
        void note(String message){trace.append(SystemClock.uptimeMillis()).append(' ').append(id).append(" [").append(stage).append("] ").append(message).append('\n');}
        void require(boolean value,String reason){if(!value)throw error(reason);}
        AssertionError error(String reason){return new AssertionError(id+" ["+stage+"] "+reason);}
        void fail(Throwable error){if(failure==null){failure=error;note("FAILED "+error);}}
        void check(){if(failure!=null)throw new AssertionError(id+" ["+stage+"] prior callback/draw failure",failure);}
        void close(){
            if(closed)return;closed=true;handler.removeCallbacksAndMessages(null);
            if(clock.root.getViewTreeObserver().isAlive())clock.root.getViewTreeObserver().removeOnDrawListener(draws);
            try{clock.apply("release",null);}finally{host.content.removeView(clock.root);}
        }
    }

    private static final class Endpoint{
        final String label;final int state,size,top,height;final View face;final Rect rect;
        Endpoint(String label,int state,int size,View face,Rect rect,int top,int height){
            this.label=label;this.state=state;this.size=size;this.face=face;this.rect=new Rect(rect);this.top=top;this.height=height;
        }
        @Override public String toString(){return label+"(state="+state+",size="+size+",rect="+rect+",top="+top+",height="+height+")";}
    }
    private static final class Metrics{
        final int small,big,bigMin,bigContent,bigMinContent,immersed,widgets,bigTop;
        Metrics(Bundle args){
            if(args==null||!args.containsKey("clockHeight")||!args.containsKey("clockHeightImmersed"))throw new AssertionError("onClockLayoutCalculated missing small/immersed heights: "+args);
            Bundle large=args.getBundle("bigClockPositionParams");
            if(large==null)throw new AssertionError("onClockLayoutCalculated missing bigClockPositionParams: "+args);
            small=args.getInt("clockHeight",-1);immersed=args.getInt("clockHeightImmersed",-1);
            big=large.getInt("bigClockCurHeight",-1);bigMin=large.getInt("bigClockMinHeight",-1);
            bigContent=large.getInt("bigClockContentHeightAtCurFont",-1);bigMinContent=large.getInt("bigClockContentHeightAtMinFont",-1);
            widgets=large.getInt("bigClockWidgetsHeight",-1);
            bigTop=large.getInt("bigClockTop",-1);
        }
        boolean near(Metrics other){
            return Math.abs((long)small-other.small)<=2&&Math.abs((long)big-other.big)<=2&&Math.abs((long)bigMin-other.bigMin)<=2
                    &&Math.abs((long)bigContent-other.bigContent)<=2&&Math.abs((long)bigMinContent-other.bigMinContent)<=2&&Math.abs((long)immersed-other.immersed)<=2&&widgets==other.widgets;
        }
        boolean sameContent(Metrics other){return other!=null&&small==other.small&&bigContent==other.bigContent&&bigMinContent==other.bigMinContent;}
        boolean sameBigContent(Metrics other){return other!=null&&bigContent==other.bigContent&&bigMinContent==other.bigMinContent;}
        boolean sameEndpoints(Metrics other){return sameContent(other)&&big==other.big&&bigMin==other.bigMin&&widgets==other.widgets&&bigTop==other.bigTop;}
        @Override public String toString(){return "bigHeight="+big+" bigMinHeight="+bigMin+" bigContent="+bigContent+" bigMinContent="+bigMinContent+" smallHeight="+small+" immersed="+immersed+" widgets="+widgets+" bigTop="+bigTop;}
    }
}
