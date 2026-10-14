package org.aliveclean;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Non-destructive, per-slot edit. The same transform is used for stills and all video frames. */
final class VivoSlotCropEditor implements AutoCloseable {
    interface Saved {void save(VivoOptions.Slot slot);}
    private final Dialog dialog;
    private final Bitmap source;
    private Bitmap rotated;
    private final VivoOptions.Slot slot;
    private final PhotoViewport viewport=new PhotoViewport();
    private final CropView view;
    private boolean closed;
    VivoSlotCropEditor(Activity activity,String title,Bitmap source,VivoOptions.Slot initial,boolean video,Saved saved){
        this.source=source;slot=initial.copy();rotated=VivoImages.rotate(source,slot.rotation);viewport.restore(slot.x,slot.y,slot.zoom);
        dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(1);body.setBackgroundColor(Color.BLACK);
        VivoUi.fitSystemBars(body);
        TextView heading=text(activity,title,18);body.addView(heading,new LinearLayout.LayoutParams(-1,dp(activity,52)));
        TextView hint=text(activity,video?"拖动或双指缩放 · 视频首帧预览，编辑应用于整段视频":"拖动或双指缩放，调整当前图片",13);
        body.addView(hint,new LinearLayout.LayoutParams(-1,dp(activity,42)));
        view=new CropView(activity);body.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(activity);body.addView(buttons,new LinearLayout.LayoutParams(-1,dp(activity,60)));
        action(buttons,"取消",()->dialog.dismiss());
        action(buttons,"复位",()->{slot.rotation=0;rebuild();viewport.reset();view.invalidate();});
        action(buttons,"旋转 90°",()->{slot.rotation=(slot.rotation+1)%4;rebuild();viewport.reset();view.invalidate();});
        action(buttons,"保存",()->{slot.x=viewport.centerX();slot.y=viewport.centerY();slot.zoom=viewport.zoom();saved.save(slot.copy());dialog.dismiss();});
        dialog.setContentView(body);dialog.setOnDismissListener(d->release());dialog.show();
        dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setStatusBarColor(Color.BLACK);dialog.getWindow().setNavigationBarColor(Color.BLACK);
    }
    private void rebuild(){if(rotated!=source)rotated.recycle();rotated=VivoImages.rotate(source,slot.rotation);view.measureFrame();}
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private static TextView text(Activity a,String title,int size){TextView t=new TextView(a);t.setText(title);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
    private void action(LinearLayout row,String name,Runnable click){TextView t=text((Activity)row.getContext(),name,15);row.addView(t,new LinearLayout.LayoutParams(0,-1,1));t.setOnClickListener(v->click.run());}
    private final class CropView extends View {
        private final RectF frame=new RectF();
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector scale;
        private float lastX,lastY;private boolean drag;
        CropView(Activity a){super(a);setContentDescription("当前素材裁切预览");
            scale=new ScaleGestureDetector(a,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){
                    if(frame.isEmpty())return false;
                    viewport.scale(d.getScaleFactor(),(d.getFocusX()-frame.left)/frame.width(),(d.getFocusY()-frame.top)/frame.height());invalidate();return true;
                }
            });
        }
        void measureFrame(){
            float aspect=(float)getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().heightPixels;
            float h=Math.max(1,getHeight()-24),w=Math.min(Math.max(1,getWidth()-32),h*aspect);h=w/aspect;
            frame.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
            viewport.dimensions(rotated.getWidth(),rotated.getHeight(),Math.max(1,(int)w),Math.max(1,(int)h));
        }
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){measureFrame();}
        @Override protected void onDraw(Canvas canvas){
            if(closed||frame.isEmpty())return;
            RectF src=new RectF(viewport.left()*rotated.getWidth(),viewport.top()*rotated.getHeight(),
                (viewport.left()+viewport.width())*rotated.getWidth(),(viewport.top()+viewport.height())*rotated.getHeight());
            Matrix matrix=new Matrix();matrix.setRectToRect(src,frame,Matrix.ScaleToFit.FILL);
            canvas.save();canvas.clipRect(frame);canvas.drawBitmap(rotated,matrix,paint);canvas.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xffeeeeee);canvas.drawRect(frame,paint);paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(frame.isEmpty())return true;scale.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:drag=frame.contains(e.getX(),e.getY());break;
                case MotionEvent.ACTION_MOVE:if(drag&&e.getPointerCount()==1&&!scale.isInProgress()){viewport.drag((e.getX()-lastX)/frame.width(),(e.getY()-lastY)/frame.height());invalidate();}break;
                case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_POINTER_UP:drag=false;break;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:drag=false;break;
            }
            lastX=e.getX();lastY=e.getY();return true;
        }
    }
    private void release(){if(closed)return;closed=true;if(rotated!=source)rotated.recycle();source.recycle();}
    @Override public void close(){dialog.dismiss();release();}
}
