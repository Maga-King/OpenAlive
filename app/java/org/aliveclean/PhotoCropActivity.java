package org.aliveclean;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Fixed display-aspect crop. Source remains private and untouched until confirmation. */
public final class PhotoCropActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(I18n.wrap(base));}
    private final PhotoViewport viewport=new PhotoViewport();
    private String source;
    private Bitmap bitmap;
    private CropView cropView;
    private TextView done;
    private int screenWidth,screenHeight;
    private boolean saving;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        source=getIntent().getStringExtra("photo");
        if(source==null||!source.matches("photo-[A-Za-z0-9._-]+")||!new File(getFilesDir(),source).isFile()){finish();return;}
        Point screen=new Point();getWindowManager().getDefaultDisplay().getRealSize(screen);
        screenWidth=screen.x;screenHeight=screen.y;
        if(state!=null)viewport.restore(state.getFloat("x",.5f),state.getFloat("y",.5f),state.getFloat("zoom",1));
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.BLACK);setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });root.requestApplyInsets();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(16),dp(8),dp(16),dp(8));root.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView cancel=button(I18n.t("取消"),false);top.addView(cancel,new LinearLayout.LayoutParams(dp(72),-1));cancel.setOnClickListener(v->finish());
        TextView title=text(I18n.t("裁切照片"),18);top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        done=button(I18n.t("完成"),true);top.addView(done,new LinearLayout.LayoutParams(dp(72),-1));done.setEnabled(false);done.setOnClickListener(v->save());
        cropView=new CropView();root.addView(cropView,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=text(I18n.t("双指缩放，拖动调整位置"),14);hint.setTextColor(0xffbbbbbb);root.addView(hint,new LinearLayout.LayoutParams(-1,dp(36)));
        TextView reset=button(I18n.t("复位"),false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(86),dp(38));rp.gravity=Gravity.CENTER;rp.bottomMargin=dp(18);root.addView(reset,rp);reset.setOnClickListener(v->{if(!saving){viewport.reset();cropView.invalidate();}});
        new Thread(()->{try{
            Bitmap decoded=ImageDecoder.decodeBitmap(ImageDecoder.createSource(new File(getFilesDir(),source)),(decoder,info,input)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1,2048f/Math.max(w,h));
                decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            runOnUiThread(()->{if(isDestroyed()||isFinishing()){decoded.recycle();return;}bitmap=decoded;viewport.dimensions(bitmap.getWidth(),bitmap.getHeight(),screenWidth,screenHeight);done.setEnabled(true);cropView.invalidate();});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){Toast.makeText(this,I18n.t("无法读取照片：")+e.getMessage(),Toast.LENGTH_LONG).show();finish();}});}},"PhotoCropDecode").start();
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String label,int size){TextView v=new TextView(this);v.setText(label);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER);return v;}
    private TextView button(String label,boolean primary){TextView v=text(label,16);GradientDrawable d=new GradientDrawable();d.setColor(primary?0xff1676ff:0xff292929);d.setCornerRadius(dp(24));v.setBackground(d);return v;}

    final class CropView extends View {
        final RectF frame=new RectF();
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final ScaleGestureDetector scale;
        float lastX,lastY;boolean touching;
        CropView(){super(PhotoCropActivity.this);setContentDescription(I18n.t("照片裁切区域，可双指缩放和拖动"));
            scale=new ScaleGestureDetector(PhotoCropActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector detector){
                    viewport.scale(detector.getScaleFactor(),(detector.getFocusX()-frame.left)/frame.width(),(detector.getFocusY()-frame.top)/frame.height());invalidate();return true;
                }
            });
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            float width=Math.max(1,w-dp(32)),height=Math.max(1,h-dp(16)),ratio=(float)screenWidth/screenHeight;
            width=Math.min(width,height*ratio);height=width/ratio;
            frame.set((w-width)/2,(h-height)/2,(w+width)/2,(h+height)/2);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(bitmap==null)return;
            canvas.save();canvas.clipRect(frame);
            float scale=frame.width()/(bitmap.getWidth()*viewport.width());
            Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate(frame.left-viewport.left()*bitmap.getWidth()*scale,frame.top-viewport.top()*bitmap.getHeight()*scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawBitmap(bitmap,matrix,paint);canvas.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0xffbbbbbb);canvas.drawRect(frame,paint);
            if(touching){paint.setColor(0x88ffffff);for(int i=1;i<3;i++){float x=frame.left+frame.width()*i/3,y=frame.top+frame.height()*i/3;canvas.drawLine(x,frame.top,x,frame.bottom,paint);canvas.drawLine(frame.left,y,frame.right,y,paint);}}
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(bitmap==null||saving||frame.isEmpty())return false;
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN&&!frame.contains(event.getX(),event.getY()))return false;
            scale.onTouchEvent(event);
            float x=0,y=0;int count=0;
            for(int i=0;i<event.getPointerCount();i++){if(action==MotionEvent.ACTION_POINTER_UP&&i==event.getActionIndex())continue;x+=event.getX(i);y+=event.getY(i);count++;}
            if(count>0){x/=count;y/=count;}
            if(action==MotionEvent.ACTION_MOVE&&touching)viewport.drag((x-lastX)/frame.width(),(y-lastY)/frame.height());
            lastX=x;lastY=y;touching=action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL;invalidate();return true;
        }
    }
    static Bitmap export(File input,PhotoViewport selection,int outWidth,int outHeight)throws IOException{
        final float left=selection.left(),top=selection.top(),width=selection.width(),height=selection.height();
        Bitmap decoded=ImageDecoder.decodeBitmap(ImageDecoder.createSource(input),(decoder,info,source)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();
            float scale=Math.min(1,Math.max(outWidth/(w*width),outHeight/(h*height)));
            scale=Math.min(scale,8192f/Math.max(w,h));
            int tw=Math.max(1,Math.round(w*scale)),th=Math.max(1,Math.round(h*scale));
            decoder.setTargetSize(tw,th);
            int l=Math.max(0,Math.min(tw-1,Math.round(left*tw))),t=Math.max(0,Math.min(th-1,Math.round(top*th)));
            int r=Math.max(l+1,Math.min(tw,Math.round((left+width)*tw))),b=Math.max(t+1,Math.min(th,Math.round((top+height)*th)));
            decoder.setCrop(new Rect(l,t,r,b));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        Bitmap result=Bitmap.createScaledBitmap(decoded,outWidth,outHeight,true);if(result!=decoded)decoded.recycle();return result;
    }
    private void save(){
        if(bitmap==null||saving)return;saving=true;done.setEnabled(false);done.setText(I18n.t("保存中"));
        PhotoViewport selected=new PhotoViewport();selected.dimensions(bitmap.getWidth(),bitmap.getHeight(),screenWidth,screenHeight);selected.restore(viewport.centerX(),viewport.centerY(),viewport.zoom());
        new Thread(()->{File output=null;try{
            Bitmap image=export(new File(getFilesDir(),source),selected,screenWidth,screenHeight);
            try{output=File.createTempFile("crop-",".png",getFilesDir());try(FileOutputStream out=new FileOutputStream(output)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException(I18n.t("无法保存裁切结果"));}}finally{image.recycle();}
            File ready=output;runOnUiThread(()->{if(isDestroyed()||isFinishing()){ready.delete();return;}setResult(RESULT_OK,new Intent().putExtra("photo",ready.getName()));finish();});
        }catch(Exception e){if(output!=null)output.delete();runOnUiThread(()->{if(!isDestroyed()){saving=false;done.setEnabled(true);done.setText(I18n.t("完成"));Toast.makeText(this,I18n.t("保存失败：")+e.getMessage(),Toast.LENGTH_LONG).show();}});}},"PhotoCropExport").start();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putFloat("x",viewport.centerX());state.putFloat("y",viewport.centerY());state.putFloat("zoom",viewport.zoom());super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){if(bitmap!=null){bitmap.recycle();bitmap=null;}if(isFinishing()&&source!=null&&source.matches("photo-[A-Za-z0-9._-]+"))new File(getFilesDir(),source).delete();super.onDestroy();}
}
