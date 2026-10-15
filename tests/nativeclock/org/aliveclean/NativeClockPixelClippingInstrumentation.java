package org.aliveclean;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Fixed real spring poses, with hardware background-difference coverage evidence. */
public final class NativeClockPixelClippingInstrumentation extends Instrumentation {
    private static final long TIMEOUT=6000;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final StringBuilder trace=new StringBuilder();
    private ClockTestActivity activity;
    private NativeClockHostFixture fixture;
    private NativeOriginalClockPlugin clock;
    private View outer;
    private String styles;
    private int[] modes={3},samples={2,5};
    private String[] hosts={"direct","native"};
    private boolean direct;
    private int captures,clips,unstable;
    private boolean fitted;
    private String prefix;
    private Pose frozen;
    private Bitmap wallpaper;

    @Override public void onCreate(Bundle args){
        super.onCreate(args);styles=args==null?null:args.getString("style");
        if(args!=null){
            modes=integers(args.getString("modes"),modes);samples=integers(args.getString("frames"),samples);
            if(args.getString("hosts")!=null)hosts=args.getString("hosts").split(",");
            fitted="true".equals(args.getString("fitted"));
        }
        start();
    }
    private static int[] integers(String text,int[] fallback){
        return text==null?fallback:Arrays.stream(text.split(",")).mapToInt(Integer::parseInt).toArray();
    }
    private interface Work{void run()throws Throwable;}
    private void main(Work work)throws Throwable{
        Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable failure){error[0]=failure;}});
        if(error[0]!=null)throw error[0];
    }
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable failed=null;
        try{
            Bundle launch=new Bundle();launch.putString("stream","test activity launch\n");sendStatus(1,launch);
            activity=(ClockTestActivity)startActivitySync(new Intent(getTargetContext(),ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(()->{activity.setShowWhenLocked(true);activity.setTurnScreenOn(true);});frames(2);
            if(!activity.content.isHardwareAccelerated())throw new AssertionError("Pixel clipping probe requires hardwareAccelerated=true");
            List<String> ids=new ArrayList<>();
            if(styles==null)ids.add("org.aliveclean.clock.hyperos.doodle.1");
            else{
                ids.add(NativeFlymeClockPlugin.ID);ids.add(NativeFlymeClockPlugin.HORIZONTAL_ID);ids.add(NativeFlymeArtworkPlugin.PERSPECTIVE);
                for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)ids.add(style.id);
                for(NativeVivoClockStyles.Style style:NativeVivoClockStyles.all(activity))ids.add(style.id);
                ids.removeIf(id->!Arrays.stream(styles.split(",")).anyMatch(id::endsWith));
            }
            if(ids.isEmpty())throw new AssertionError("No requested styles");
            wallpaper=Bitmap.createBitmap(96,192,Bitmap.Config.ARGB_8888);
            for(int y=0;y<192;y++)for(int x=0;x<96;x++)wallpaper.setPixel(x,y,Color.rgb(30+x,55+y/2,160-y/3));
            for(String host:hosts)for(String id:ids){
                if(!host.equals("direct")&&!host.equals("native"))throw new IllegalArgumentException("Unknown fixture "+host);
                direct=host.equals("direct");
                main(()->attach(id));frames(4);
                // Warm stock crop geometry before taking fixed-pose evidence.
                for(int size:new int[]{2,0,1}){main(()->scene(2,size,false));frames(4);}
                if(fitted){
                    main(()->{
                        View active=clock.apply(8);if(!(active instanceof NativeAdaptiveClockFace))throw new AssertionError("No adaptive large face");
                        NativeAdaptiveClockFace face=(NativeAdaptiveClockFace)active;
                        int middle=(face.minimumHeight()+face.naturalHeight())/2;
                        Bundle budget=new Bundle();budget.putInt("expectedClockHeight",clock.root.getPaddingTop()+middle);
                        clock.apply("onClockExpectedHeightChange",budget);
                        note("fitted budget="+budget+" natural="+face.naturalHeight()+" minimum="+face.minimumHeight());
                    });frames(4);
                }
                for(int mode:modes){
                    main(()->{thaw();color(mode);});frames(4);
                    prefix=host+"-"+id.substring("org.aliveclean.clock.".length()).replace('.','-')+"-m"+mode+(fitted?"-fit":"");
                    main(()->scene(2,1,false));frames(4);materialReady(mode);capture("endpoint-large",mode);
                    // Each sample comes from a new real spring, never an invented interpolation.
                    for(int[] route:new int[][]{{2,1,2,0},{2,1,2,2}}){
                        for(int sample:samples){
                            main(()->{thaw();scene(route[0],route[1],false);});frames(4);materialReady(mode);
                            freeze(route[2],route[3],sample);frames(3);materialReady(mode);
                            capture("s"+route[0]+"z"+route[1]+"-s"+route[2]+"z"+route[3]+"-f"+sample,mode);
                        }
                    }
                }
                main(this::close);
            }
            if(captures==0)throw new AssertionError("No pixel captures");
        }catch(Throwable failure){failed=failure;}
        finally{
            try{main(()->{close();if(activity!=null)activity.finish();});}catch(Throwable cleanup){if(failed==null)failed=cleanup;else failed.addSuppressed(cleanup);}
            if(wallpaper!=null)wallpaper.recycle();
            try{write("native-clock-pixel-clipping.txt",trace.toString());}catch(Throwable output){if(failed==null)failed=output;else failed.addSuppressed(output);}
        }
        String status=failed==null&&clips==0&&unstable==0?"OK":"FAILED";
        String summary="NATIVE_CLOCK_PIXEL_CLIPPING_"+status+" captures="+captures+" clips="+clips+" unstable="+unstable+" trace=native-clock-pixel-clipping.txt\n";
        result.putString("stream",summary+trace.substring(Math.max(0,trace.length()-14000))+(failed==null?"":android.util.Log.getStackTraceString(failed)));
        finish(status.equals("OK")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void attach(String id)throws Exception{
        activity.content.removeAllViews();
        if(direct){
            clock=(NativeOriginalClockPlugin)new NativeClockProvider(activity,activity.getAssets(),null).apply(id);
            clock.test((name,args)->null,true);outer=clock.root;
        }else{
            fixture=new NativeClockHostFixture(activity);
            fixture.write(new JSONObject(fixture.read()).put("pkg",id).put("clockStyleConfig",NativeClockHostFixture.config(id)).toString());
            clock=fixture.customClock;outer=(View)fixture.root;
        }
        if(clock==null||!id.equals(clock.id))throw new AssertionError("Wrong clock "+id);
        Bundle rows=new Bundle();rows.putInt("cardOccupiedRows",0);rows.putInt("originCardOccupiedRows",0);clock.apply("onWidgetDataRowChanged",rows);
        Bundle time=new Bundle();java.util.Calendar fixed=java.util.Calendar.getInstance();fixed.set(2026,8,27,13,4,0);time.putLong("time",fixed.getTimeInMillis());clock.apply("setTime",time);
        // Reproduce the direct jitter fixture exactly; the installed host keeps
        // its own fullscreen outer root. Neither path changes clipping flags.
        activity.content.addView(outer,new FrameLayout.LayoutParams(-1,direct?-2:-1));
        scene(2,1,false);note("attach "+id+" fixture="+(direct?"direct-wrap-content":"installed-native-host")+" host="+outer.getClass().getName()+"; all container clipping flags preserved");
    }
    private void scene(int state,int size,boolean animation)throws Exception{
        Bundle args=new Bundle();args.putInt("uiState",state);args.putInt("clockSize",size);args.putBoolean("isAnim",animation);
        if(direct)clock.apply("onClockStateChanged",args);else fixture.call("onClockStateChanged",args);
    }
    private void color(int mode){
        Bundle args=new Bundle();args.putParcelable("wallpaperBitmap",wallpaper);clock.apply("setWallpaperBitmap",args);
        args=new Bundle();args.putInt("mode",mode);args.putInt("color",Color.WHITE);clock.apply("openAliveEditColor",args);
    }
    private void materialReady(int mode)throws Throwable{
        if(mode==3||clock.apply(8) instanceof NativeOfficialClockFaces)return;
        long deadline=SystemClock.uptimeMillis()+TIMEOUT;boolean[] ready={false};
        do{main(()->ready[0]=clock.materialApplied());if(ready[0])return;frames(2);}while(SystemClock.uptimeMillis()<deadline);
        throw new AssertionError("Material not ready mode="+mode+" "+NativeClockLoadState.snapshot());
    }
    private void frames(int count)throws Throwable{
        CountDownLatch done=new CountDownLatch(1);
        main(()->{
            ViewTreeObserver.OnDrawListener listener=new ViewTreeObserver.OnDrawListener(){
                int drawn;
                @Override public void onDraw(){
                    if(++drawn>=count)activity.content.post(()->{if(activity.content.getViewTreeObserver().isAlive())activity.content.getViewTreeObserver().removeOnDrawListener(this);done.countDown();});
                    else activity.content.postInvalidateOnAnimation();
                }
            };
            activity.content.getViewTreeObserver().addOnDrawListener(listener);activity.content.invalidate();
        });
        if(!done.await(TIMEOUT,TimeUnit.MILLISECONDS))throw new AssertionError("No hardware draw within timeout");
    }
    private void freeze(int state,int size,int wantedFrame)throws Throwable{
        CountDownLatch done=new CountDownLatch(1);Throwable[] error={null};
        main(()->{
            scene(state,size,true);
            ViewTreeObserver.OnPreDrawListener listener=new ViewTreeObserver.OnPreDrawListener(){
                int visible;
                @Override public boolean onPreDraw(){
                    try{
                        View active=clock.apply(8);
                        if(!clock.sceneAnimating())throw new AssertionError("Spring ended before requested visible frame "+wantedFrame);
                        if(active.getAlpha()>0&&++visible>=wantedFrame){
                            frozen=new Pose(active);Object transition=field(clock,"transition");
                            // Silent native abort prevents a later tick, while preserving
                            // layout, scene, clipping, material mounts and the captured pose.
                            set(transition,"running",false);set(transition,"completed",null);
                            Object spring=field(transition,"spring");
                            if(spring==null)throw new AssertionError("No running stock spring to freeze");
                            ((java.lang.reflect.Method)field(transition,"abort")).invoke(spring);set(transition,"spring",null);
                            frozen.apply();activity.content.getViewTreeObserver().removeOnPreDrawListener(this);
                            note("freeze requestedFrame="+wantedFrame+" visibleFrame="+visible+" "+frozen);done.countDown();
                        }
                    }catch(Throwable failure){error[0]=failure;activity.content.getViewTreeObserver().removeOnPreDrawListener(this);done.countDown();}
                    return true;
                }
            };
            // Registered after scene's pending pre-draw: the first incoming
            // pose is already populated when this listener examines it.
            activity.content.getViewTreeObserver().addOnPreDrawListener(listener);activity.content.invalidate();
        });
        if(!done.await(TIMEOUT,TimeUnit.MILLISECONDS))throw new AssertionError("No intermediate spring pose");
        if(error[0]!=null)throw error[0];
    }
    private void thaw(){if(frozen!=null){frozen=null;}}
    private static final class Pose{
        final View view;final float x,y,sx,sy,px,py,alpha;
        Pose(View view){this.view=view;x=view.getTranslationX();y=view.getTranslationY();sx=view.getScaleX();sy=view.getScaleY();px=view.getPivotX();py=view.getPivotY();alpha=view.getAlpha();}
        void apply(){view.setPivotX(px);view.setPivotY(py);view.setScaleX(sx);view.setScaleY(sy);view.setTranslationX(x);view.setTranslationY(y);view.setAlpha(alpha);}
        @Override public String toString(){return view.getClass().getName()+" scale="+sx+","+sy+" translation="+x+","+y+" pivot="+px+","+py+" alpha="+alpha;}
    }
    private Bitmap copy(int background)throws Throwable{
        main(()->{activity.content.setBackgroundColor(background);if(frozen!=null)frozen.apply();activity.content.invalidate();});frames(3);
        Bitmap[] bitmap={null};CountDownLatch ready=new CountDownLatch(1);int[] result={-1};
        main(()->{
            View decor=activity.getWindow().getDecorView();bitmap[0]=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
            PixelCopy.request(activity.getWindow(),new Rect(0,0,decor.getWidth(),decor.getHeight()),bitmap[0],status->{result[0]=status;ready.countDown();},handler);
        });
        if(!ready.await(TIMEOUT,TimeUnit.MILLISECONDS)||result[0]!=PixelCopy.SUCCESS)throw new AssertionError("PixelCopy status="+result[0]);
        return bitmap[0];
    }
    private void capture(String name,int mode)throws Throwable{
        String key=prefix+"-"+name;Mask[] reference={null};long[] before={0},after={0};
        main(()->{reference[0]=mask();before[0]=geometry(clock.apply(8));});
        Bitmap black=copy(Color.BLACK),white=copy(Color.WHITE),repeat=copy(Color.BLACK);
        main(()->after[0]=geometry(clock.apply(8)));
        Mask mask=reference[0];int w=black.getWidth(),h=black.getHeight();
        // Keep only scanlines in Java heap; full-screen int arrays alongside
        // seven evidence bitmaps can exceed the disposable app's heap limit.
        int[] expected=new int[w],above=new int[w],below=new int[w],local=new int[w],b=new int[w],a=new int[w],again=new int[w];
        int[] observed=new int[w],difference=new int[w],rowExpected=new int[h],rowMissing=new int[h];
        Bitmap alphaImage=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888),diff=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int total=0,missing=0,insideMissing=0,gpuMissing=0,changes=0;
        for(int y=1;y<h-1;y++){
            mask.leaves.getPixels(above,0,w,0,y-1,w,1);mask.leaves.getPixels(expected,0,w,0,y,w,1);mask.leaves.getPixels(below,0,w,0,y+1,w,1);
            mask.face.getPixels(local,0,w,0,y,w,1);black.getPixels(b,0,w,0,y,w,1);white.getPixels(a,0,w,0,y,w,1);repeat.getPixels(again,0,w,0,y,w,1);
            Arrays.fill(difference,0);
            for(int x=1;x<w-1;x++){
                int delta=median(Color.red(a[x])-Color.red(b[x]),Color.green(a[x])-Color.green(b[x]),Color.blue(a[x])-Color.blue(b[x]));
                int alpha=Math.max(0,Math.min(255,255-delta));observed[x]=Color.rgb(alpha,alpha,alpha);
                if((expected[x]>>>24)<180)continue;
                if(distance(b[x],again[x])>12)changes++;
                boolean interior=true;
                for(int dx=-1;dx<=1;dx++)if((above[x+dx]>>>24)<160||(expected[x+dx]>>>24)<160||(below[x+dx]>>>24)<160){interior=false;break;}
                if(!interior)continue;
                total++;rowExpected[y]++;
                if(alpha<=8){
                    missing++;rowMissing[y]++;
                    if((local[x]>>>24)<24){insideMissing++;difference[x]=0xffff00ff;}else{gpuMissing++;difference[x]=0xffff2020;}
                }else difference[x]=0xff103010;
            }
            alphaImage.setPixels(observed,0,w,0,y,w,1);diff.setPixels(difference,0,w,0,y,w,1);
        }
        boolean changed=before[0]!=after[0]||changes>Math.max(20,total/200);
        boolean clipped=missing>Math.max(32,total/500);
        if(changed)unstable++;else if(clipped)clips++;
        captures++;
        String line=key+" mode="+mode+" expectedInterior="+total+" hardMissing="+missing+" internalSoftwareMissing="+insideMissing+" hardwareOrOuterMissing="+gpuMissing+" repeatBlackChanged="+changes+" geometryStable="+(before[0]==after[0])+" result="+(changed?"UNSTABLE":clipped?"CLIP":"OK");
        note(line+"\n"+mask.description);
        if(total<100)throw new AssertionError("Insufficient visible reference pixels: "+line);
        StringBuilder rows=new StringBuilder("y,expectedInterior,hardMissing\n");
        for(int y=0;y<h;y++)if(rowExpected[y]>0){
            rows.append(y).append(',').append(rowExpected[y]).append(',').append(rowMissing[y]).append('\n');
            if(rowMissing[y]>=8&&rowMissing[y]>=rowExpected[y]*.8){
                note("HORIZONTAL_MISSING_ROW y="+y+" expected="+rowExpected[y]+" missing="+rowMissing[y]);
                for(Edge edge:mask.edges)if(Math.abs(edge.bounds.bottom-y)<=4||Math.abs(edge.bounds.top-y)<=4)note("NEAR_CLIP_EDGE "+edge);
            }
        }
        save(black,key+"-black");save(white,key+"-white");save(repeat,key+"-black-repeat");save(mask.leaves,key+"-leaf-mask");save(mask.face,key+"-face-mask");save(alphaImage,key+"-hardware-alpha");save(diff,key+"-missing");write(key+"-rows.csv",rows.toString());
        black.recycle();white.recycle();repeat.recycle();mask.leaves.recycle();mask.face.recycle();alphaImage.recycle();diff.recycle();
        write("native-clock-pixel-clipping.txt",trace.toString());
    }
    private static int median(int a,int b,int c){return Math.max(Math.min(a,b),Math.min(Math.max(a,b),c));}
    private static int distance(int a,int b){return Math.max(Math.abs(Color.red(a)-Color.red(b)),Math.max(Math.abs(Color.green(a)-Color.green(b)),Math.abs(Color.blue(a)-Color.blue(b))));}
    private static final class Leaf{
        final View view;final Matrix drawing;final RectF crop;
        Leaf(View view){this(view,null,null);}
        Leaf(View view,Matrix drawing,RectF crop){this.view=view;this.drawing=drawing;this.crop=crop;}
    }
    private static final class Edge{
        final String reason;final RectF bounds;
        Edge(String reason,RectF bounds){this.reason=reason;this.bounds=bounds;}
        @Override public String toString(){return reason+" windowBounds="+bounds;}
    }
    private static final class Mask{
        Bitmap leaves,face;String description;final List<Edge> edges=new ArrayList<>();
    }
    private Matrix windowMatrix(View view){
        Matrix m=new Matrix(),decor=new Matrix(),inverse=new Matrix();view.transformMatrixToGlobal(m);
        activity.getWindow().getDecorView().transformMatrixToGlobal(decor);
        if(!decor.invert(inverse))throw new AssertionError("Singular decor transform");m.postConcat(inverse);return m;
    }
    private View original(View face){while(face instanceof NativeAdaptiveClockFace)face=((NativeAdaptiveClockFace)face).content();return face;}
    private Mask mask()throws Throwable{
        View active=clock.apply(8),vendor=original(active),decor=activity.getWindow().getDecorView();
        Mask out=new Mask();out.leaves=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);out.face=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas raw=new Canvas(out.leaves),whole=new Canvas(out.face);whole.concat(windowMatrix(active));active.draw(whole);
        List<Leaf> digits=digits(vendor);if(digits.isEmpty())throw new AssertionError("No numeric leaves for "+vendor);
        IdentityHashMap<View,Boolean> mounts=effectMounts();StringBuilder description=new StringBuilder("active=").append(active).append(" vendor=").append(vendor).append('\n');
        for(Leaf leaf:digits){
            int saved=raw.save();raw.concat(windowMatrix(leaf.view));
            if(leaf.crop!=null)raw.clipRect(leaf.crop);
            // Draw Layout directly for ordinary TextViews: ancestor and own
            // TextView clipping must not make the reference lose the same pixels.
            if(leaf.view instanceof TextView)textMask(raw,(TextView)leaf.view,leaf.drawing);else leaf.view.draw(raw);
            raw.restoreToCount(saved);description.append("numeric leaf ").append(leaf.view).append('\n');
            for(View node=leaf.view;node!=null;node=node.getParent() instanceof View?(View)node.getParent():null){
                description.append(chain(node,mounts.containsKey(node))).append('\n');
                if(node.getParent() instanceof ViewGroup&&((ViewGroup)node.getParent()).getClipChildren())edge(out,node,new RectF(0,0,node.getWidth(),node.getHeight()),"parent clipChildren "+identity(node));
                if(mounts.containsKey(node)||node.getLayerType()!=View.LAYER_TYPE_NONE||(node.getAlpha()>0&&node.getAlpha()<1&&node.hasOverlappingRendering()))edge(out,node,new RectF(0,0,node.getWidth(),node.getHeight()),"finite effect/alpha layer "+identity(node));
                Rect clip=node.getClipBounds();if(clip!=null)edge(out,node,new RectF(clip),"explicit clip "+identity(node));
                if(node instanceof ViewGroup){ViewGroup g=(ViewGroup)node;if(g.getClipToPadding()&&(g.getPaddingLeft()!=0||g.getPaddingTop()!=0||g.getPaddingRight()!=0||g.getPaddingBottom()!=0))edge(out,node,new RectF(g.getPaddingLeft(),g.getPaddingTop(),g.getWidth()-g.getPaddingRight(),g.getHeight()-g.getPaddingBottom()),"padding clip "+identity(node));}
            }
        }
        out.description=description.toString();return out;
    }
    private void textMask(Canvas canvas,TextView text,Matrix drawing)throws Throwable{
        Layout layout=text.getLayout();if(layout==null)return;
        if(drawing!=null){
            Paint paint=new Paint(text.getPaint());paint.setColor(Color.WHITE);paint.setAlpha(255);
            for(int line=0;line<layout.getLineCount();line++){
                Path path=new Path();text.getPaint().getTextPath(text.getText().toString(),layout.getLineStart(line),layout.getLineEnd(line),layout.getLineLeft(line),layout.getLineBaseline(line),path);path.transform(drawing);canvas.drawPath(path,paint);
            }
        }else{
            float offset=0;
            if(text.getClass().getName().equals("com.oplus.keyguard.clock.digital.widget.MyCustomizedTextView"))offset=((Number)field(text,"fontMetricsDrawOffsetY")).floatValue();
            int saved=canvas.save();canvas.translate(text.getCompoundPaddingLeft()-text.getScrollX(),text.getBaseline()-layout.getLineBaseline(0)-text.getScrollY()+offset);layout.draw(canvas);canvas.restoreToCount(saved);
            android.graphics.drawable.Drawable left=text.getCompoundDrawables()[0];
            if(left!=null){saved=canvas.save();canvas.translate(text.getPaddingLeft(),text.getPaddingTop()+(text.getHeight()-text.getPaddingTop()-text.getPaddingBottom()-left.getBounds().height())/2f);left.draw(canvas);canvas.restoreToCount(saved);}
        }
    }
    private List<Leaf> digits(View vendor)throws Throwable{
        List<Leaf> result=new ArrayList<>();
        if(vendor instanceof NativeHyperOsFace){for(Object item:(List<?>)field(vendor,"numbers"))if(visible((View)item,vendor))result.add(new Leaf((View)item));}
        else if(vendor instanceof NativeVivoClockFace){NativeVivoClockFace vivo=(NativeVivoClockFace)vendor;for(TextView view:vivo.materialViews())if(vivo.glassView(view)&&visible(view,vendor))result.add(new Leaf(view));}
        else if(vendor instanceof OfficialClockFace){for(TextView view:(TextView[])field(vendor,"digits"))if(visible(view,vendor))result.add(new Leaf(view));}
        else if(vendor instanceof NativeFlymePerspectiveFace){View time=(View)field(vendor,"time");result.add(new Leaf(time,(Matrix)field(vendor,"timeMatrix"),null));}
        else if(vendor instanceof FlymeAliveAodFace){RectF number=new RectF();((FlymeAliveAodFace)vendor).numberBounds(number);result.add(new Leaf(vendor,null,number));}
        else if(vendor instanceof NativeOfficialClockFaces){
            View time=(View)field(vendor,"clockTime");if(time==null)throw new AssertionError("Official time not measured");
            for(Object item:(List<?>)time.getClass().getMethod("getDigitalTimeViews").invoke(time))texts((View)item,result);
        }else throw new AssertionError("Unsupported numeric face "+vendor);
        return result;
    }
    private void texts(View view,List<Leaf> result){
        if(view.getVisibility()!=View.VISIBLE||view.getAlpha()<=0)return;
        if(view instanceof TextView)result.add(new Leaf(view));else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)texts(group.getChildAt(i),result);}
    }
    private static boolean visible(View view,View ancestor){
        for(;;){if(view.getVisibility()!=View.VISIBLE||view.getAlpha()<=0)return false;if(view==ancestor)return true;if(!(view.getParent() instanceof View))return false;view=(View)view.getParent();}
    }
    private IdentityHashMap<View,Boolean> effectMounts()throws Throwable{
        IdentityHashMap<View,Boolean> result=new IdentityHashMap<>();Object material=field(clock,"material");
        if(Boolean.TRUE.equals(field(material,"effectApplied"))){
            Map<?,?> parts=(Map<?,?>)field(material,"vivoParts");
            if(parts.isEmpty()){View target=(View)field(material,"target");if(target!=null)result.put(target,true);}else for(Object target:parts.keySet())result.put((View)target,true);
        }
        return result;
    }
    private void edge(Mask mask,View node,RectF local,String reason){windowMatrix(node).mapRect(local);mask.edges.add(new Edge(reason,local));}
    private String chain(View node,boolean effect){
        String text=identity(node)+" bounds="+node.getLeft()+","+node.getTop()+","+node.getRight()+","+node.getBottom()+" matrix="+windowMatrix(node)+" alpha="+node.getAlpha()+" layer="+node.getLayerType()+" knownEffect="+effect+" clip="+node.getClipBounds()+" outline="+node.getClipToOutline();
        if(node instanceof ViewGroup){ViewGroup group=(ViewGroup)node;text+=" children="+group.getClipChildren()+" padding="+group.getClipToPadding()+":"+group.getPaddingLeft()+","+group.getPaddingTop()+","+group.getPaddingRight()+","+group.getPaddingBottom();}
        return text;
    }
    private static String identity(View view){return view.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(view));}
    private long geometry(View view){
        long hash=view.getWidth();hash=31*hash+view.getHeight();hash=31*hash+view.getLeft();hash=31*hash+view.getTop();
        for(float value:new float[]{view.getScaleX(),view.getScaleY(),view.getTranslationX(),view.getTranslationY(),view.getPivotX(),view.getPivotY(),view.getAlpha()})hash=31*hash+Float.floatToIntBits(value);
        hash=31*hash+view.getScrollX();hash=31*hash+view.getScrollY();hash=31*hash+view.getVisibility();
        if(view instanceof TextView)hash=31*hash+((TextView)view).getText().toString().hashCode();
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)hash=31*hash+geometry(group.getChildAt(i));}
        // Also protect against a parent re-layout between the fixed-pose copies.
        if(view==clock.apply(8))for(View parent=(View)view.getParent();parent!=null;parent=parent.getParent() instanceof View?(View)parent.getParent():null){hash=31*hash+parent.getWidth();hash=31*hash+parent.getHeight();hash=31*hash+parent.getTop();hash=31*hash+parent.getLeft();}
        return hash;
    }
    private static Field declared(Object owner,String name)throws NoSuchFieldException{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(owner.getClass().getName()+"."+name);
    }
    private static Object field(Object owner,String name)throws Exception{return declared(owner,name).get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{declared(owner,name).set(owner,value);}
    private void close()throws Exception{thaw();if(fixture!=null){fixture.close();fixture=null;}else if(clock!=null)clock.apply("release",null);clock=null;if(activity!=null)activity.content.removeAllViews();}
    private void save(Bitmap image,String name)throws Exception{try(FileOutputStream file=getTargetContext().openFileOutput(name+".png",Context.MODE_PRIVATE)){image.compress(Bitmap.CompressFormat.PNG,100,file);}}
    private void write(String name,String text)throws Exception{try(FileOutputStream file=getTargetContext().openFileOutput(name,Context.MODE_PRIVATE)){file.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
    private void note(String text){trace.append(SystemClock.uptimeMillis()).append(' ').append(text).append('\n');}
}
