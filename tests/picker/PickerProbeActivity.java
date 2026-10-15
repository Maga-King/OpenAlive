package org.aliveclean;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.app.WallpaperManager;
import android.os.Bundle;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Test-only launcher. Failure injection never enters the production application. */
public final class PickerProbeActivity extends Activity {
    private ComponentName expected;
    private boolean forceFallback;
    private int attempts;
    private final StringBuilder report=new StringBuilder();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        expected=new ComponentName("org.aliveclean","org.aliveclean.CleanWallpaper");
        forceFallback=getIntent().getBooleanExtra("fallback",false);
        try{
            boolean reliable=WallpaperPicker.open(this,expected,20);
            if(!reliable||attempts!=(forceFallback?2:1))throw new AssertionError("Unexpected route");
            record("PREVIEW_STARTED reliable="+reliable+" attempts="+attempts);
        }catch(Throwable error){record("FAILED "+error);}
    }
    @Override public void startActivityForResult(Intent intent,int request){
        attempts++;
        ComponentName received=intent.getParcelableExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT);
        if(!expected.equals(received))throw new AssertionError("Wrong wallpaper component: "+received);
        record("attempt="+attempts+" destination="+intent.getComponent()+" engine="+received);
        if(forceFallback&&attempts==1)throw new ActivityNotFoundException("Test-only implicit route failure");
        super.startActivityForResult(intent,request);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);record("RETURNED result="+result);finish();
    }
    private void record(String line){
        report.append(line).append('\n');
        try(FileOutputStream out=openFileOutput("result.txt",0)){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}
        catch(Exception error){throw new IllegalStateException(error);}
    }
}
