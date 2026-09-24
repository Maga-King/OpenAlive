package org.aliveclean;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

/** Visible window for original views that intentionally skip off-window updates. */
public final class ClockTestActivity extends Activity {
    FrameLayout content;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        content = new FrameLayout(this);
        content.setBackgroundColor(android.graphics.Color.BLACK);
        setContentView(content);
    }
}
