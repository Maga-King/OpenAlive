package org.aliveclean;
public final class XiaomiProbeActivity extends android.app.Activity {
    public void onCreate(android.os.Bundle b){super.onCreate(b);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);android.widget.TextView text=new android.widget.TextView(this);text.setText("OpenAlive Earth startup test");setContentView(text);}
}
