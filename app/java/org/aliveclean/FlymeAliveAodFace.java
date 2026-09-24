package org.aliveclean;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Standalone ALIVE AOD clock. Its host supplies time and visibility; no timer or wake lock. */
final class FlymeAliveAodFace extends View {
    private final FlymeAliveClockTemplate template;
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Calendar calendar = Calendar.getInstance();
    private final RectF numberBounds = new RectF();
    private final RectF dateBounds = new RectF();
    private final android.graphics.Rect dateInk = new android.graphics.Rect();
    private String date = "", week = "";
    private float scale, originX, dateX, weekX, dateBaseline;
    private long time;
    private boolean twentyFour = true;

    FlymeAliveAodFace(Context host, AssetManager moduleAssets, String id) throws Exception {
        super(host);
        template = new FlymeAliveClockTemplate(moduleAssets, id);
        datePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        datePaint.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity);
        datePaint.setColor(Color.WHITE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        update(System.currentTimeMillis(), TimeZone.getDefault(), DateFormat.is24HourFormat(host));
    }

    void update(long millis, TimeZone zone, boolean format24) {
        time = millis; twentyFour = format24;
        calendar.setTimeZone(zone); calendar.setTimeInMillis(millis);
        template.update(calendar, format24);
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        // AODBasicView uses separate date/week labels with gap_between_date_week (6dp).
        boolean chinese = "zh".equals(locale.getLanguage());
        String pattern = DateFormat.getBestDateTimePattern(locale, chinese ? "MMMd" : "MMMdEEE");
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(pattern, locale);
        format.setTimeZone(zone); date = format.format(calendar.getTime());
        format = new java.text.SimpleDateFormat("EEE", locale);
        format.setTimeZone(zone); week = chinese ? format.format(calendar.getTime()) : "";
        setContentDescription(template.timeText()+" "+date+(week.isEmpty() ? "" : " "+week));
        geometry(getWidth());
        requestLayout(); invalidate();
    }

    private void geometry(int width) {
        if (width <= 0) return;
        // AODDisplayView.getScale(): a 1080px design, minimum scale 1 on phones.
        // Fit narrow preview containers instead of letting the original 500px box clip.
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        scale = Math.max(1f, screenWidth/1080f);
        scale = Math.min(scale, width/(float)template.width);
        originX = (width-template.width*scale)/2f;
        template.bounds(numberBounds);
        numberBounds.left = originX+numberBounds.left*scale;
        numberBounds.right = originX+numberBounds.right*scale;
        numberBounds.top *= scale; numberBounds.bottom *= scale;
        // AODDisplayView places AODBasicView after the template plus dp_12.
        Paint.FontMetrics fm = datePaint.getFontMetrics();
        float dateTop = template.height*scale+12*getResources().getDisplayMetrics().density;
        dateBaseline = dateTop-fm.ascent;
        float dateWidth = datePaint.measureText(date);
        float gap = week.isEmpty() ? 0 : 6*getResources().getDisplayMetrics().density;
        dateX = (width-dateWidth-gap-datePaint.measureText(week))/2f;
        weekX = dateX+dateWidth+gap;
        datePaint.getTextBounds(date, 0, date.length(), dateInk);
        dateBounds.set(dateX+dateInk.left, dateBaseline+dateInk.top,
                dateX+dateInk.right, dateBaseline+dateInk.bottom);
        if (!week.isEmpty()) {
            datePaint.getTextBounds(week, 0, week.length(), dateInk);
            dateBounds.union(weekX+dateInk.left, dateBaseline+dateInk.top,
                    weekX+dateInk.right, dateBaseline+dateInk.bottom);
        }
    }

    @Override protected void onMeasure(int w, int h) {
        int width = resolveSize(getResources().getDisplayMetrics().widthPixels, w);
        geometry(width);
        float bottom = template.height*scale+12*getResources().getDisplayMetrics().density;
        Paint.FontMetrics fm = datePaint.getFontMetrics();
        setMeasuredDimension(width, resolveSize((int)Math.ceil(bottom+fm.descent-fm.ascent), h));
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { geometry(w); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int saved = canvas.save(); canvas.translate(originX, 0); canvas.scale(scale, scale);
        template.draw(canvas); canvas.restoreToCount(saved);
        canvas.drawText(date, dateX, dateBaseline, datePaint);
        if (!week.isEmpty()) canvas.drawText(week, weekX, dateBaseline, datePaint);
    }
    void color(int value) { template.color(value); datePaint.setColor(value); invalidate(); }
    void numberBounds(RectF out) { out.set(numberBounds); }
    void dateBounds(RectF out) { out.set(dateBounds); }
    String timeText() { return template.timeText(); }
    long time() { return time; }
    boolean is24Hour() { return twentyFour; }
}
