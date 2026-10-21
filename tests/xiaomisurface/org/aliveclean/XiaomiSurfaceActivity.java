package org.aliveclean;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class XiaomiSurfaceActivity extends Activity {
    FrameLayout root;SurfaceView surface;
    public void onCreate(Bundle state){
        super.onCreate(state);setShowWhenLocked(true);setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root=new FrameLayout(this);setContentView(root);
    }
    SurfaceView addSurface(){surface=new SurfaceView(this);root.addView(surface,new FrameLayout.LayoutParams(360,792));return surface;}
    void removeSurface(){root.removeView(surface);surface=null;}
}
