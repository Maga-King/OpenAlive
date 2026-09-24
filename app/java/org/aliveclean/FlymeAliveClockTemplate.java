package org.aliveclean;

import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Flyme's shipped ALIVE template geometry and font; no ColorOS clock layout involved. */
final class FlymeAliveClockTemplate {
    static final String VERTICAL = "flyme.alive.hverticaltime";
    static final String HORIZONTAL = "flyme.alive.hhorizontaltime";
    final String id;
    final int width, height;
    private final Part[] parts;

    private static final class Part {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect ink = new Rect();
        final RectF bounds = new RectF();
        final float x, y;
        final String field, align, alignV;
        String text = "";
        float left, top, baseline;
        Part(JSONObject json, AssetManager assets, String folder) throws Exception {
            x = (float)json.getDouble("x"); y = (float)json.getDouble("y");
            field = json.getString("field"); align = json.getString("align"); alignV = json.getString("alignV");
            // Zooking q.b() uses the supplied typeface directly; q.d() handles
            // color only. This implementation does not synthesize the XML bold flag.
            paint.setTypeface(Typeface.createFromAsset(assets, folder+json.getString("font")));
            paint.setTextSize((float)json.getDouble("size")); paint.setColor(Color.WHITE);
        }
        void update(String value) {
            if (text.equals(value)) return;
            text = value;
            Paint.FontMetrics fm = paint.getFontMetrics();
            // Match Zooking q.e(): integer measured box, align it, then draw at -fm.top.
            int w = (int)paint.measureText(text), h = (int)(fm.bottom-fm.top);
            left = x - ("center".equals(align) ? w/2f : "right".equals(align) ? w : 0);
            top = y - ("center".equals(alignV) ? h/2f : "bottom".equals(alignV) ? h : 0);
            baseline = top-fm.top;
            paint.getTextBounds(text, 0, text.length(), ink);
            bounds.set(left+ink.left, baseline+ink.top, left+ink.right, baseline+ink.bottom);
        }
    }

    FlymeAliveClockTemplate(AssetManager assets, String id) throws Exception {
        if (!VERTICAL.equals(id) && !HORIZONTAL.equals(id)) throw new IllegalArgumentException("clock id");
        this.id = id;
        String folder = "native-clock/templates/flyme/"+id.substring(id.lastIndexOf('.')+1)+"/";
        JSONObject json = new JSONObject(read(assets, folder+"render.json"));
        if (json.getInt("schema") != 1 || !id.equals(json.getString("id"))) throw new IllegalArgumentException("clock schema");
        width = json.getInt("width"); height = json.getInt("height");
        JSONArray array = json.getJSONArray("texts");
        parts = new Part[array.length()];
        for (int i=0; i<parts.length; i++) parts[i] = new Part(array.getJSONObject(i), assets, folder);
    }

    void update(Calendar time, boolean use24Hour) {
        int hour = time.get(Calendar.HOUR_OF_DAY);
        if (!use24Hour) { hour %= 12; if (hour == 0) hour = 12; }
        String h = String.format(Locale.ROOT, "%02d", hour);
        String m = String.format(Locale.ROOT, "%02d", time.get(Calendar.MINUTE));
        for (Part part : parts) part.update("hour".equals(part.field) ? h : "minute".equals(part.field) ? m : h+":"+m);
    }

    void draw(Canvas canvas) { for (Part part : parts) canvas.drawText(part.text, part.left, part.baseline, part.paint); }
    void color(int color) { for (Part part : parts) part.paint.setColor(color); }
    void bounds(RectF out) { out.setEmpty(); for (Part part : parts) out.union(part.bounds); }
    String timeText() {
        if (parts.length == 1) return parts[0].text;
        return parts[0].text+":"+parts[1].text;
    }
    private static String read(AssetManager assets, String path) throws Exception {
        try (InputStream in = assets.open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int n;
            while ((n=in.read(bytes)) != -1) out.write(bytes, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
