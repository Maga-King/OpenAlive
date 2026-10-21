package org.aliveclean;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

/** Original SuperWallpaperClock, updated by ColorOS's existing AOD minute ticks. */
final class XiaomiAodClockView extends AodClockFace {
    private final XiaomiAodPreview.Renderer renderer;
    private final ClockUpdates updates;
    private final int[] location=new int[2];
    private boolean enabled;
    private Runnable onFailure;
    private long minute=Long.MIN_VALUE;
    XiaomiAodClockView(Context context,Bundle metadata){
        super(context);setClickable(false);setFocusable(false);setClipChildren(false);
        try{
            Context assets=context.getPackageName().equals("org.aliveclean")?context:context.createPackageContext("org.aliveclean",0);
            Context resources=NativeClockProvider.resourceContext(context,assets.getAssets());
            renderer=new XiaomiAodPreview.Renderer(resources,metadata);
            addView(renderer.view,new LayoutParams(-1,-1));
        }catch(Exception|LinkageError error){throw new IllegalStateException("Original Xiaomi AOD clock unavailable",error);}
        updates=new ClockUpdates(context,false,time->{
            try{refresh(time);}catch(RuntimeException|LinkageError error){
                enabled=false;updatesStop();android.util.Log.w("OpenAliveXiaomi","Original clock refresh unavailable",error);
                if(onFailure!=null)onFailure.run();
            }
        });
    }
    void whenFailed(Runnable callback){onFailure=callback;}
    private void updatesStop(){if(updates!=null)updates.stop();}
    @Override void active(boolean value){enabled=value;activity();}
    @Override void tick(long now){if(enabled&&minute!=Math.floorDiv(now,60000L))refresh(now);}
    @Override void refresh(long now){
        try{renderer.update();minute=Math.floorDiv(now,60000L);}
        catch(Exception error){throw new IllegalStateException("Original Xiaomi AOD clock update failed",error);}
    }
    @Override int notificationTop(){
        View content=renderer.content;content.getLocationOnScreen(location);
        return content.getHeight()==0?0:Math.round(location[1]+content.getHeight()+getWidth()*.035f);
    }
    private void activity(){
        boolean visible=enabled&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE;
        renderer.active(visible);if(visible)updates.start();else updates.stop();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();activity();}
    @Override protected void onDetachedFromWindow(){renderer.active(false);updates.stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(updates!=null)activity();}
}
