package org.aliveclean;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Font assets for the native ColorOS clock. No layout, time or AOD ownership. */
final class NativeClockFonts {
    static final String[] PATHS = {
        "native-clock/flyme/FlymeNumber-VF.ttf",
        "native-clock/flyme/SixCaps-Regular.ttf",
        "native-clock/flyme/DMSerifDisplay-Regular.ttf",
        "native-clock/hyperos/MiSansRCFVF.ttf",
        "native-clock/hyperos/MiSansRoundedFVF.ttf",
        "native-clock/hyperos/MiSerifFVF.ttf",
        "native-clock/hyperos/MiClock-Light.otf"
    };

    static boolean contains(Object path) {
        if (!(path instanceof String)) return false;
        for (String font : PATHS) if (font.equals(path)) return true;
        return false;
    }

    static String[] append(String[] original) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        Collections.addAll(paths, original);
        Collections.addAll(paths, PATHS);
        return paths.toArray(new String[0]);
    }

    // The plugin retains its Resources, theme, configuration and class loader.
    // Only explicitly selected font loading calls receive the extra assets.
    static Context withAssets(Context plugin, AssetManager fonts) {
        return new ContextWrapper(plugin) {
            @Override public AssetManager getAssets() { return fonts; }
        };
    }

    private NativeClockFonts() {}
}
