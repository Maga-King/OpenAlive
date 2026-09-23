package org.aliveclean;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.InflaterInputStream;

/** Original Flyme SVGA art and renderer, with a bounded, locally owned timeline. */
final class FlymeNotificationLight extends FrameLayout {
    static final class Model {
        final Object entity;
        final int frames,fps;
        Model(Object entity)throws Exception {
            this.entity=entity;frames=entity.getClass().getField("e").getInt(entity);
            fps=entity.getClass().getField("d").getInt(entity);
            if(frames<2||fps<1||frames*1000L/fps>15000)throw new IOException("Invalid notification timeline");
        }
        long duration(){return frames*1000L/fps;}
    }
    // Raw DEX names are intentionally isolated here; the bundled official DEX is pinned.
    static Model load(Context assets,boolean ring)throws Exception {
        ClassLoader loader=FlymeNotificationLight.class.getClassLoader();
        Class<?> movie=Class.forName("T4.f",true,loader),entity=Class.forName("P4.q",true,loader);
        byte[] bytes;
        try(InputStream in=new InflaterInputStream(assets.getAssets().open("notification/"+(ring?"circle":"light")+".svga"));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){out.write(b,0,n);if(out.size()>8*1024*1024)throw new IOException("Oversized notification preset");}bytes=out.toByteArray();
        }
        Object adapter=movie.getField("h").get(null);
        Object decoded=adapter.getClass().getMethod("c",byte[].class).invoke(adapter,(Object)bytes);
        return new Model(entity.getConstructor(movie,File.class).newInstance(decoded,assets.getCacheDir()));
    }
    private final Model model;
    private final boolean ring;
    private final ArrayList<ImageView> images=new ArrayList<>();
    private final Field frame;
    private ValueAnimator animator;
    private int ringColor=Color.WHITE;
    FlymeNotificationLight(Context context,Model model,boolean ring)throws Exception {
        super(context);this.model=model;this.ring=ring;
        frame=Class.forName("P4.d",true,FlymeNotificationLight.class.getClassLoader()).getField("b");
        setClipChildren(false);setClipToPadding(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    private ImageView image()throws Exception {
        Class<?> type=Class.forName("com.opensource.svgaplayer.SVGAImageView",true,FlymeNotificationLight.class.getClassLoader());
        ImageView view=(ImageView)type.getConstructor(Context.class,AttributeSet.class).newInstance(getContext(),null);
        type.getMethod("setClearsAfterStop",boolean.class).invoke(view,false);
        type.getMethod("setClearsAfterDetached",boolean.class).invoke(view,false);
        type.getMethod("setVideoItem",model.entity.getClass()).invoke(view,model.entity);
        ImageView.ScaleType scale=ring?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.FIT_XY;
        Drawable drawable=view.getDrawable();drawable.getClass().getField("c").set(drawable,scale);
        view.setScaleType(scale);images.add(view);tint(view);return view;
    }
    void setRingColor(int color){ringColor=color|0xff000000;for(ImageView image:images)tint(image);}
    private void tint(ImageView image){
        if(!ring||ringColor==Color.WHITE){image.setLayerType(View.LAYER_TYPE_NONE,null);return;}
        // Tint only this small ring layer; keep the official per-frame alpha.
        Paint paint=new Paint();paint.setColorFilter(new LightingColorFilter(ringColor,0));
        image.setLayerType(View.LAYER_TYPE_HARDWARE,paint);
    }
    void layoutForDisplay(int width,int height,DisplayCutout cutout,int offsetX,int offsetY)throws Exception {
        stop();removeAllViews();images.clear();
        if(ring){
            for(RectF bounds:CameraHoleGeometry.resolve(getContext().getDisplay(),cutout,width,height,getResources().getDisplayMetrics().density)){
                // Keep the inner edge outside the physical camera, not merely the
                // outer edge. Read fresh geometry above on every preview/playback.
                int w=ringSize(bounds,getResources().getDisplayMetrics().density),h=w;
                LayoutParams lp=new LayoutParams(w,h,Gravity.TOP|Gravity.LEFT);
                lp.leftMargin=Math.round(bounds.centerX()-w/2f)-offsetX;
                lp.topMargin=Math.round(bounds.centerY()-h/2f)-offsetY;
                addView(image(),lp);
            }
        }else{
            int edge=Math.max(1,Math.round(height*100f/3200f));
            addView(image(),new LayoutParams(edge,-1,Gravity.LEFT));
            ImageView right=image();right.setRotation(180f);addView(right,new LayoutParams(edge,-1,Gravity.RIGHT));
        }
    }
    static int ringSize(RectF hole,float density){
        float innerDiameter=Math.max(hole.width(),hole.height())+4f*density;
        // Official asset: 72 px clear inner circle in its 111 px design canvas.
        return (int)Math.ceil(innerDiameter*111f/72f);
    }
    static List<RectF> holes(DisplayCutout cutout,int width,int height,float density){
        ArrayList<RectF> result=new ArrayList<>();
        if(cutout!=null&&Build.VERSION.SDK_INT>=31&&cutout.getCutoutPath()!=null){
            PathMeasure measure=new PathMeasure(cutout.getCutoutPath(),false);
            do{
                Path contour=new Path();measure.getSegment(0,measure.getLength(),contour,true);
                RectF b=new RectF();contour.computeBounds(b,true);
                if(validHole(b,width,height)&&Math.abs(b.width()-b.height())<=Math.max(2,Math.min(b.width(),b.height())*.15f))result.add(b);
            }while(measure.nextContour());
        }
        // Bounds touching the screen edge may include the whole status-bar gap.
        // They are not a physical camera mask; don't use their height as a diameter.
        if(result.isEmpty()&&cutout!=null)for(Rect r:cutout.getBoundingRects()){
            RectF b=new RectF(r);
            if(validHole(b,width,height)&&Math.abs(b.width()-b.height())<=2)result.add(b);
        }
        if(result.isEmpty()){
            float size=28*density,cy=24*density;
            result.add(new RectF(width/2f-size/2,cy-size/2,width/2f+size/2,cy+size/2));
        }
        return result;
    }
    private static boolean validHole(RectF b,int width,int height){
        return b.width()>2&&b.height()>2&&b.left>0&&b.top>0&&b.right<width&&b.bottom<height
                &&b.width()<Math.min(width,height)*.4f&&b.height()<Math.min(width,height)*.4f;
    }
    void seek(int index){
        try{for(ImageView image:images){Drawable d=image.getDrawable();frame.setInt(d,Math.max(0,Math.min(model.frames-1,index)));d.invalidateSelf();}}
        catch(IllegalAccessException error){throw new IllegalStateException(error);}
    }
    void play(Runnable complete){
        stop();seek(0);
        ValueAnimator next=ValueAnimator.ofInt(0,model.frames-1);animator=next;
        next.setDuration(model.duration());next.setInterpolator(new LinearInterpolator());
        next.addUpdateListener(a->seek((Integer)a.getAnimatedValue()));
        next.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(animator==a){animator=null;complete.run();}}});
        next.start();
    }
    void stop(){ValueAnimator old=animator;animator=null;if(old!=null){old.removeAllListeners();old.removeAllUpdateListeners();old.cancel();}}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
