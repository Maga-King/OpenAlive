package org.aliveclean.uihost;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import java.util.ArrayList;

/** Runs the actual official view/drawable bytecode with its original resource IDs. */
public final class OfficialUiInstrumentation extends Instrumentation {
    private final ArrayList<String> failures=new ArrayList<>();
    private int checked;
    private OfficialUiHost activity;
    private final ArrayList<String> observations=new ArrayList<>();

    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}

    private void check(String name,Runnable action){
        try{action.run();checked++;}
        catch(Throwable t){failures.add(name+": "+android.util.Log.getStackTraceString(t));}
    }

    private void render(String layout){
        int id=activity.getResources().getIdentifier(layout,"layout","com.flyme.systemuieditor");
        if(id==0)throw new AssertionError("Missing official layout: "+layout);
        FrameLayout root=new FrameLayout(activity);
        View view=activity.getLayoutInflater().inflate(id,root,false);
        root.addView(view);
        int width=activity.getResources().getDisplayMetrics().widthPixels;
        int height=activity.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        root.layout(0,0,width,height);
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try{root.draw(new Canvas(image));}finally{image.recycle();}
    }

    // Exercise the original square PhotoView and its original inverse-matrix
    // crop helper, rather than copying either algorithm into this host.
    private void squareCrop(int sourceWidth,int sourceHeight){
        Bitmap bitmap=Bitmap.createBitmap(sourceWidth,sourceHeight,Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        ImageView photo=null;
        try{
            int layout=activity.getResources().getIdentifier("sysui_aod_preview_image_clock","layout","com.flyme.systemuieditor");
            View root=activity.getLayoutInflater().inflate(layout,new FrameLayout(activity),false);
            int photoId=activity.getResources().getIdentifier("iv_date_time_custom","id","com.flyme.systemuieditor");
            photo=(ImageView)root.findViewById(photoId);
            int width=activity.getResources().getDisplayMetrics().widthPixels;
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            root.layout(0,0,width,root.getMeasuredHeight());
            int side=Math.round(160*activity.getResources().getDisplayMetrics().density);
            if(photo.getWidth()!=side||photo.getHeight()!=side)throw new AssertionError("Official crop is not 160 dp square");
            photo.setImageBitmap(bitmap);
            Class<?> continuation=Class.forName("O5.d");
            Class<?> helper=Class.forName("z2.c");
            for(int zoom:new int[]{1,2}){
                photo.getClass().getMethod("setScale",float.class).invoke(photo,(float)zoom);
                Object calculation=helper.getConstructor(ImageView.class,int.class,int.class,continuation)
                        .newInstance(photo,sourceWidth,sourceHeight,null);
                Rect actual=(Rect)helper.getMethod("t",Object.class).invoke(calculation,(Object)null);
                int cropSide=Math.min(sourceWidth,sourceHeight)/zoom;
                Rect expected=new Rect((sourceWidth-cropSide)/2,(sourceHeight-cropSide)/2,
                        (sourceWidth+cropSide)/2,(sourceHeight+cropSide)/2);
                if(!expected.equals(actual))throw new AssertionError("Crop "+sourceWidth+"x"+sourceHeight+" zoom="+zoom+": "+actual+" expected "+expected);
                observations.add("OFFICIAL_SQUARE_CROP source="+sourceWidth+"x"+sourceHeight+" zoom="+zoom+" rect="+actual);
            }
            long down=SystemClock.uptimeMillis();
            float center=side/2f;
            for(int step=0;step<3;step++){
                MotionEvent event=MotionEvent.obtain(down,down+16*step,
                        step==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_MOVE,
                        center+side*step/16f,center,0);
                try{photo.dispatchTouchEvent(event);}finally{event.recycle();}
            }
            Object calculation=helper.getConstructor(ImageView.class,int.class,int.class,continuation)
                    .newInstance(photo,sourceWidth,sourceHeight,null);
            Rect panned=(Rect)helper.getMethod("t",Object.class).invoke(calculation,(Object)null);
            MotionEvent cancel=MotionEvent.obtain(down,down+48,MotionEvent.ACTION_CANCEL,center+side/8f,center,0);
            try{photo.dispatchTouchEvent(cancel);}finally{cancel.recycle();}
            int cropSide=Math.min(sourceWidth,sourceHeight)/2;
            if(panned==null||Math.abs(panned.width()-cropSide)>1||Math.abs(panned.height()-cropSide)>1
                    ||panned.left>=(sourceWidth-cropSide)/2||panned.left<0||panned.right>sourceWidth)
                throw new AssertionError("Official crop did not retain dragged center: "+panned);
            observations.add("OFFICIAL_SQUARE_PAN source="+sourceWidth+"x"+sourceHeight+" rect="+panned);
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        finally{if(photo!=null)photo.setImageDrawable(null);bitmap.recycle();}
    }

    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Intent intent=new Intent(getTargetContext(),OfficialUiHost.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(OfficialUiHost)startActivitySync(intent);
            runOnMainSync(()->{
                for(String layout:new String[]{"activity_editor","view_editor_tab","editor_aod_button",
                    "editor_lockscreen_button_photo_wp_legacy_sysui","editor_launcher_button_photo_wp_legacy_sysui",
                    "view_aod_panel","view_editor_aod_panel","view_editor_lockscreen_texture_panel",
                    "view_editor_launcher_effect_panel","item_aod_main_panel_image_with_label",
                    "item_launcher_main_panel_effect","view_sysui_aod_clock_preview",
                    "view_sysui_lockscreen_clock_preview","view_sysui_aod_sync_lockscreen_legacy_clock_preview",
                    "fragment_wallpaper_crop","sysui_aod_preview_image_clock","view_aod_image_edit_panel",
                    "view_editor_aod_image_panel","item_aod_main_panel_custom_image_with_label"})check(layout,()->render(layout));
                check("official_square_crop_portrait",()->squareCrop(800,1200));
                check("official_square_crop_landscape",()->squareCrop(1200,800));
                check("official_square_crop_square",()->squareCrop(800,800));
                check("photo_source_dialog",()->{
                    AlertDialog dialog=new AlertDialog.Builder(activity).setItems(new String[]{"Photo A","Photo B"},null).create();
                    try{dialog.show();dialog.getListView().setSelection(1);}finally{dialog.dismiss();}
                });
                check("photo_grid",()->{
                    GridView grid=new GridView(activity);grid.setNumColumns(3);
                    AlertDialog dialog=new AlertDialog.Builder(activity).setView(grid).create();
                    try{dialog.show();grid.setPressed(true);grid.setPressed(false);}finally{dialog.dismiss();}
                });
            });
        }catch(Throwable t){failures.add(android.util.Log.getStackTraceString(t));}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());}
        result.putString("stream","OFFICIAL_UI_"+(failures.isEmpty()?"OK":"FAILED")+" checks="+checked+"\n"+String.join("\n",observations)+"\n"+String.join("\n",failures));
        finish(failures.isEmpty()?-1:1,result);
    }
}
