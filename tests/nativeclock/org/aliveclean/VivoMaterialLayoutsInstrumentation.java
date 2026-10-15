package org.aliveclean;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Hardware coverage regression for the original nested/transformed Vivo layouts. */
public final class VivoMaterialLayoutsInstrumentation extends Instrumentation {
    private ClockTestActivity activity;
    private View face;
    private NativeVivoClockFace vivoFace;
    private NativeClockHostFixture host;
    private boolean useHost;
    private boolean legacyMetrics;
    private NativeVivoClockMaterial material;
    private String only;
    private final StringBuilder report=new StringBuilder();
    @Override public void onCreate(Bundle args){super.onCreate(args);only=args==null?null:args.getString("style");useHost=args!=null&&"true".equals(args.getString("host"));legacyMetrics=args!=null&&"true".equals(args.getString("legacy_metrics"));start();}
    private interface Work{void run()throws Exception;}
    private void main(Work work)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);}
    private void settle()throws Exception{java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);runOnMainSync(()->activity.content.postDelayed(ready::countDown,900));if(!ready.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("frame timeout");}
    private void save(Bitmap image,String name)throws Exception{try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}}
    @Override public void onStart(){Bundle out=new Bundle();boolean success=false;try{
        activity=(ClockTestActivity)startActivitySync(new Intent(getTargetContext(),ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        OfficialVivoClockUi original=new OfficialVivoClockUi(activity);
        Bitmap wallpaper=Bitmap.createBitmap(96,192,Bitmap.Config.ARGB_8888);Paint p=new Paint();p.setShader(new LinearGradient(0,0,96,192,new int[]{0xff143460,0xffae5420,0xff49694d},null,Shader.TileMode.CLAMP));new Canvas(wallpaper).drawRect(0,0,96,192,p);
        int failures=0,count=0;
        java.util.List<String> styles=new java.util.ArrayList<>();
        for(NativeVivoClockStyles.Style style:NativeVivoClockStyles.all(activity))styles.add(style.id);
        for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)styles.add(style.id);
        for(String id:styles){
            String key=id.substring("org.aliveclean.clock.".length());
            if(only!=null&&!java.util.Arrays.stream(only.split(",")).anyMatch(id::endsWith))continue;
            main(()->{
                activity.content.removeAllViews();View view;
                java.util.Calendar time=java.util.Calendar.getInstance();time.set(2026,8,27,13,4,0);
                if(useHost){
                    host=new NativeClockHostFixture(activity);
                    host.write(new org.json.JSONObject(host.read()).put("pkg",id).put("clockStyleConfig",NativeClockHostFixture.config(id)).toString());
                    Bundle tick=new Bundle();tick.putLong("time",time.getTimeInMillis());host.customClock.apply("setTime",tick);
                    face=host.customClock.apply(8);view=(View)host.root;
                }else if(NativeVivoClockStyles.contains(id)){NativeVivoClockFace vivo=new NativeVivoClockFace(activity,original,NativeVivoClockStyles.find(activity,id),false,false);vivo.update(time.getTimeInMillis(),time.getTimeZone(),true);face=vivo;view=face;}
                else{NativeHyperOsFace hyper=new NativeHyperOsFace(OfficialHyperOsUi.open(activity),NativeHyperOsStyles.find(id),false,false);hyper.refresh("Asia/Shanghai",true);face=hyper;view=face;}
                View content=face;
                while(content instanceof NativeAdaptiveClockFace)content=((NativeAdaptiveClockFace)content).content();
                vivoFace=null;
                if(NativeVivoClockStyles.contains(id)){
                    if(!(content instanceof NativeVivoClockFace))throw new AssertionError("Vivo material metadata unavailable for "+id+": "+content);
                    vivoFace=(NativeVivoClockFace)content;
                }else if(!(content instanceof NativeHyperOsFace))throw new AssertionError("HyperOS face unavailable for "+id+": "+content);
                if(legacyMetrics&&vivoFace!=null)for(TextView label:vivoFace.materialViews())label.setFallbackLineSpacing(false);
                FrameLayout scaled=new FrameLayout(activity);scaled.setClipChildren(false);scaled.setClipToPadding(false);scaled.addView(view,new FrameLayout.LayoutParams(-1,-1));
                activity.content.addView(scaled,new FrameLayout.LayoutParams(-1,-1));scaled.setPivotX(0);scaled.setPivotY(0);scaled.setScaleX(.65f);scaled.setScaleY(.65f);scaled.setTranslationX(60);scaled.setTranslationY(140);
            });settle();
            if(only!=null&&vivoFace!=null)main(()->report.append(vivoFace.geometryReport()).append('\n'));
            if(vivoFace!=null)main(()->{
                NativeVivoClockFace vivo=vivoFace;
                for(TextView label:vivo.materialViews())if(label.getVisibility()==View.VISIBLE&&vivo.glassView(label)){
                    Rect ink=new Rect();label.getPaint().getTextBounds(label.getText().toString(),0,label.length(),ink);
                    if(ink.bottom+label.getBaseline()>label.getHeight()+2)
                        throw new AssertionError("Glyph extends below TextView: "+ink+" baseline="+label.getBaseline()+" height="+label.getHeight());
                }
            });
            Bitmap plain=getUiAutomation().takeScreenshot();
            for(boolean glass:new boolean[]{true,false}){
                main(()->{if(useHost){Bundle args=new Bundle();args.putParcelable("wallpaperBitmap",wallpaper);host.customClock.apply("setWallpaperBitmap",args);args=new Bundle();args.putInt("mode",glass?7:8);host.customClock.apply("openAliveEditColor",args);}else{if(material==null)material=new NativeVivoClockMaterial(face,activity.getAssets());material.mode(glass);material.update(wallpaper,new RectF(0,100,face.getWidth(),face.getHeight()+100));}});settle();
                // Glass builds its distance field asynchronously. Check readiness,
                // rather than treating an arbitrary first-frame delay as failure.
                long deadline=SystemClock.uptimeMillis()+5000;
                boolean[] ready={false};
                do{main(()->ready[0]=useHost?host.customClock.materialApplied():material.applied());if(ready[0])break;settle();}while(SystemClock.uptimeMillis()<deadline);
                Bitmap rendered=getUiAutomation().takeScreenshot();if(plain==null||rendered==null)throw new AssertionError("screenshot unavailable");
                RectF coverage=new RectF();
                main(()->{coverage.set(0,0,face.getWidth(),face.getHeight());Matrix transform=new Matrix();face.transformMatrixToGlobal(transform);transform.mapRect(coverage);});
                int expected=0,filled=0,colored=0,datePixels=0;
                if(useHost&&vivoFace!=null){
                    java.util.List<Rect> dates=new java.util.ArrayList<>();
                    java.util.List<RectF> digits=new java.util.ArrayList<>();
                    main(()->{NativeVivoClockFace v=vivoFace;
                        for(TextView label:v.materialViews())if(label.getVisibility()==View.VISIBLE){
                            if(v.glassView(label)){
                                Rect ink=new Rect();label.getPaint().getTextBounds(label.getText().toString(),0,label.length(),ink);
                                RectF rect=new RectF(ink);rect.offset(label.getCompoundPaddingLeft(),label.getBaseline());
                                if(label.getLayout()!=null)rect.offset(label.getLayout().getLineLeft(0),0);
                                Matrix transform=new Matrix();label.transformMatrixToGlobal(transform);transform.mapRect(rect);digits.add(rect);
                            }else{Rect rect=new Rect();if(label.getGlobalVisibleRect(rect))dates.add(rect);}
                        }
                    });
                    if(digits.isEmpty()||dates.isEmpty())throw new AssertionError("Vivo digit/date coverage unavailable for "+id+": digits="+digits.size()+" dates="+dates.size());
                    for(Rect rect:dates){
                        int before=0,after=0;
                        for(int y=Math.max(0,rect.top);y<Math.min(plain.getHeight(),rect.bottom);y++)for(int x=Math.max(0,rect.left);x<Math.min(plain.getWidth(),rect.right);x++){
                            // S9's week view bounds overlap the first digit. Only
                            // date pixels outside digit ink can stay unchanged.
                            boolean digit=false;for(RectF number:digits)if(number.contains(x,y)){digit=true;break;}if(digit)continue;
                            int a=plain.getPixel(x,y),b=rendered.getPixel(x,y);
                            if(Color.red(a)>180&&Color.green(a)>180&&Color.blue(a)>180){before++;if(Color.red(b)>180&&Color.green(b)>180&&Color.blue(b)>180)after++;}
                        }
                        datePixels+=before;
                        if(before>10&&after<before*.95)throw new AssertionError("Date changed with digit material: "+before+" -> "+after);
                    }
                    if(datePixels<=10)throw new AssertionError("No comparable Vivo date pixels for "+id+": "+datePixels);
                }
                for(int y=Math.max(0,(int)Math.floor(coverage.top));y<Math.min(plain.getHeight(),(int)Math.ceil(coverage.bottom));y++)for(int x=Math.max(0,(int)Math.floor(coverage.left));x<Math.min(plain.getWidth(),(int)Math.ceil(coverage.right));x++){
                    int a=plain.getPixel(x,y),b=rendered.getPixel(x,y);if(Color.red(a)<180||Color.green(a)<180||Color.blue(a)<180)continue;expected++;
                    if(Math.max(Color.red(b),Math.max(Color.green(b),Color.blue(b)))>16)filled++;
                    if(Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b))>35)colored++;
                }
                boolean applied=useHost?host.customClock.materialApplied():material.applied();
                String line=key+" glass="+glass+" size="+face.getWidth()+"x"+face.getHeight()+" expected="+expected+" filled="+filled+" colored="+colored+" datePixels="+datePixels+" applied="+applied+" layout="+face.isLayoutRequested();report.append(line).append('\n');
                if(expected<100||filled<expected*.95||colored<expected*.5||!applied){failures++;save(plain,key+"-plain");save(rendered,key+(glass?"-glass":"-blur"));}
                if(only!=null){save(plain,key+"-plain");save(rendered,key+(glass?"-glass":"-blur"));}
                rendered.recycle();
            }
            main(()->{if(material!=null){material.close();material=null;}if(host!=null){host.close();host=null;}else if(face instanceof AutoCloseable)((AutoCloseable)face).close();});plain.recycle();count++;
            try(FileOutputStream progress=new FileOutputStream(new File(getTargetContext().getFilesDir(),"material-layout-progress.txt"))){progress.write(report.toString().getBytes("UTF-8"));}
        }
        report.append("VIVO_MATERIAL_LAYOUTS count=").append(count).append(" failures=").append(failures);out.putString("stream",report.toString());success=count>0&&failures==0;
    }catch(Throwable e){out.putString("stream",report+android.util.Log.getStackTraceString(e));}finally{runOnMainSync(()->{if(material!=null)material.close();try{if(host!=null)host.close();}catch(Exception ignored){}if(activity!=null)activity.finish();});}finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
