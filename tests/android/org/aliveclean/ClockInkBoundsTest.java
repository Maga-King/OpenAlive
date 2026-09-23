package org.aliveclean;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

final class ClockInkBoundsTest {
    static int checks;
    static void run(Context context){
        FrameLayout parent=new FrameLayout(context);
        TextView text=new TextView(context);text.setText("20:36");
        text.setGravity(Gravity.TOP|Gravity.LEFT);text.setTextSize(TypedValue.COMPLEX_UNIT_PX,120);
        text.setPadding(10,8,10,8);parent.addView(text);
        parent.layout(0,0,1000,2000);
        layout(text,400);
        ClockInkBounds ink=new ClockInkBounds();Rect before=new Rect(),after=new Rect();
        if(!ink.measure(text,0,before))throw new AssertionError("Numeric clock rejected");
        layout(text,460);if(!ink.measure(text,0,after))throw new AssertionError("Larger font container rejected");
        same(before,after,"layout padding must not move ink");
        parent.setTranslationY(24);ink.measure(text,0,after);
        Rect translated=new Rect(before);translated.offset(0,24);same(translated,after,"ancestor movement retained");
        ink.measure(text,30,after);translated.offset(0,30);same(translated,after,"native draw offset retained");
        text.setCompoundDrawablesWithIntrinsicBounds(new ColorDrawable(0xffffffff),null,null,null);
        if(ink.measure(text,0,after))throw new AssertionError("Image clock must retain original bounds");
        text.setCompoundDrawables(null,null,null,null);text.setText("clock");layout(text,400);
        if(ink.measure(text,0,after))throw new AssertionError("Unknown clock content must retain original bounds");
        checks+=4;
    }
    private static void layout(TextView text,int height){
        text.measure(View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        text.layout(20,100,520,100+height);
    }
    private static void same(Rect expected,Rect actual,String label){
        if(!expected.equals(actual))throw new AssertionError(label+": "+expected+" != "+actual);
        checks++;
    }
}
