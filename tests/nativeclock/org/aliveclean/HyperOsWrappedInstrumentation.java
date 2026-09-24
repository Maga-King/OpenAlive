package org.aliveclean;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;

/** Original layouts after adapting outer geometry; works without ColorOS packages. */
public final class HyperOsWrappedInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        ClockTestActivity activity=null;Bundle result=new Bundle();StringBuilder log=new StringBuilder();int passed=0;
        try{
            Bundle stage=new Bundle();stage.putString("stream","test activity launch\n");sendStatus(1,stage);
            activity=(ClockTestActivity)startActivitySync(new Intent(getTargetContext(),ClockTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final ClockTestActivity window=activity;
            OfficialHyperOsUi runtime=OfficialHyperOsUi.open(NativeClockProvider.resourceContext(activity,activity.getAssets()));
            int width=activity.getResources().getDisplayMetrics().widthPixels;
            for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL){
                for(int scene=0;scene<4;scene++){
                    final int mode=scene;NativeHyperOsFace[] face={null};Throwable[] failure={null};
                    runOnMainSync(()->{try{
                        face[0]=new NativeHyperOsFace(runtime,style,mode>=2,(mode&1)!=0);
                        window.content.addView(face[0],new android.widget.FrameLayout.LayoutParams(-1,-2));
                        face[0].refresh("Asia/Shanghai",true);
                        if(style.template.equals("all_in_one")&&mode==0){
                            java.lang.reflect.Field originalField=NativeHyperOsFace.class.getDeclaredField("original");originalField.setAccessible(true);
                            OfficialHyperOsClockFace original=(OfficialHyperOsClockFace)originalField.get(face[0]);
                            for(int layer=0;layer<original.getChildCount();layer++){
                                View renderer=original.getChildAt(layer);
                                Object info=renderer.getClass().getMethod("getClockStyleInfo").invoke(renderer);
                                if(!Boolean.valueOf(style.vertical).equals(info.getClass().getMethod("isDoubleRow").invoke(info)))
                                    throw new AssertionError("Original double-row preset not retained");
                            }
                        }
                    }catch(Throwable error){failure[0]=error;}});
                    waitForIdleSync();
                    runOnMainSync(()->{try{
                        if(failure[0]!=null)return;
                        NativeHyperOsFace current=face[0];
                        current.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                        current.layout(0,0,width,current.getMeasuredHeight());
                        RectF ink=new RectF();current.numberBounds(ink);
                        if(ink.isEmpty()){
                            java.lang.reflect.Field numbers=NativeHyperOsFace.class.getDeclaredField("numbers");numbers.setAccessible(true);
                            for(Object number:(java.util.List<?>)numbers.get(current)){
                                log.append("EMPTY_NUMBER ").append(number.getClass().getName());
                                for(String key:new String[]{"mOriginLeftEmpty","mOriginRightEmpty","mOriginImageWidth","mOriginImageHeight","mScale","mTranslateX","mTranslateY","mVectorDrawable"}){
                                    try{log.append(' ').append(key).append('=').append(number.getClass().getField(key).get(number));}catch(NoSuchFieldException ignore){}
                                }log.append('\n');
                            }
                        }
                        if(ink.isEmpty()||ink.left < -2||ink.right>width+2||ink.top < -2||ink.bottom>current.getHeight()+2)
                            throw new AssertionError("numeric bounds clipped: "+ink+" size="+width+"x"+current.getHeight());
                        Bitmap raster=Bitmap.createBitmap(width,current.getHeight(),Bitmap.Config.ARGB_8888);
                        current.draw(new Canvas(raster));
                        int pixels=0;for(int y=0;y<raster.getHeight();y+=4)for(int x=0;x<width;x+=4)if(Color.alpha(raster.getPixel(x,y))>0)pixels++;
                        if(pixels<25)throw new AssertionError("empty original face");
                        if(mode==0){
                            Bitmap preview=Bitmap.createBitmap(540,900,Bitmap.Config.ARGB_8888);
                            Canvas canvas=new Canvas(preview);float scale=Math.min(540f/width,900f/current.getHeight());
                            canvas.translate((540-width*scale)/2,0);canvas.scale(scale,scale);current.draw(canvas);
                            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),style.preview))){preview.compress(Bitmap.CompressFormat.PNG,100,out);}preview.recycle();
                        }
                        raster.recycle();current.color(0xff60a0ff);current.refresh("America/New_York",false);current.refresh("Asia/Shanghai",true);
                        log.append("WRAPPED_OK ").append(style.id).append(" scene=").append(mode).append(" height=").append(current.getHeight()).append(" ink=").append(ink).append('\n');
                    }catch(Throwable error){failure[0]=error;}finally{if(face[0]!=null)face[0].close();window.content.removeAllViews();}});
                    if(failure[0]!=null)log.append("WRAPPED_FAILED ").append(style.id).append(" scene=").append(scene).append('\n').append(android.util.Log.getStackTraceString(failure[0])).append('\n');
                    else passed++;
                }
            }
            if(passed!=NativeHyperOsStyles.ALL.length*4)throw new AssertionError("passed="+passed+" expected="+NativeHyperOsStyles.ALL.length*4);
            // Exercise the production factory and protocol separately from the
            // individual faces. Geometry comes from the captured ColorOS APK;
            // this is still an isolated test, not a live SystemUI acceptance.
            android.content.Context host=offlineHost(activity);
            for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL){
                Throwable[] failure={null};NativeOriginalClockPlugin[] plugin={null};
                runOnMainSync(()->{try{
                    NativeClockProvider factory=new NativeClockProvider(host,host.getAssets(),null);
                    plugin[0]=(NativeOriginalClockPlugin)factory.apply(style.id);
                    window.content.addView(plugin[0].apply(1));
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];
                waitForIdleSync();
                for(int state:new int[]{2,3,5,2})for(int size:new int[]{1,2,1}){
                    runOnMainSync(()->{try{
                        Bundle change=new Bundle();change.putInt("uiState",state);change.putInt("clockSize",size);
                        plugin[0].apply("onClockStateChanged",change);
                        Bundle tick=new Bundle();tick.putLong("time",System.currentTimeMillis());plugin[0].apply("setTime",tick);
                    }catch(Throwable error){failure[0]=error;}});
                    if(failure[0]!=null)throw failure[0];
                    waitForIdleSync();
                    runOnMainSync(()->{try{
                        View root=plugin[0].apply(1);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));root.layout(0,0,width,root.getMeasuredHeight());
                        Rect ink=plugin[0].apply("getClockVisibleRect",null).getParcelable("visibleRect");
                        if(ink==null||ink.isEmpty())throw new AssertionError("Production factory lost numeric bounds: "+style.id+" state="+state+" size="+size);
                        String saved=plugin[0].apply("getStyleData",null).getString("styleData");
                        if(!style.id.equals(new org.json.JSONObject(saved).getString("style")))throw new AssertionError("Factory lost style ID");
                        Bundle restore=new Bundle();restore.putString("styleData",saved);plugin[0].apply("setStyleData",restore);
                        if(!saved.equals(plugin[0].apply("getStyleData",null).getString("styleData")))throw new AssertionError("Configuration round trip changed style");
                    }catch(Throwable error){failure[0]=error;}});
                    if(failure[0]!=null)throw failure[0];
                }
                runOnMainSync(()->{plugin[0].apply("release",null);window.content.removeAllViews();});
                log.append("FACTORY_OK ").append(style.id).append(" transitions=12 restore=true release=true\n");
            }
            log.append("HYPEROS_WRAPPED_OK scenes=").append(passed).append('\n');result.putString("stream",log.toString());finish(-1,result);
        }catch(Throwable error){result.putString("stream",log+android.util.Log.getStackTraceString(error));finish(0,result);}
        finally{if(activity!=null){final ClockTestActivity done=activity;runOnMainSync(done::finish);}}
    }
    static android.content.Context offlineHost(android.content.Context host)throws Exception{
        File apk=new File(host.getCodeCacheDir(),"captured-coloros-clock.apk");
        if(!apk.exists())try(java.io.InputStream in=host.getAssets().open("test-clock-host.apk");FileOutputStream out=new FileOutputStream(apk)){
            if(!apk.setReadOnly())throw new java.io.IOException("Cannot protect test resource archive");
            byte[] data=new byte[65536];for(int n;(n=in.read(data))!=-1;)out.write(data,0,n);
        }
        android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo();info.packageName="com.oplus.keyguard.personality.clocks";
        info.sourceDir=apk.getAbsolutePath();info.publicSourceDir=info.sourceDir;info.uid=android.os.Process.myUid();
        android.content.res.Resources resources=host.getPackageManager().getResourcesForApplication(info);
        android.content.Context geometry=new android.content.ContextWrapper(host){@Override public android.content.res.Resources getResources(){return resources;}};
        return new android.content.ContextWrapper(host){
            @Override public android.content.Context createPackageContext(String pkg,int flags)throws android.content.pm.PackageManager.NameNotFoundException{
                return info.packageName.equals(pkg)?geometry:super.createPackageContext(pkg,flags);
            }
        };
    }
}
