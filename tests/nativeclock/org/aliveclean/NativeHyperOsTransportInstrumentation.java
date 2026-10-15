package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;

/** Real original controls in the installed ColorOS container, without applying a clock. */
public final class NativeHyperOsTransportInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();ClockTestActivity[] activity={null};StringBuilder evidence=new StringBuilder();
        try{
            Context app=getTargetContext();
            Bundle stage=new Bundle();stage.putString("stream","test activity launch\n");sendStatus(1,stage);
            activity[0]=(ClockTestActivity)startActivitySync(new android.content.Intent(app,ClockTestActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL){
                String id=style.id;
                NativeOriginalClockPlugin[] clock={null};Object[] container={null};Throwable[] failure={null};
                runOnMainSync(()->{try{
                    Context base=app.createPackageContext("com.oplus.keyguard.clock.base",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
                    Class<?> type=base.getClassLoader().loadClass("com.oplus.keyguard.ui.ClockPluginContainer");
                    container[0]=type.getConstructor(Context.class,Context.class).newInstance(app,base);
                    Class<?> providerType=base.getClassLoader().loadClass("v3.c");
                    Object provider=providerType.getConstructor(int.class).newInstance(7);
                    NativeClockProvider factory=new NativeClockProvider(app,app.getAssets(),null);
                    java.util.function.Function<String,Object> capture=name->{Object created=factory.apply(name);clock[0]=(NativeOriginalClockPlugin)created;return created;};
                    field(providerType,Object.class).set(provider,capture);
                    field(type,providerType).set(container[0],provider);
                    if(!Boolean.TRUE.equals(type.getMethod("c",String.class).invoke(container[0],id)))throw new AssertionError("native host rejected original HyperOS clock");
                    activity[0].content.addView((View)container[0]);
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];
                waitForIdleSync();
                runOnMainSync(()->{try{
                    // Actual black-screen fingerprint sequence: hidden UNLOCK
                    // gets replayed at the next sleep before the AOD callback.
                    for(int origin:new int[]{2,5})for(int size:new int[]{0,1,2}){
                        Bundle scene=new Bundle();scene.putInt("uiState",origin);scene.putInt("clockSize",size);
                        scene.putBoolean("isAnim",true);call(container[0],"onClockStateChanged",scene);
                        View face=clock[0].apply(8);
                        scene.putInt("uiState",1);scene.putInt("clockSize",11);
                        for(int repeat=0;repeat<2;repeat++){
                            call(container[0],"onClockStateChanged",scene);
                            if(clock[0].clockContainer.getVisibility()!=View.INVISIBLE)
                                throw new AssertionError("unlock replay exposes clock: "+id);
                            if(clock[0].apply(8)!=face)
                                throw new AssertionError("unlock replaced the last visible face: "+id);
                        }
                        scene.putInt("uiState",5);scene.putInt("clockSize",size);
                        call(container[0],"onClockStateChanged",scene);
                        if(clock[0].clockContainer.getVisibility()!=View.VISIBLE)
                            throw new AssertionError("next sleep lost clock: "+id);
                        View aodFace=clock[0].apply(8);
                        call(container[0],"onClockStateChanged",scene);
                        if(clock[0].apply(8)!=aodFace)
                            throw new AssertionError("duplicate AOD replaced clock: "+id);
                    }
                    evidence.append(id).append(" fingerprint_replay=true\n");
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];
                for(int state:new int[]{2,3,5,2})for(int size:new int[]{1,2,1}){
                    runOnMainSync(()->{try{
                        Bundle scene=new Bundle();scene.putInt("uiState",state);scene.putInt("clockSize",size);
                        call(container[0],"onClockStateChanged",scene);
                        Bundle tick=new Bundle();tick.putLong("time",System.currentTimeMillis());call(container[0],"setTime",tick);
                    }catch(Throwable error){failure[0]=error;}});
                    waitForIdleSync();
                    // Size 2 now owns a separate lyrics face. The animated ink
                    // intentionally starts outside its final content rectangle.
                    // Check settled geometry, rather than the spring's first frame.
                    boolean[] moving={true};long deadline=android.os.SystemClock.uptimeMillis()+3000;
                    do{
                        runOnMainSync(()->moving[0]=clock[0].sceneAnimating());
                        if(!moving[0])break;
                        android.os.SystemClock.sleep(20);waitForIdleSync();
                    }while(android.os.SystemClock.uptimeMillis()<deadline);
                    if(moving[0]){
                        runOnMainSync(()->{try{
                            View face=clock[0].apply(8);
                            evidence.append("pending alpha=").append(face.getAlpha()).append(" attached=").append(face.isAttachedToWindow()).append(" shown=").append(face.isShown()).append(" window=").append(face.getWindowVisibility()).append(" root=").append(clock[0].root.getWidth()).append('x').append(clock[0].root.getHeight()).append('\n');
                            for(android.view.ViewParent p=face.getParent();p instanceof View;p=p.getParent())evidence.append(p.getClass().getName()).append(" visible=").append(((View)p).getVisibility()).append(" alpha=").append(((View)p).getAlpha()).append('\n');
                            java.lang.reflect.Field f=NativeOriginalClockPlugin.class.getDeclaredField("transition");f.setAccessible(true);Object transition=f.get(clock[0]);
                            for(String name:new String[]{"pending","running","spring"}){f=transition.getClass().getDeclaredField(name);f.setAccessible(true);evidence.append(name).append('=').append(f.get(transition)).append('\n');}
                        }catch(Throwable error){failure[0]=error;}});
                        throw new AssertionError("scene transition did not settle: "+id+" state="+state+" size="+size);
                    }
                    runOnMainSync(()->{try{
                        NativeOriginalClockPlugin plugin=clock[0];View host=(View)container[0];
                        int width=app.getResources().getDisplayMetrics().widthPixels;
                        int screen=app.getResources().getDisplayMetrics().heightPixels;
                        host.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(screen,View.MeasureSpec.AT_MOST));
                        host.layout(0,0,width,host.getMeasuredHeight());
                        if(host.getHeight()<=0||host.getHeight()>screen)throw new AssertionError("clock exceeds host screen: "+id+" state="+state+" size="+size+" host="+host.getHeight()+" body="+plugin.clockContainer.getHeight());
                        Bundle bounds=call(container[0],"getClockVisibleRect",null);Rect ink=bounds.getParcelable("visibleRect");
                        if(ink==null||ink.isEmpty())throw new AssertionError("native host cannot locate HyperOS numbers: "+id+" state="+state+" size="+size);
                        // Large double-row originals intentionally occupy more
                        // than 75% of the screen. Reject empty outer space, not
                        // genuine digit height, unlike the classic-only test.
                        if(plugin.clockContainer.getHeight()>screen*.75f&&ink.height()<plugin.clockContainer.getHeight()*.6f)
                            throw new AssertionError("empty screen space retained in original face: "+id+" body="+plugin.clockContainer.getHeight()+" ink="+ink);
                        int[] origin=new int[2];plugin.clockContainer.getLocationOnScreen(origin);
                        if(ink.top<origin[1]-1||ink.bottom>origin[1]+plugin.clockContainer.getHeight()+1)throw new AssertionError("numeric ink escaped reported body: "+id+" state="+state+" size="+size+" ink="+ink+" origin="+java.util.Arrays.toString(origin)+" body="+plugin.clockContainer.getHeight()+" active="+plugin.apply(8).getHeight());
                        Bundle saved=call(container[0],"getStyleData",null);
                        if(!saved.getString("styleData").contains(id))throw new AssertionError("native host lost original style id");
                        if(state==2&&size==1){
                            Bitmap bitmap=Bitmap.createBitmap(540,900,Bitmap.Config.ARGB_8888);
                            Canvas canvas=new Canvas(bitmap);canvas.scale(540f/width,540f/width);plugin.clockContainer.draw(canvas);
                            try(FileOutputStream out=new FileOutputStream(new File(app.getFilesDir(),style.preview))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
                        }
                        evidence.append(id).append(" state=").append(state).append(" size=").append(size).append(" height=").append(host.getHeight()).append(" ink=").append(ink).append('\n');
                    }catch(Throwable error){failure[0]=error;}});
                    if(failure[0]!=null)throw failure[0];
                }
                runOnMainSync(()->{try{
                    Bundle saved=call(container[0],"getStyleData",null);
                    org.json.JSONObject json=new org.json.JSONObject(saved.getString("styleData"));json.put("color",Color.CYAN);
                    saved.putString("styleData",json.toString());call(container[0],"setStyleData",saved);
                    if(new org.json.JSONObject(call(container[0],"getStyleData",null).getString("styleData")).getInt("color")!=Color.CYAN)throw new AssertionError("original color update rejected");
                    call(container[0],"release",null);activity[0].content.removeAllViews();
                }catch(Throwable error){failure[0]=error;}});
                if(failure[0]!=null)throw failure[0];
            }
            runOnMainSync(()->activity[0].finish());
            result.putString("stream",evidence+"NATIVE_HYPEROS_OK factory=true native_container=true compact=true bounds=true color=true\n");finish(-1,result);
        }catch(Throwable error){
            if(activity[0]!=null)runOnMainSync(()->activity[0].finish());
            result.putString("stream",evidence+android.util.Log.getStackTraceString(error));finish(0,result);
        }
    }
    private static Bundle call(Object host,String name,Bundle args)throws Exception{return (Bundle)host.getClass().getMethod("k",String.class,Bundle.class).invoke(host,name,args);}
    private static java.lang.reflect.Field field(Class<?> owner,Class<?> type)throws Exception{
        java.lang.reflect.Field found=null;
        for(java.lang.reflect.Field f:owner.getDeclaredFields())if(f.getType()==type&&!java.lang.reflect.Modifier.isStatic(f.getModifiers())){
            if(found!=null)throw new AssertionError("ambiguous native field");found=f;
        }
        if(found==null)throw new NoSuchFieldException(type.getName());found.setAccessible(true);return found;
    }
}
