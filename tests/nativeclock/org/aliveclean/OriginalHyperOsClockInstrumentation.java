package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Dependency probe using unmodified HyperOS classes/resources, only inside the test APK. */
public final class OriginalHyperOsClockInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        ClockTestActivity[] activity = new ClockTestActivity[1];
        try {
            Context app = getTargetContext();
            stage("runtime copy");
            File file=NativeClockRuntime.unpack(app,"hyperos");
            if(file.canWrite())throw new AssertionError("runtime is writable");
            if(!file.equals(NativeClockRuntime.unpack(app,"hyperos")))throw new AssertionError("runtime cache changed");
            stage("isolated runtime load");
            OfficialHyperOsUi source = new OfficialHyperOsUi(app, file);
            stage("test activity launch");
            activity[0] = (ClockTestActivity) startActivitySync(new android.content.Intent(app, ClockTestActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            stage("test activity attached");
            StringBuilder log = new StringBuilder();
            for(int mode=0;mode<5;mode++)probe(source, app, activity[0].content, log,mode);
            runOnMainSync(() -> activity[0].finish());
            result.putString("stream", log.toString()); finish(-1, result);
        } catch(Throwable error) {
            if(activity[0] != null) runOnMainSync(() -> activity[0].finish());
            result.putString("stream", android.util.Log.getStackTraceString(error)); finish(0,result);
        }
    }
    private void probe(OfficialHyperOsUi source, Context app, FrameLayout window, StringBuilder log,int mode) {
        String[] names={"all_in_one:1", "all_in_one:2", "all_in_one:3", "classic_plus:21", "classic:21", "rhombus:4", "eastern_a:1", "eastern_b:2", "eastern_c:1"};
        int nativeWidth=source.getResources().getDisplayMetrics().widthPixels;
        int nativeHeight=source.getResources().getDisplayMetrics().heightPixels;
        int cellHeight=(int)Math.ceil(nativeHeight*540f/nativeWidth)+60;
        Bitmap bitmap=Bitmap.createBitmap(1620,cellHeight*3,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.BLACK);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setTextSize(22);text.setColor(Color.LTGRAY);
        int i=0;
        for(String name:names) {
            int column=i%3, row=i/3; i++;
            OfficialHyperOsClockFace[] current=new OfficialHyperOsClockFace[1];
            Throwable[] failure=new Throwable[1];
            runOnMainSync(()->{
                try {
                    String[] parts=name.split(":");
                    current[0]=new OfficialHyperOsClockFace(source,parts[0],Integer.parseInt(parts[1]));
                    current[0].scene(mode!=0&&mode!=4,mode==2||mode==4,mode==3);
                    window.addView(current[0]);
                    current[0].refresh("Asia/Shanghai",true);
                    measure(current[0],nativeWidth,nativeHeight);
                }catch(Throwable error){failure[0]=error;}
            });
            // Original AllInOneSingleClock posts its AOD scale correction after
            // attachment. Capture only after that runnable and layout have run.
            waitForIdleSync();
            runOnMainSync(()->{
            int saved=canvas.save();canvas.translate(column*540,row*cellHeight); canvas.clipRect(0,0,540,cellHeight);
            canvas.drawText(name,30,35,text);
            try {
                stage(name+" scene="+mode+" create");
                if(failure[0]!=null)throw failure[0];
                String[] parts=name.split(":");
                OfficialHyperOsClockFace parent=current[0];
                if(parent.displayType()!=(mode==0?8:mode==1?9:mode==2?11:mode==3?40:10))throw new AssertionError("original scene flags incorrect");
                boolean layered=mode==0||mode==3;
                int expectedLayers=layered&&(parts[0].equals("all_in_one")||parts[0].equals("eastern_a")||parts[0].equals("eastern_c"))?2:1;
                if(parent.getChildCount()!=expectedLayers)throw new AssertionError("duplicate or missing original foreground layer");
                View face=parent.getChildAt(0);
                for(int layer=0;layer<parent.getChildCount();layer++){
                    View original=parent.getChildAt(layer);
                    log.append("METRICS ").append(name).append(" scene=").append(mode).append(" layer=").append(layer);
                    for(String method:new String[]{"getClockHeight","getClockVisibleHeight","getTopMargin","getNotificationClockTop","getNotificationClockBottom"})
                        log.append(' ').append(method).append('=').append(original.getClass().getMethod(method).invoke(original));
                    log.append('\n');
                }
                stage(name+" measure");
                measure(parent,nativeWidth,nativeHeight);
                stage(name+" draw");
                Bitmap tile=Bitmap.createBitmap(nativeWidth,nativeHeight,Bitmap.Config.ARGB_8888);
                Canvas tileCanvas=new Canvas(tile);tileCanvas.drawColor(Color.BLACK);parent.draw(tileCanvas);
                int ink=0;
                int[] pixels=new int[nativeWidth];
                for(int y=0;y<nativeHeight;y+=2) {
                    tile.getPixels(pixels,0,nativeWidth,0,y,nativeWidth,1);
                    for(int x=0;x<nativeWidth;x+=2)if((pixels[x]&0xFFFFFF)!=0)ink++;
                }
                if(ink<50)throw new IllegalStateException("blank original renderer, ink="+ink);
                canvas.translate(0,60);canvas.scale(540f/nativeWidth,540f/nativeWidth);canvas.drawBitmap(tile,0,0,null);tile.recycle();
                log.append("ORIGINAL_VIEW_DRAWN ").append(name).append(" scene=").append(mode).append(" ").append(face.getClass().getName()).append(" height=").append(face.getMeasuredHeight()).append(" ink=").append(ink).append('\n');
                describe(parent, log);
                requireDate(parent);
                parent.refresh("America/New_York",false);
                parent.refresh("Asia/Shanghai",true);
                parent.close();
                stage(name+" done");
            } catch(Throwable error) {
                log.append("ORIGINAL_VIEW_BLOCKED ").append(name).append('\n').append(android.util.Log.getStackTraceString(error)).append('\n');
                canvas.drawText(error.getClass().getSimpleName(),30,100,text);
            }
            window.removeAllViews();
            canvas.restoreToCount(saved);
            });
        }
        try(FileOutputStream out=new FileOutputStream(new File(app.getFilesDir(),"hyperos-originals-"+mode+".png"))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
        catch(Exception error){log.append(error);}
        bitmap.recycle();
    }
    private static void measure(View parent,int width,int height){
        parent.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        parent.layout(0,0,parent.getMeasuredWidth(),parent.getMeasuredHeight());
    }
    private static void requireDate(View view) {
        if(view instanceof android.widget.TextView && view.getVisibility()==View.VISIBLE && view.getId()!=View.NO_ID
                && "text_area".equals(view.getResources().getResourceEntryName(view.getId()))
                && ((android.widget.TextView)view).getText().length()==0)
            throw new AssertionError("visible original date is empty");
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)requireDate(((ViewGroup)view).getChildAt(n));
    }
    private void stage(String message) {
        Bundle status=new Bundle();status.putString("stream",message+"\n");sendStatus(1,status);
        android.util.Log.i("OpenAliveClockProbe",message);
    }
    private static void describe(View view, StringBuilder log) {
        if(view instanceof android.widget.TextView) {
            android.widget.TextView text=(android.widget.TextView)view;
            log.append("  TEXT ").append(view.getId() == View.NO_ID ? "no-id" : view.getResources().getResourceEntryName(view.getId()))
                    .append(" value=").append(text.getText()).append(" visibility=").append(view.getVisibility())
                    .append(" bounds=").append(view.getLeft()).append(',').append(view.getTop()).append(',')
                    .append(view.getRight()).append(',').append(view.getBottom()).append('\n');
        }
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)describe(((ViewGroup)view).getChildAt(n),log);
    }
}
