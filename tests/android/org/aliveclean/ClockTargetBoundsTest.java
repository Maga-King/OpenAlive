package org.aliveclean;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Compare the production reader to rasterized digits, including native hidden-container semantics. */
public final class ClockTargetBoundsTest {
    static int checks;
    public static final class Digit extends FrameLayout {
        final TextView text;
        Digit(Context c,String value){super(c);text=value==null?null:new TextView(c);
            if(text!=null){text.setText(value);text.setTextColor(0xffffffff);text.setTextSize(TypedValue.COMPLEX_UNIT_PX,96);
                text.setGravity(Gravity.CENTER);text.setPadding(17,11,3,7);addView(text);}}
        public TextView getVisibleTextView(){return text;}
    }
    public static final class Container extends FrameLayout {
        final Digit digit;
        Container(Context c,String value){super(c);digit=new Digit(c,value);addView(digit);}
        public Digit getDigitalTimeView(){return digit;}
    }
    public static final class Target extends FrameLayout {
        final List<Container> containers=new ArrayList<>();final List<Digit> numbers=new ArrayList<>();
        Target(Context c){super(c);}
        public List<Container> getDigitalTimeContainers(){return containers;}
        public List<Digit> getSimpleDigitalTimeViews(){return numbers;}
        Container add(String value,int x,int y,boolean number){
            Container container=new Container(getContext(),value);addView(container);containers.add(container);
            if(number)numbers.add(container.digit);
            container.layout(0,0,800,1000);Digit digit=container.digit;digit.layout(x,y,x+140,y+180);
            if(digit.text!=null){digit.text.measure(View.MeasureSpec.makeMeasureSpec(140,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));digit.text.layout(0,0,140,180);}
            return container;
        }
    }
    static void run(Context context){
        Typeface nativeFont=Typeface.createFromAsset(context.getAssets(),"clock-test.ttf");
        for(Typeface font:new Typeface[]{Typeface.DEFAULT,Typeface.SERIF,Typeface.MONOSPACE,Typeface.create("sans-serif-condensed",Typeface.ITALIC),nativeFont,Typeface.create(nativeFont,730,false)})
        for(boolean stacked:new boolean[]{false,true})for(int gravity:new int[]{Gravity.LEFT|Gravity.TOP,Gravity.CENTER,Gravity.RIGHT|Gravity.BOTTOM}){
            Target target=new Target(context);FrameLayout host=new FrameLayout(context);host.addView(target);
            host.layout(0,0,1200,1600);target.layout(130,210,930,1210);
            for(int i=0;i<4;i++){
                TextView text=target.add(String.valueOf(i+1),40+(stacked?i%2:i)*140,90+(stacked?i/2:0)*190,true).digit.text;
                text.setGravity(gravity);text.setTypeface(font);
                text.measure(View.MeasureSpec.makeMeasureSpec(140,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));text.layout(0,0,140,180);
            }
            // The colon parent is GONE, while its empty child remains VISIBLE.
            Container colon=target.add(null,0,0,false);colon.setVisibility(View.GONE);
            target.setPivotX(400);target.setPivotY(500);target.setScaleX(.9f);target.setScaleY(.9f);
            ClockTargetBounds reader=new ClockTargetBounds();Rect destination=new Rect();
            require(reader.measure(target,destination),"Hidden colon caused measurement failure");
            near(raster(host),destination,"Actual global painted center");
            // Notification layout switches move individual numbers as well as
            // their parent. Read the rendered hierarchy, not the old endpoint.
            for(Digit digit:target.numbers){digit.setTranslationX(31);digit.setTranslationY(-19);digit.setScaleY(.7f);}
            target.setTranslationX(23);target.setTranslationY(41);
            require(reader.measure(target,destination),"Moved clock rejected");
            near(raster(host),destination,"Notification digit/ancestor transforms lost");
            colon.setVisibility(View.VISIBLE);colon.digit.layout(200,90,225,270);
            require(reader.measure(target,destination),"Drawable colon blocked digit measurement");
            near(raster(host),destination,"Separator changed digit center");
            for(Digit digit:target.numbers){
                TextView text=digit.text;text.setTypeface(Typeface.create(Typeface.SERIF,900,true));
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX,61);text.setText("8");
                text.measure(View.MeasureSpec.makeMeasureSpec(140,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));text.layout(0,0,140,180);
            }
            require(reader.measure(target,destination),"Changed font rejected");
            near(raster(host),destination,"Stale center after changing font");
            // No valid partial rectangle when a real time digit is unsupported.
            target.numbers.get(0).text.setText("unknown");Rect unchanged=new Rect(destination);
            require(!reader.measure(target,destination),"Unsupported time digit accepted");require(destination.equals(unchanged),"Fallback mutated native rectangle");
        }
    }
    private static Rect raster(View host){
        Bitmap bitmap=Bitmap.createBitmap(1200,1600,Bitmap.Config.ARGB_8888);
        host.draw(new Canvas(bitmap));Rect result=new Rect();
        int[] pixels=new int[1200*1600];bitmap.getPixels(pixels,0,1200,0,0,1200,1600);
        for(int y=0;y<1600;y++)for(int x=0;x<1200;x++)if((pixels[y*1200+x]>>>24)>32)result.union(x,y,x+1,y+1);
        bitmap.recycle();require(!result.isEmpty(),"Pixel oracle has no drawn digits");return result;
    }
    private static void near(Rect expected,Rect actual,String label){
        require(Math.abs(expected.exactCenterX()-actual.exactCenterX())<=2&&Math.abs(expected.exactCenterY()-actual.exactCenterY())<=2,label+": "+expected+" != "+actual);
    }
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
}
