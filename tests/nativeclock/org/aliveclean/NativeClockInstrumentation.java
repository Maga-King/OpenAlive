package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.Method;
import java.util.HashSet;

/** Runs actual installed ColorOS codec/cache when available; no system writes. */
public final class NativeClockInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context app = getTargetContext();
            Context plugin = null;
            try { plugin = app.createPackageContext("com.oplus.keyguard.personality.clocks", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY); }
            catch (android.content.pm.PackageManager.NameNotFoundException spare) { /* Asset rendering on HyperOS. */ }
            Bitmap bitmap = Bitmap.createBitmap(1080, NativeClockFonts.PATHS.length * 210, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.BLACK);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            HashSet<String> metrics = new HashSet<>();
            StringBuilder diagnostic = new StringBuilder();
            int index = 0;
            for (String path : NativeClockFonts.PATHS) {
                Typeface typeface = new Typeface.Builder(app.getAssets(), path).setWeight(400)
                        .setFontVariationSettings("'wght' 400, 'opsz' 1010").build();
                require(typeface != null && !typeface.equals(Typeface.DEFAULT), "font fallback " + path);
                for (int weight : new int[]{100, 400, 700}) {
                    Typeface sample = new Typeface.Builder(app.getAssets(), path).setWeight(weight)
                            .setFontVariationSettings("'wght' "+weight+", 'opsz' 1010").build();
                    paint.setTypeface(sample); paint.setTextSize(115);
                    Paint.FontMetrics line = paint.getFontMetrics();
                    Rect ink = new Rect(); paint.getTextBounds("0123456789", 0, 10, ink);
                    require(line.top <= line.ascent && line.descent <= line.bottom,
                            "invalid variable metrics "+path+" weight="+weight);
                    require(ink.top-line.top >= paint.getTextSize()*.30f,
                            "variable font loses date clearance "+path+" weight="+weight);
                }
                if (plugin != null) {
                    Context wrapped = NativeClockFonts.withAssets(plugin, app.getAssets());
                    require(wrapped.getResources() == plugin.getResources(), "resource namespace changed");
                    Class<?> cache = plugin.getClassLoader().loadClass("com.oplus.keyguard.clock.digital.util.FontTypefaceCache");
                    Method build = cache.getMethod("buildTypeFace", Context.class, String.class, int.class, int.class);
                    Typeface actual = (Typeface)build.invoke(null, wrapped, path, 400, 0);
                    require(actual != null && !actual.equals(Typeface.DEFAULT), "native font fallback " + path);
                    require(actual == build.invoke(null, wrapped, path, 400, 0), "native cache missed " + path);
                    typeface = actual;
                    for (int style : new int[]{0, 2, 7}) roundTrip(plugin.getClassLoader(), path, style);
                }
                paint.setTypeface(Typeface.DEFAULT); paint.setTextSize(25);
                canvas.drawText(path.substring(path.indexOf('/')+1), 35, index * 210 + 35, paint);
                paint.setTypeface(typeface); paint.setTextSize(115);
                require(paint.hasGlyph("0") && paint.hasGlyph("9") && paint.hasGlyph(":"), "missing time glyph " + path);
                Rect colon = new Rect(); paint.getTextBounds(":", 0, 1, colon);
                require(!colon.isEmpty(), "empty colon glyph " + path);
                Rect digit = new Rect(); paint.getTextBounds("0123456789", 0, 10, digit);
                Paint.FontMetrics fm = paint.getFontMetrics();
                require(fm.top <= fm.ascent && fm.descent <= fm.bottom, "invalid font line metrics " + path);
                require(digit.top-fm.top >= paint.getTextSize()*.30f, "native top margin cannot fit " + path);
                diagnostic.append(path).append(" top=").append(fm.top).append(" ascent=").append(fm.ascent)
                        .append(" descent=").append(fm.descent).append(" bottom=").append(fm.bottom)
                        .append(" ink=").append(digit).append('\n');
                metrics.add(paint.measureText("18:30") + ":" + paint.measureText("09:59"));
                canvas.drawText("18:30   09:59", 35, index * 210 + 155, paint);
                index++;
            }
            require(metrics.size() >= 5, "fonts rendered as same fallback");
            String[] combined = NativeClockFonts.append(new String[]{"original.ttf"});
            require(combined.length == NativeClockFonts.PATHS.length+1 && combined[0].equals("original.ttf"), "native entries changed");
            require(NativeClockFonts.append(combined).length == combined.length, "duplicate entries");
            require(!NativeClockFonts.contains("native-clock/unknown.ttf"), "unknown path accepted");
            try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), "fonts.png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
            if (plugin != null) {
                Typeface reference = new Typeface.Builder(plugin.getAssets(), "fonts/fixed/OPPODigit01.ttf").setWeight(400).build();
                paint.setTypeface(reference); paint.setTextSize(115);
                Rect bounds = new Rect(); paint.getTextBounds("0123456789", 0, 10, bounds);
                Paint.FontMetrics fm = paint.getFontMetrics();
                diagnostic.append("OPPO reference top=").append(fm.top).append(" ascent=").append(fm.ascent)
                        .append(" descent=").append(fm.descent).append(" bottom=").append(fm.bottom)
                        .append(" ink=").append(bounds).append('\n');
            }
            result.putString("stream", diagnostic+"NATIVE_CLOCK_FONTS_OK fonts=7 native_codec="+(plugin != null)+" native_cache="+(plugin != null)+"\n");
            finish(-1, result);
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            result.putString("stream", trace.toString()); finish(1, result);
        }
    }
    private static void roundTrip(ClassLoader loader, String path, int style) throws Exception {
        Class<?> adapterClass = loader.loadClass("com.oplus.keyguard.clock.digital.config.DigitalClockStyleConfigTypeAdapter");
        Class<?> readerClass = loader.loadClass("com.google.gson.stream.JsonReader");
        Class<?> writerClass = loader.loadClass("com.google.gson.stream.JsonWriter");
        Class<?> configClass = loader.loadClass("com.oplus.keyguard.clock.digital.config.DigitalClockStyleConfig");
        Object adapter = adapterClass.getConstructor().newInstance();
        String input = "{\"clockStyle\":"+style+",\"timeFamilyName\":\""+path+"\",\"timeFontWeight\":400,\"timeFontWeightRate\":50,\"versionCode\":5}";
        Object reader = readerClass.getConstructor(Reader.class).newInstance(new StringReader(input));
        Object config = adapterClass.getMethod("read", readerClass).invoke(adapter, reader);
        require(path.equals(configClass.getMethod("getTimeFamilyName").invoke(config)), "native read reset font");
        require(((Integer)configClass.getMethod("getClockStyle").invoke(config)) == style, "native read reset layout");
        StringWriter output = new StringWriter();
        Object writer = writerClass.getConstructor(Writer.class).newInstance(output);
        adapterClass.getMethod("write", writerClass, configClass).invoke(adapter, writer, config);
        writerClass.getMethod("flush").invoke(writer);
        Object reread = readerClass.getConstructor(Reader.class).newInstance(new StringReader(output.toString()));
        Object restored = adapterClass.getMethod("read", readerClass).invoke(adapter, reread);
        require(path.equals(configClass.getMethod("getTimeFamilyName").invoke(restored)), "native round trip reset font");
        require(((Integer)configClass.getMethod("getClockStyle").invoke(restored)) == style, "native round trip reset layout");
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
