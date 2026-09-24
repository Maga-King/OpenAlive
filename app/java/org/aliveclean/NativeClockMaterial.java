package org.aliveclean;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.view.View;
import java.lang.reflect.Method;

/** Uses the installed ColorOS glass shader; owns no wallpaper capture or timer. */
final class NativeClockMaterial implements AutoCloseable {
    private final View root;
    private Bitmap wallpaper;
    private Bitmap softGradient;
    private View target;
    private Object builder;
    private Class<?> type,vec2,vec4,config;
    private boolean enabled,closed,failed;
    private boolean soft;
    private boolean wallpaperSoft;
    private final android.graphics.RectF lastRect=new android.graphics.RectF();
    private final android.view.ViewTreeObserver.OnPreDrawListener preDraw=()->{
        if(enabled&&target!=null&&(soft||hasWallpaper())){
            android.graphics.RectF rect=wallpaperRect();
            if(!rect.equals(lastRect))refresh();
        }
        return true;
    };
    private final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->refresh();
    NativeClockMaterial(View root){this.root=root;root.getViewTreeObserver().addOnPreDrawListener(preDraw);}
    boolean hasWallpaper(){return wallpaper!=null&&!wallpaper.isRecycled();}
    Bitmap wallpaper(){return hasWallpaper()?wallpaper:null;}
    boolean applied(){return builder!=null&&enabled&&(soft||hasWallpaper());}
    void wallpaper(Bitmap bitmap){
        // The protocol owns this bitmap. Never recycle or mutate it here.
        wallpaper=bitmap!=null&&!bitmap.isRecycled()?bitmap:null;refresh();
    }
    void scene(View face,int mode){
        if(target!=face){
            clear();
            if(target!=null)target.removeOnLayoutChangeListener(layout);
            target=face;if(target!=null)target.addOnLayoutChangeListener(layout);
        }
        soft=mode==5;wallpaperSoft=mode==6;enabled=mode==1||soft||wallpaperSoft;refresh();
    }
    private void refresh(){
        if(closed||failed||target==null)return;
        if(!enabled||(!soft&&!hasWallpaper())){clear();return;}
        if(target.getWidth()<=0||target.getHeight()<=0)return;
        try{
            if(builder==null){
                Context plugin=root.getContext().createPackageContext("com.oplus.keyguard.personality.clocks",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader loader=plugin.getClassLoader();String ns="com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.";
                type=loader.loadClass(ns+"GlassEffectBuilder");vec2=loader.loadClass(ns+"GlassEffectBuilder$Vec2");vec4=loader.loadClass(ns+"GlassEffectBuilder$Vec4");config=loader.loadClass(ns+"GlassRegionConfig");
                builder=type.getConstructor().newInstance();
                type.getMethod("init",int.class,int.class,boolean.class,String.class).invoke(builder,target.getWidth(),target.getHeight(),true,"OpenAliveClock");
                // All original glyphs stay white; every native region uses the
                // same transparent material, including antialiased edge pixels.
                Object region=config.getConstructor(float.class).newInstance(0f);
                config.getMethod("setMaskColor",int.class).invoke(region,0);
                Method set=type.getMethod("setConfig",int.class,config,config);
                for(int i=0;i<6;i++)set.invoke(builder,i,region,region);
                type.getMethod("setMaskColorProgress",float.class).invoke(builder,1f);
            }
            Bitmap sample=wallpaper;
            if(soft){
                if(softGradient==null){
                    android.util.DisplayMetrics display=root.getResources().getDisplayMetrics();
                    softGradient=softGradient(256,Math.max(1,Math.round(256f*display.heightPixels/display.widthPixels)));
                }
                sample=softGradient;
            }
            type.getMethod("setWallpaperBg",Bitmap.class,Bitmap.class).invoke(builder,sample,sample);
            // Retain the native blur and wallpaper sampling, without glass
            // refraction for the wallpaper-following soft material.
            type.getMethod("setGlass",float.class).invoke(builder,wallpaperSoft?0f:1f);
            type.getMethod("setEffectSize",int.class,int.class).invoke(builder,target.getWidth(),target.getHeight());
            android.util.DisplayMetrics dm=root.getResources().getDisplayMetrics();
            Object resolution=vec2.getConstructor(float.class,float.class).newInstance((float)dm.widthPixels,(float)dm.heightPixels);
            android.graphics.RectF rect=wallpaperRect();lastRect.set(rect);
            Object crop=vec4.getConstructor(float.class,float.class,float.class,float.class).newInstance(rect.left,rect.top,rect.width(),rect.height());
            type.getMethod("setClockRect",vec2,vec4,int.class,float.class).invoke(builder,resolution,crop,0,1f);
            type.getMethod("buildRenderEffect").invoke(builder);
            RenderEffect effect=(RenderEffect)type.getMethod("getRenderEffect").invoke(builder);
            if(effect==null)throw new IllegalStateException("Native material effect is empty");
            target.setRenderEffect(effect);
        }catch(Exception failure){failed=true;clear();NativeClockLoadState.failure("Native clock glass",failure);}
    }
    private android.graphics.RectF wallpaperRect(){
        android.graphics.RectF rect=new android.graphics.RectF(0,0,target.getWidth(),target.getHeight());
        android.graphics.Matrix transform=new android.graphics.Matrix();target.transformMatrixToGlobal(transform);
        View host=NativeClockEditor.findRoot(root);
        if(host!=null){
            android.graphics.Matrix parent=new android.graphics.Matrix(),inverse=new android.graphics.Matrix();
            host.transformMatrixToGlobal(parent);
            if(parent.invert(inverse))transform.postConcat(inverse);
        }
        transform.mapRect(rect);return rect;
    }
    private void clear(){
        if(target!=null)target.setRenderEffect(null);
        if(builder!=null){try{type.getMethod("release").invoke(builder);}catch(Exception ignored){}builder=null;}
    }
    void settled(){refresh();}
    static Bitmap softGradient(int width,int height){
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        android.graphics.Paint paint=new android.graphics.Paint(3);
        // Same sampling backdrop and native material settings as the lockscreen
        // demonstration, shared with its visual regression test.
        paint.setShader(new android.graphics.LinearGradient(0,0,width,height,new int[]{0xff0d4878,0xffec9666,0xff227452},null,android.graphics.Shader.TileMode.CLAMP));
        new android.graphics.Canvas(bitmap).drawRect(0,0,width,height,paint);return bitmap;
    }
    public void close(){closed=true;clear();if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnPreDrawListener(preDraw);if(target!=null)target.removeOnLayoutChangeListener(layout);target=null;wallpaper=null;if(softGradient!=null){softGradient.recycle();softGradient=null;}}
}
