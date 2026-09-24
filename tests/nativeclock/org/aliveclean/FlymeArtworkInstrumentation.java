package org.aliveclean;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.TimeZone;

/** Original artistic face geometry, without changing the user's system clock. */
public final class FlymeArtworkInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        ClockTestActivity activity=null;Bundle result=new Bundle();StringBuilder log=new StringBuilder();int count=0;
        try{
            Bundle stage=new Bundle();stage.putString("stream","test activity launch\n");sendStatus(1,stage);
            activity=(ClockTestActivity)startActivitySync(new Intent(getTargetContext(),ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final ClockTestActivity window=activity;int width=activity.getResources().getDisplayMetrics().widthPixels;
            for(int scene=0;scene<4;scene++){
                int mode=scene;NativeFlymePerspectiveFace[] face={null};Throwable[] failure={null};
                runOnMainSync(()->{try{
                    face[0]=new NativeFlymePerspectiveFace(NativeClockProvider.resourceContext(window,window.getAssets()),mode>=2,(mode&1)!=0);
                    window.content.addView(face[0],new android.widget.FrameLayout.LayoutParams(-1,-2));
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];
                for(int hour:new int[]{0,1,8,12,18,23}){
                    runOnMainSync(()->{
                        Calendar calendar=Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));calendar.set(2026,8,24,hour,38);
                        face[0].update(calendar.getTimeInMillis(),calendar.getTimeZone(),true);
                    });waitForIdleSync();
                    runOnMainSync(()->{try{
                        NativeFlymePerspectiveFace current=face[0];
                        current.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                        current.layout(0,0,width,current.getMeasuredHeight());
                        RectF ink=new RectF();current.numberBounds(ink);
                        if(ink.isEmpty()||ink.left < -1||ink.top < -1||ink.right>width+1||ink.bottom>current.getHeight()+1)
                            throw new AssertionError("Clipped original perspective geometry "+ink+" / "+current.getHeight());
                        Bitmap raster=Bitmap.createBitmap(width,current.getHeight(),Bitmap.Config.ARGB_8888);current.draw(new Canvas(raster));
                        int pixels=0;Rect visible=new Rect();
                        for(int y=0;y<raster.getHeight();y++)for(int x=0;x<width;x++)if(Color.alpha(raster.getPixel(x,y))>16){pixels++;visible.union(x,y,x+1,y+1);}
                        if(pixels<50)throw new AssertionError("Empty original perspective drawing");
                        // The time is the uppermost content; its reported ink must match the raster.
                        if(Math.abs(visible.top-ink.top)>4)throw new AssertionError("Raster/matrix mismatch "+visible+" vs "+ink);
                        if(hour==18){
                            Bitmap preview=Bitmap.createBitmap(540,900,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(preview);
                            float scale=Math.min(540f/width,900f/current.getHeight());canvas.translate((540-width*scale)/2,0);canvas.scale(scale,scale);current.draw(canvas);
                            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"flyme-perspective-"+mode+".png"))){preview.compress(Bitmap.CompressFormat.PNG,100,out);}preview.recycle();
                        }
                        log.append("PERSPECTIVE_OK scene=").append(mode).append(" hour=").append(hour).append(" ink=").append(ink).append(" height=").append(current.getHeight()).append('\n');raster.recycle();
                    }catch(Throwable error){failure[0]=error;}});
                    if(failure[0]!=null)throw failure[0];count++;
                }
                runOnMainSync(()->{face[0].close();window.content.removeAllViews();});
            }
            android.content.Context host=HyperOsWrappedInstrumentation.offlineHost(activity);
            NativeOriginalClockPlugin[] plugin={null};Throwable[] failure={null};
            runOnMainSync(()->{try{
                plugin[0]=(NativeOriginalClockPlugin)new NativeClockProvider(host,host.getAssets(),null).apply(NativeFlymeArtworkPlugin.PERSPECTIVE);
                window.content.addView(plugin[0].apply(1));
            }catch(Throwable error){failure[0]=error;}});
            if(failure[0]!=null)throw failure[0];waitForIdleSync();
            for(int state:new int[]{2,3,5,2})for(int size:new int[]{1,2,1}){
                runOnMainSync(()->{try{
                    Bundle change=new Bundle();change.putInt("uiState",state);change.putInt("clockSize",size);plugin[0].apply("onClockStateChanged",change);
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];waitForIdleSync();
                runOnMainSync(()->{try{
                    View root=plugin[0].apply(1);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));root.layout(0,0,width,root.getMeasuredHeight());
                    Rect ink=plugin[0].apply("getClockVisibleRect",null).getParcelable("visibleRect");
                    if(ink==null||ink.isEmpty())throw new AssertionError("Production artwork factory lost digits");
                    String saved=plugin[0].apply("getStyleData",null).getString("styleData");Bundle restore=new Bundle();restore.putString("styleData",saved);plugin[0].apply("setStyleData",restore);
                    if(!NativeFlymeArtworkPlugin.PERSPECTIVE.equals(new org.json.JSONObject(saved).getString("style"))||!saved.equals(plugin[0].apply("getStyleData",null).getString("styleData")))throw new AssertionError("Artwork restore changed style");
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];
            }
            runOnMainSync(()->{plugin[0].apply("release",null);window.content.removeAllViews();});
            log.append("ARTWORK_FACTORY_OK transitions=12 restore=true release=true\n");
            result.putString("stream",log+"FLYME_ARTWORK_OK checks="+count+"\n");finish(-1,result);
        }catch(Throwable error){result.putString("stream",log+android.util.Log.getStackTraceString(error));finish(0,result);}
        finally{if(activity!=null){ClockTestActivity done=activity;runOnMainSync(done::finish);}}
    }
}
