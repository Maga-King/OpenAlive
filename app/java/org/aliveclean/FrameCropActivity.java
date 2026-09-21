package org.aliveclean;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Official layout and PhotoView, with a local square/angle configuration adapter. */
public final class FrameCropActivity extends Activity {
    private OfficialUi ui;
    private ImageView photo;
    private Bitmap bitmap;
    private String source;
    private FrameCrop pending;
    private boolean committed,newPhoto,settingAngle;
    private SeekBar angle;
    private TextView angleLabel;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        SceneOptions options=new SceneOptions(getSharedPreferences(SceneOptions.DRAFT,0));
        source=getIntent().getStringExtra("photo");if(source==null)source=options.framePhoto;
        newPhoto=getIntent().getBooleanExtra("new_photo",false);
        if(!source.matches("[A-Za-z0-9._-]+")||source.equals(".")||source.equals("..")){finish();return;}
        pending=newPhoto?new FrameCrop(.5f,.5f,1,0):new FrameCrop(options.frameX,options.frameY,options.frameSize,options.frameAngle);
        if(state!=null)pending=new FrameCrop(state.getFloat("x",.5f),state.getFloat("y",.5f),state.getFloat("size",1),state.getFloat("angle",0));
        new Thread(()->{
            Bitmap decoded=null;
            try{
                decoded=ImageDecoder.decodeBitmap(ImageDecoder.createSource(new File(getFilesDir(),source)),(decoder,info,input)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1,2048f/Math.max(w,h));
                    decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                decoded.setDensity(Bitmap.DENSITY_NONE);Bitmap ready=decoded;
                runOnUiThread(()->{if(isDestroyed()||isFinishing()){ready.recycle();return;}bitmap=ready;try{setup();}catch(Exception e){fail(e);}});
            }catch(Exception e){if(decoded!=null)decoded.recycle();runOnUiThread(()->fail(e));}
        },"FramePhotoDecode").start();
    }
    private void setup()throws Exception{
        ui=new OfficialUi(this);
        View root=ui.inflate("activity_editor",new FrameLayout(this));root.setBackgroundColor(0xff000000);setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });root.requestApplyInsets();
        // This host supplies data and placement only; XML widgets, styles and
        // PhotoView gestures execute unchanged official code in isolated resources.
        for(int i=0;i<((ViewGroup)root).getChildCount();i++)if(((ViewGroup)root).getChildAt(i) instanceof SurfaceView)((ViewGroup)root).getChildAt(i).setVisibility(View.GONE);
        View cancel=ui.find(root,"btn_cancel");cancel.setOnClickListener(v->finish());cancel.bringToFront();
        TextView done=(TextView)ui.find(root,"btn_apply");done.setText("完成");done.setOnClickListener(v->save());done.bringToFront();
        FrameLayout preview=(FrameLayout)ui.find(root,"sysui_legacy_aod_preview_container");
        View crop=ui.inflate("sysui_aod_preview_image_clock",preview);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);
        position.bottomMargin=Math.round(200*getResources().getDisplayMetrics().density);preview.addView(crop,position);
        photo=(ImageView)ui.find(crop,"iv_date_time_custom");
        ((TextView)ui.find(crop,"tv_datetime")).setText("双指缩放，拖动选择中心");ui.find(crop,"tv_lunar_calendar").setVisibility(View.GONE);
        FrameLayout panel=(FrameLayout)ui.find(root,"fl_aod_panel_container");
        View tools=ui.inflate("view_aod_image_edit_panel",panel);
        panel.addView(tools,new FrameLayout.LayoutParams(-1,Math.round(240*getResources().getDisplayMetrics().density),Gravity.BOTTOM));
        ((TextView)ui.find(tools,"aod_image_choose_wallpaper_btn")).setText("重置取景");
        ui.find(tools,"aod_image_choose_wallpaper_btn").setOnClickListener(v->{try{restore(new FrameCrop(.5f,.5f,1,0));}catch(Exception e){fail(e);}});
        angleLabel=(TextView)ui.find(tools,"label_style_tv");
        ui.find(tools,"iv_size_smaller").setVisibility(View.GONE);ui.find(tools,"iv_size_larger").setVisibility(View.GONE);
        angle=(SeekBar)ui.find(tools,"radius_adjust_seekbar");angle.setMax(9000);
        angle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
            public void onProgressChanged(SeekBar bar,int value,boolean user){if(user&&!settingAngle&&bitmap!=null){try{FrameCrop c=current();restore(new FrameCrop(c.x,c.y,c.size,value/100f-45));}catch(Exception e){fail(e);}}}
        });
        photo.setImageBitmap(bitmap);
        photo.getClass().getMethod("setMaximumScale",float.class).invoke(photo,20f);
        photo.post(()->{try{restore(pending);}catch(Exception e){fail(e);}});
    }
    private FrameCrop current(){
        if(photo==null||bitmap==null||photo.getWidth()==0)return pending;
        Matrix inverse=new Matrix();Matrix matrix=photo.getImageMatrix();if(!matrix.invert(inverse))return pending;
        float[] center={photo.getWidth()/2f,photo.getHeight()/2f};inverse.mapPoints(center);
        float[] m=new float[9];matrix.getValues(m);float scale=(float)Math.hypot(m[0],m[3]);
        return new FrameCrop(center[0]/bitmap.getWidth(),center[1]/bitmap.getHeight(),
                photo.getWidth()/scale/Math.min(bitmap.getWidth(),bitmap.getHeight()),
                (float)Math.toDegrees(Math.atan2(m[3],m[0]))).fit(bitmap.getWidth(),bitmap.getHeight());
    }
    private void restore(FrameCrop state)throws Exception{
        if(photo.getWidth()==0)return;
        pending=state.fit(bitmap.getWidth(),bitmap.getHeight());
        float scale=photo.getWidth()/(pending.size*Math.min(bitmap.getWidth(),bitmap.getHeight()));
        Matrix wanted=new Matrix();wanted.setTranslate(-pending.x*bitmap.getWidth(),-pending.y*bitmap.getHeight());
        wanted.postRotate(pending.angle);wanted.postScale(scale,scale);wanted.postTranslate(photo.getWidth()/2f,photo.getHeight()/2f);
        Object attacher=photo.getClass().getMethod("getAttacher").invoke(photo);
        Matrix base=(Matrix)attacher.getClass().getField("m").get(attacher),inverse=new Matrix();
        if(!base.invert(inverse))throw new IOException("取景矩阵不可用");
        Matrix supplement=new Matrix(wanted);supplement.preConcat(inverse);
        ((Matrix)attacher.getClass().getField("o").get(attacher)).set(supplement);
        attacher.getClass().getMethod("a").invoke(attacher);
        settingAngle=true;angle.setProgress(Math.round((pending.angle+45)*100));settingAngle=false;
        angleLabel.setText(String.format(java.util.Locale.getDefault(),"角度 %.1f°",pending.angle));
    }
    private void save(){
        FrameCrop c=current();
        boolean ok=getSharedPreferences(SceneOptions.DRAFT,0).edit().putBoolean("frame_pair",true).putString("frame_photo",source)
                .putFloat("frame_x",c.x).putFloat("frame_y",c.y).putFloat("frame_size",c.size).putFloat("frame_angle",c.angle).commit();
        if(ok){committed=true;setResult(RESULT_OK);finish();}else Toast.makeText(this,"取景保存失败",Toast.LENGTH_LONG).show();
    }
    private void fail(Exception e){if(isDestroyed())return;android.util.Log.e("AliveClean","Official crop host failed",e);Toast.makeText(this,"取景页面无法打开："+e.getMessage(),Toast.LENGTH_LONG).show();finish();}
    @Override protected void onSaveInstanceState(Bundle state){FrameCrop c=current();state.putFloat("x",c.x);state.putFloat("y",c.y);state.putFloat("size",c.size);state.putFloat("angle",c.angle);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){
        if(photo!=null)photo.setImageDrawable(null);if(bitmap!=null)bitmap.recycle();
        if(ui!=null)ui.close();if(isFinishing()&&newPhoto&&!committed&&source!=null)new File(getFilesDir(),source).delete();super.onDestroy();
    }
}
