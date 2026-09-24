package org.aliveclean;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/** Runs as an ordinary non-debuggable Activity, without instrumentation's VM changes. */
public final class NativeClockReleaseProbe extends Activity {
    public static final Map<String,String> FINAL_FIELD=new HashMap<>();
    private FrameLayout stage;
    private int checked;
    @Override public void onCreate(Bundle args){
        super.onCreate(args);stage=new FrameLayout(this);setContentView(stage);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stage.post(()->{
            try{
                if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)
                    throw new AssertionError("Probe is debuggable");
                if(getApplicationInfo().targetSdkVersion!=37)throw new AssertionError("SystemUI SDK contract not reproduced");
                java.lang.reflect.Field field=NativeClockReleaseProbe.class.getField("FINAL_FIELD");
                field.setAccessible(true);
                try{field.set(null,new HashMap<>());throw new AssertionError("VM permits final writes; invalid production test");}
                catch(IllegalAccessException expected){}
                ArrayList<String> ids=new ArrayList<>();
                ids.add(NativeFlymeClockPlugin.ID);ids.add(NativeFlymeClockPlugin.HORIZONTAL_ID);
                ids.add(NativeFlymeArtworkPlugin.PERSPECTIVE);
                for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL)ids.add(style.id);
                checkNext(ids,0);
            }catch(Throwable failure){done(android.util.Log.getStackTraceString(failure));}
        });
    }
    private void checkNext(ArrayList<String> ids,int at){
        if(at==ids.size()){done("NATIVE_RELEASE_HOST_OK styles="+at+" scenes="+checked+" final_write_rejected=true");return;}
        NativeOriginalClockPlugin clock=null;
        try{
            NativeClockProvider factory=new NativeClockProvider(this,getAssets(),null);
            clock=(NativeOriginalClockPlugin)factory.apply(ids.get(at));
            stage.addView(clock.apply(1));
            checkScene(ids,at,clock,0);
        }catch(Throwable failure){
            if(clock!=null)clock.apply("release",null);stage.removeAllViews();
            done(ids.get(at)+"\n"+android.util.Log.getStackTraceString(failure));
        }
    }
    private void checkScene(ArrayList<String> ids,int at,NativeOriginalClockPlugin clock,int mode){
        if(mode==4){clock.apply("release",null);stage.removeAllViews();stage.post(()->checkNext(ids,at+1));return;}
        try{
                int ui=mode<2?2:3,size=mode%2+1;
                Bundle scene=new Bundle();scene.putInt("uiState",ui);scene.putInt("clockSize",size);
                clock.apply("onClockStateChanged",scene);
                Bundle time=new Bundle();time.putLong("time",System.currentTimeMillis());clock.apply("setTime",time);
        }catch(Throwable failure){clock.apply("release",null);done(android.util.Log.getStackTraceString(failure));return;}
        // Original number controls post their time/layout updates. Let those
        // callbacks and a real window traversal complete before checking ink.
        stage.postDelayed(()->{try{
                View root=clock.apply(1);int width=getResources().getDisplayMetrics().widthPixels;
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                root.layout(0,0,width,root.getMeasuredHeight());
                if(root.getHeight()==0)throw new AssertionError("Empty clock "+ids.get(at));
                android.graphics.Rect bounds=clock.apply("getClockVisibleRect",null).getParcelable("visibleRect");
                if(bounds==null||bounds.isEmpty())throw new AssertionError("Empty numbers "+ids.get(at));
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                try{root.draw(new android.graphics.Canvas(bitmap));}finally{bitmap.recycle();}
                checked++;
                checkScene(ids,at,clock,mode+1);
        }catch(Throwable failure){clock.apply("release",null);stage.removeAllViews();done(ids.get(at)+" scene="+mode+"\n"+android.util.Log.getStackTraceString(failure));}},100);
    }
    private void done(String result){
        try(java.io.FileOutputStream out=openFileOutput("release-host.txt",MODE_PRIVATE)){
            out.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }catch(Exception error){android.util.Log.e("OpenAliveClockProbe","Result write failed",error);}
        android.util.Log.i("OpenAliveClockProbe",result);finish();
    }
}
