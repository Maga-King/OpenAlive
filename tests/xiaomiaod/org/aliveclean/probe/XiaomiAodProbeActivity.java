package org.aliveclean.probe;

/** Disposable foreground window prevents OEM freezing during instrumented checks. */
public final class XiaomiAodProbeActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){
        super.onCreate(state);setShowWhenLocked(true);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.widget.TextView message=new android.widget.TextView(this);
        message.setText("OpenAlive battery checks\nThis test page closes automatically.");
        message.setTextSize(20);message.setGravity(android.view.Gravity.CENTER);setContentView(message);
    }
}
