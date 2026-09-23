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
            Target target=new Target(context);target.layout(0,0,800,1000);
            for(int i=0;i<4;i++){
                TextView text=target.add(String.valueOf(i+1),40+(stacked?i%2:i)*140,90+(stacked?i/2:0)*190,true).digit.text;
                text.setGravity(gravity);text.setTypeface(font);
                text.measure(View.MeasureSpec.makeMeasureSpec(140,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));text.layout(0,0,140,180);
            }
            // The colon parent is GONE, while its empty child remains VISIBLE.
            Container colon=target.add(null,0,0,false);colon.setVisibility(View.GONE);
            Rect nativeBox=raw(target),expected=raster(target);ClockTargetBounds reader=new ClockTargetBounds();
            Rect destination=new Rect(nativeBox);
            require(reader.correct(target,destination),"Hidden colon caused entire correction to fall back");
            near(expected,destination,"Painted digit center");
            require(destination.centerX()!=nativeBox.centerX()||destination.centerY()!=nativeBox.centerY(),"Padding correction had no effect");
            // Match a scaled/transferred native endpoint, independent of transient view animations.
            Rect transferred=new Rect(300,700,300+nativeBox.width()*2,700+nativeBox.height()*2);
            Rect projected=new Rect(300+(expected.left-nativeBox.left)*2,700+(expected.top-nativeBox.top)*2,
                    300+(expected.right-nativeBox.left)*2,700+(expected.bottom-nativeBox.top)*2);
            for(Digit digit:target.numbers){digit.setTranslationX(81);digit.setTranslationY(-39);digit.setScaleY(.7f);}
            require(reader.correct(target,transferred),"Scaled destination rejected");near(projected,transferred,"Target changed with handoff transform");
            // A visible drawable separator needs no TextView; the four numbers still define the ink.
            colon.setVisibility(View.VISIBLE);colon.digit.layout(200,90,225,270);
            destination.set(raw(target));require(reader.correct(target,destination),"Drawable colon blocked digit measurement");near(expected,destination,"Separator changed digit center");
            // Reuse the reader and the same views after changing font, weight,
            // size and digits. Only reflection methods may be cached, never ink.
            for(Digit digit:target.numbers){
                TextView text=digit.text;text.setTypeface(Typeface.create(Typeface.SERIF,900,true));
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX,61);text.setText("8");
                text.measure(View.MeasureSpec.makeMeasureSpec(140,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));text.layout(0,0,140,180);
            }
            destination.set(raw(target));require(reader.correct(target,destination),"Changed font rejected");near(raster(target),destination,"Stale font center after changing font");
            // No valid partial rectangle when a real time digit is unsupported.
            target.numbers.get(0).text.setText("unknown");Rect unchanged=raw(target);destination.set(unchanged);
            require(!reader.correct(target,destination),"Unsupported time digit accepted");require(destination.equals(unchanged),"Fallback mutated native rectangle");
        }
    }
    private static Rect raw(Target target){Rect result=new Rect();for(Container c:target.containers)if(c.getVisibility()==View.VISIBLE){Digit d=c.digit;result.union(d.getLeft(),d.getTop(),d.getRight(),d.getBottom());}return result;}
    private static Rect raster(Target target){
        Rect result=new Rect();Bitmap bitmap=Bitmap.createBitmap(140,180,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        for(Digit digit:target.numbers){
            bitmap.eraseColor(0);digit.text.draw(canvas);Rect painted=new Rect();
            for(int y=0;y<180;y++)for(int x=0;x<140;x++)if((bitmap.getPixel(x,y)>>>24)>32)painted.union(x,y,x+1,y+1);
            require(!painted.isEmpty(),"Pixel oracle has no drawn digit");painted.offset(digit.getLeft(),digit.getTop());result.union(painted);
        }
        bitmap.recycle();return result;
    }
    private static void near(Rect expected,Rect actual,String label){
        require(Math.abs(expected.exactCenterX()-actual.exactCenterX())<=2&&Math.abs(expected.exactCenterY()-actual.exactCenterY())<=2,label+": "+expected+" != "+actual);
    }
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
}
