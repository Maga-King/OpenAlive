package org.aliveclean.uihost;

import android.app.Activity;
import android.os.Bundle;

/** Compatibility host for the unchanged ROM layout; not a replacement editor. */
public final class OfficialUiHost extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(getResources().getIdentifier("activity_editor", "layout", "com.flyme.systemuieditor"));
    }
}
