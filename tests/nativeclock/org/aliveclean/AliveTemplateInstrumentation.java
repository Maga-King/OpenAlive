package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Real Android rasterization; never changes the device's wallpaper, clock or settings. */
public final class AliveTemplateInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Throwable[] failure = new Throwable[1];
            runOnMainSync(() -> { try { verify(); } catch(Throwable error) { failure[0]=error; } });
            if (failure[0] != null) throw new AssertionError(failure[0]);
            result.putString("stream", "ALIVE_TEMPLATE_OK original_geometry=true date_below=true time_zone=true\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error)); finish(0, result);
        }
    }
    private void verify() throws Exception {
        Context app = getTargetContext();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.setLocale(Locale.SIMPLIFIED_CHINESE);
        Context host = app.createConfigurationContext(config);
        TimeZone shanghai = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar time = Calendar.getInstance(shanghai); time.clear(); time.set(2026, 8, 22, 18, 30);
        Bitmap sheet = Bitmap.createBitmap(1440, 1500, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet); canvas.drawColor(Color.BLACK);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG); label.setColor(0xff999999); label.setTextSize(30);
        int row = 0;
        for (String id : new String[]{FlymeAliveClockTemplate.VERTICAL, FlymeAliveClockTemplate.HORIZONTAL}) {
            FlymeAliveAodFace face = new FlymeAliveAodFace(host, app.getAssets(), id);
            face.update(time.getTimeInMillis(), shanghai, true);
            layout(face, 1080);
            require("18:30".equals(face.timeText()), "time mismatch");
            RectF numbers = new RectF(), date = new RectF();
            face.numberBounds(numbers); face.dateBounds(date);
            require(!numbers.isEmpty() && !date.isEmpty(), "empty bounds");
            require(date.top > numbers.bottom, "calendar overlaps time");
            require(numbers.left >= 0 && numbers.right <= face.getWidth(), "time clipped");
            require(date.bottom <= face.getHeight(), "calendar clipped");
            if (id.equals(FlymeAliveClockTemplate.VERTICAL)) require(numbers.height() > numbers.width(), "lost vertical layout");
            canvas.drawText(id, 80, row+45, label);
            int saved = canvas.save(); canvas.translate(180, row+70); face.draw(canvas); canvas.restoreToCount(saved);
            // A 12-hour selection and a timezone change must not fall back to system wall time.
            face.update(time.getTimeInMillis(), shanghai, false);
            require("06:30".equals(face.timeText()), "12-hour conversion");
            face.update(time.getTimeInMillis(), TimeZone.getTimeZone("UTC"), true);
            require("10:30".equals(face.timeText()), "timezone conversion");
            for (int minute=0; minute<60; minute++) {
                time.set(Calendar.MINUTE, minute); face.update(time.getTimeInMillis(), shanghai, true); layout(face, 420);
                face.numberBounds(numbers); face.dateBounds(date);
                require(numbers.bottom < date.top, "minute update overlaps date");
            }
            time.set(Calendar.MINUTE, 30);
            row += 900;
        }
        try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), "alive-templates.png"))) {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        sheet.recycle();
    }
    private static void layout(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
