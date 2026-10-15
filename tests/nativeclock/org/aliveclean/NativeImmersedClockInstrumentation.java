package org.aliveclean;

import android.app.Instrumentation;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import java.util.ArrayList;

/** Native music/lyrics scene contract across every registered clock family. */
public final class NativeImmersedClockInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();ClockTestActivity activity=null;StringBuilder evidence=new StringBuilder();
        try{
            android.content.Context app=getTargetContext();
            activity=(ClockTestActivity)startActivitySync(new android.content.Intent(app,ClockTestActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            final ClockTestActivity host=activity;
            ArrayList<String> ids=new ArrayList<>();ids.add(NativeFlymeClockPlugin.ID);ids.add(NativeFlymeClockPlugin.HORIZONTAL_ID);ids.add(NativeFlymeArtworkPlugin.PERSPECTIVE);
            for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)ids.add(style.id);
            for(NativeVivoClockStyles.Style style:NativeVivoClockStyles.all(app))ids.add(style.id);
            NativeClockProvider factory=new NativeClockProvider(app,app.getAssets(),null);
            Throwable[] failure={null};float density=app.getResources().getDisplayMetrics().density;
            for(String id:ids){
                NativeOriginalClockPlugin[] current={null};
                runOnMainSync(()->{try{
                    NativeOriginalClockPlugin clock=(NativeOriginalClockPlugin)factory.apply(id);current[0]=clock;host.content.addView(clock.root);
                    state(clock,2,2,false);layout(clock);
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];
                waitForIdleSync();android.os.SystemClock.sleep(300);waitForIdleSync();
                runOnMainSync(()->{try{
                    NativeOriginalClockPlugin clock=current[0];
                    Bundle[] metrics={null};clock.test((name,args)->{if(name.equals("onClockLayoutCalculated"))metrics[0]=args.deepCopy();return null;},true);
                    state(clock,2,1,false);layout(clock);View large=clock.apply(8);
                    expectedHeight(clock,metrics,id,evidence);
                    state(clock,2,0,false);layout(clock);View small=clock.apply(8);
                    state(clock,2,2,false);layout(clock);
                    View immersed=clock.apply(8);int immersedHeight=clock.clockContainer.getHeight();
                    require(immersed!=small&&immersed!=large,"immersed still uses artwork",id);
                    require(immersed.getAlpha()==1&&clock.clockContainer.getVisibility()==View.VISIBLE,"immersed invisible",id);
                    require(metrics[0]!=null&&metrics[0].getInt("clockHeightImmersed")==immersedHeight,"wrong immersed height metrics="+(metrics[0]==null?"null":metrics[0].getInt("clockHeightImmersed"))+" actual="+immersedHeight+" root="+clock.root.getHeight(),id);
                    require(clock.root.getPaddingTop()==metrics[0].getInt("clockTopImmersed"),"wrong immersed top",id);
                    Bundle bigMetrics=metrics[0].getBundle("bigClockPositionParams");
                    require(bigMetrics!=null&&bigMetrics.getInt("bigClockCurHeight")>0
                            &&bigMetrics.getInt("bigClockMinHeight")<=bigMetrics.getInt("bigClockCurHeight"),
                            "invalid large clock height range: "+metrics[0],id);
                    // Some original horizontal faces have identical large and
                    // compact artwork. Their host eligibility is consistently
                    // small-only; asynchronous widget rows must not change it.
                    boolean supportsLarge=bigMetrics.getInt("bigClockCurHeight")>metrics[0].getInt("clockHeight");
                    clock.apply("onWidgetsPluginReady",null);
                    Bundle rowRace=new Bundle();rowRace.putInt("cardOccupiedRows",1);rowRace.putInt("originCardOccupiedRows",0);
                    clock.apply("onWidgetDataRowChanged",rowRace);
                    Bundle restoredBig=metrics[0].getBundle("bigClockPositionParams");
                    require((restoredBig.getInt("bigClockCurHeight")>metrics[0].getInt("clockHeight"))==supportsLarge,
                            "widget restore changes SystemUI large/small eligibility: "+metrics[0],id);
                    rowRace.putInt("cardOccupiedRows",0);clock.apply("onWidgetDataRowChanged",rowRace);
                    require(immersedHeight>0&&immersedHeight/density<90,"lyrics clock too tall: "+immersedHeight/density,id);
                    require(large.getVisibility()==View.GONE&&small.getVisibility()==View.GONE,"artwork overlaps lyrics clock",id);
                    Rect bounds=((Bundle)clock.apply("getClockVisibleRect",null)).getParcelable("visibleRect");
                    require(bounds!=null&&!bounds.isEmpty(),"immersed has no visible digits",id);
                    state(clock,5,2,false);layout(clock);
                    require(clock.apply(8)==immersed&&clock.root.getPaddingTop()==metrics[0].getInt("clockTopImmersed"),"state 5 lost immersed layout",id);
                    state(clock,3,1,false);layout(clock);require(clock.apply(8)!=immersed,"true AOD retained lyrics face",id);
                    state(clock,2,1,false);layout(clock);require(clock.apply(8)==large,"exit did not restore artwork",id);
                    state(clock,2,2,true);layout(clock);state(clock,1,11,true);layout(clock);
                    require(clock.clockContainer.getVisibility()==View.INVISIBLE,"unlock flashed clock",id);
                    state(clock,5,1,true);layout(clock);require(clock.clockContainer.getVisibility()==View.VISIBLE,"AOD missing after music",id);
                    state(clock,2,0,false);layout(clock);state(clock,2,2,true);layout(clock);
                    evidence.append(id).append(" immersed_height_dp=").append(immersedHeight/density).append(" OK\n");
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];waitForIdleSync();
                android.os.SystemClock.sleep(500);
                runOnMainSync(()->{try{
                    NativeOriginalClockPlugin clock=current[0];View face=clock.apply(8);
                    require(face.getAlpha()==1&&Math.abs(face.getScaleX()-1)<.01&&Math.abs(face.getScaleY()-1)<.01&&Math.abs(face.getTranslationX())<.01&&Math.abs(face.getTranslationY())<.01,"transition left stale transform alpha="+face.getAlpha()+" scale="+face.getScaleX()+","+face.getScaleY()+" translation="+face.getTranslationX()+","+face.getTranslationY(),id);
                    require(!clock.sceneAnimating(),"transition never completed",id);
                    Rect digits=clock.apply("getClockVisibleRect",null).getParcelable("visibleRect");
                    int[] origin=new int[2];clock.clockContainer.getLocationOnScreen(origin);
                    require(digits.left>=origin[0]&&digits.right<=origin[0]+clock.clockContainer.getWidth()
                            &&digits.top>=origin[1]-1&&digits.bottom<=origin[1]+clock.clockContainer.getHeight()+1,"settled ink escaped lyrics body: "+digits+" origin="+java.util.Arrays.toString(origin)+" height="+clock.clockContainer.getHeight(),id);
                    Bitmap shot=Bitmap.createBitmap(clock.root.getWidth(),clock.root.getHeight(),Bitmap.Config.ARGB_8888);
                    clock.root.draw(new Canvas(shot));
                    int[] pixels=new int[shot.getWidth()*shot.getHeight()];shot.getPixels(pixels,0,shot.getWidth(),0,0,shot.getWidth(),shot.getHeight());
                    int ink=0;for(int pixel:pixels)if(Color.alpha(pixel)>64)ink++;
                    if(ink<=100)describe(clock.root,evidence,0);
                    require(ink>100,"lyrics face draws no text",id);
                    if(id.contains("doodle"))try(java.io.OutputStream out=app.openFileOutput("immersed-doodle.png",0)){shot.compress(Bitmap.CompressFormat.PNG,100,out);}
                    shot.recycle();
                    clock.apply("release",null);host.content.removeAllViews();
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];
            }
            for(String id:new String[]{NativeFlymeClockPlugin.ID,"org.aliveclean.clock.hyperos.doodle.1",NativeVivoClockStyles.all(app)[0].id}){
                NativeClockHostFixture[] outer={null};
                try{
                    runOnMainSync(()->{try{
                        outer[0]=new NativeClockHostFixture(app,6);
                        host.content.addView((View)outer[0].root,new android.widget.FrameLayout.LayoutParams(-1,-1));
                        new NativeClockEditSession(outer[0]).select(id,NativeClockHostFixture.config(id));
                        Bundle scene=new Bundle();scene.putInt("uiState",2);scene.putInt("clockSize",2);scene.putBoolean("isAnim",false);
                        outer[0].call("onClockStateChanged",scene);
                    }catch(Throwable error){failure[0]=error;}});
                    waitForIdleSync();android.os.SystemClock.sleep(300);waitForIdleSync();
                    if(failure[0]!=null)throw failure[0];
                    runOnMainSync(()->{try{
                        NativeOriginalClockPlugin clock=outer[0].customClock;
                        require(clock.clockContainer.getHeight()/density<90&&clock.clockContainer.getHeight()>0,"outer host lost lyrics geometry",id);
                        require(clock.apply(8) instanceof NativeOfficialClockFaces,"outer host retained artwork",id);
                        View lyrics=clock.apply(8);
                        require(outer[0].read().contains(id),"lyrics changed persisted style",id);
                        Bundle scene=new Bundle();scene.putInt("uiState",2);scene.putInt("clockSize",1);scene.putBoolean("isAnim",false);
                        outer[0].call("onClockStateChanged",scene);
                        require(clock.apply(8)!=lyrics,"outer host failed to restore artwork",id);
                        evidence.append(id).append(" installed_outer_host OK\n");
                    }catch(Throwable error){failure[0]=error;}});
                    if(failure[0]!=null)throw failure[0];
                }finally{runOnMainSync(()->{try{if(outer[0]!=null)outer[0].close();host.content.removeAllViews();}catch(Exception error){failure[0]=error;}});}
                if(failure[0]!=null)throw failure[0];
            }
            result.putString("stream",evidence+"NATIVE_IMMERSED_CLOCK_OK styles="+ids.size()+"\n");finish(-1,result);
        }catch(Throwable error){result.putString("stream",evidence+android.util.Log.getStackTraceString(error));finish(0,result);}
        finally{if(activity!=null){final ClockTestActivity host=activity;runOnMainSync(host::finish);}}
    }
    private static void state(NativeOriginalClockPlugin clock,int state,int size,boolean animate){Bundle b=new Bundle();b.putInt("uiState",state);b.putInt("clockSize",size);b.putBoolean("isAnim",animate);clock.apply("onClockStateChanged",b);}
    private static void expectedHeight(NativeOriginalClockPlugin clock,Bundle[] metrics,String id,StringBuilder evidence){
        require(metrics[0]!=null,"missing natural geometry",id);
        Bundle natural=metrics[0].getBundle("bigClockPositionParams").deepCopy();
        int cur=natural.getInt("bigClockContentHeightAtCurFont"),min=natural.getInt("bigClockContentHeightAtMinFont");
        require(cur>0&&min>0&&min<=cur,"invalid natural content range "+natural,id);
        int full=clock.clockContainer.getHeight();
        boolean adaptable=cur-min>1;
        for(int repeat=0;repeat<3;repeat++){
            int target=min+(cur-min)/2;
            Bundle request=new Bundle();request.putInt("expectedClockHeight",natural.getInt("bigClockTop")+natural.getInt("bigClockWidgetsHeight")+target);
            clock.apply("onClockExpectedHeightChange",request);layout(clock);
            int actual=clock.clockContainer.getHeight();
            if(adaptable){
                require(actual<full&&Math.abs(actual-target)<=1,"expected height ignored: natural="+full+" target="+target+" actual="+actual,id);
                Rect ink=clock.apply("getClockVisibleRect",null).getParcelable("visibleRect");int[] origin=new int[2];clock.clockContainer.getLocationOnScreen(origin);
                require(ink!=null&&!ink.isEmpty()&&ink.top>=origin[1]-1&&ink.bottom<=origin[1]+actual+1,"adapted glyphs escape content: "+ink+" actual="+actual,id);
            }else require(actual==full,"fixed artwork unexpectedly shrank",id);
            Bundle after=metrics[0].getBundle("bigClockPositionParams");
            for(String key:new String[]{"bigClockCurHeight","bigClockMinHeight","bigClockContentHeightAtCurFont","bigClockContentHeightAtMinFont"})
                require(after.getInt(key)==natural.getInt(key),"adaptive callback changed natural endpoint "+key+": "+natural+" -> "+after,id);
            request.putInt("expectedClockHeight",Integer.MAX_VALUE);clock.apply("onClockExpectedHeightChange",request);layout(clock);
            require(clock.clockContainer.getHeight()==full,"unlimited space failed to restore natural artwork",id);
        }
        evidence.append(id).append(" expected_height_range=").append(min).append("..").append(cur)
                .append(" adaptable=").append(adaptable).append(" endpoints_stable=true\n");
    }
    private static void layout(NativeOriginalClockPlugin clock){int w=clock.root.getResources().getDisplayMetrics().widthPixels;clock.root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));clock.root.layout(0,0,w,clock.root.getMeasuredHeight());}
    private static void require(boolean value,String reason,String id){if(!value)throw new AssertionError(id+": "+reason);}
    private static void describe(View v,StringBuilder out,int depth){
        if(depth==2&&!(v instanceof NativeOfficialClockFaces))return;
        out.append(depth).append(' ').append(v.getClass().getName()).append(" vis=").append(v.getVisibility()).append(" xy=").append(v.getX()).append(',').append(v.getY()).append(" size=").append(v.getWidth()).append('x').append(v.getHeight()).append(" alpha=").append(v.getAlpha());
        if(v instanceof android.widget.TextView)out.append(" text=").append(((android.widget.TextView)v).getText()).append(" color=").append(Integer.toHexString(((android.widget.TextView)v).getCurrentTextColor()));
        out.append('\n');if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++)describe(((android.view.ViewGroup)v).getChildAt(i),out,depth+1);
    }
}
