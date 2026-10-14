package org.aliveclean;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.UUID;

/** A photo-space mask editor. White enables the original effect; black excludes it. */
final class VivoMaskEditor {
    interface Saved {void save(String file);}
    private final Activity host;
    private final Dialog dialog;
    private final CanvasView canvas;
    private final Saved saved;
    private final boolean subject;
    private boolean saving,disposed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final TextView status,done;
    private VivoSubjectMask.Job segmentation;
    VivoMaskEditor(Activity host,Bitmap photo,Bitmap existing,boolean subject,Saved saved){
        this.host=host;this.saved=saved;this.subject=subject;
        dialog=new Dialog(host,android.R.style.Theme_Material_NoActionBar);
        LinearLayout body=new LinearLayout(host);body.setOrientation(1);body.setBackgroundColor(0xff101114);
        body.setPadding(dp(16),dp(12),dp(16),dp(16));
        LinearLayout heading=new LinearLayout(host);body.addView(heading,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView cancel=action("取消",()->dialog.dismiss());cancel.setOnClickListener(v->dialog.dismiss());heading.addView(cancel,new LinearLayout.LayoutParams(dp(60),-1));
        TextView title=label(subject?"主体蒙版":"涂抹范围",18);heading.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        done=action("完成",this::save);heading.addView(done,new LinearLayout.LayoutParams(dp(60),-1));
        status=label(subject?"白色选区为主体，背景自动取反；可用画笔修边":"涂抹需要产生动态效果的区域",13);
        status.setTextColor(0xffb8bcc6);body.addView(status,new LinearLayout.LayoutParams(-1,dp(42)));
        canvas=new CanvasView(host,photo,existing);body.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout tools=new LinearLayout(host);body.addView(tools,new LinearLayout.LayoutParams(-1,dp(48)));
        addTool(tools,"画笔",()->{canvas.erase=false;canvas.boxMode=false;status.setText("画笔：增加选区");});
        addTool(tools,"擦除",()->{canvas.erase=true;canvas.boxMode=false;status.setText("擦除：移除选区");});
        addTool(tools,"撤销",canvas::undo);addTool(tools,"清空",canvas::clear);
        if(subject){LinearLayout generate=new LinearLayout(host);body.addView(generate,new LinearLayout.LayoutParams(-1,dp(48)));
            addTool(generate,"框选主体",()->{canvas.boxMode=true;status.setText("拖动框选主体，再点生成；复杂边缘可用画笔修补");});
            addTool(generate,"自动识别",()->{canvas.box.set(0,0,1,1);generate();});
            addTool(generate,"识别框选",this::generate);
        }
        TextView brush=label("画笔大小",12);body.addView(brush);
        SeekBar size=new SeekBar(host);size.setMax(95);size.setProgress(25);body.addView(size,new LinearLayout.LayoutParams(-1,dp(38)));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int value,boolean user){canvas.brush=(value+5)/1000f;}});
        FrameLayout insets=new FrameLayout(host);insets.addView(body,new FrameLayout.LayoutParams(-1,-1));VivoUi.fitSystemBars(insets);
        dialog.setContentView(insets);dialog.setOnDismissListener(d->{disposed=true;if(segmentation!=null)segmentation.cancel();canvas.close();});
        dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setStatusBarColor(0xff101114);dialog.getWindow().setNavigationBarColor(0xff101114);
        if(subject&&existing==null)generate();
    }
    private int dp(int value){return Math.round(host.getResources().getDisplayMetrics().density*value);}
    void close(){dialog.dismiss();}
    private TextView label(String text,int size){TextView v=new TextView(host);v.setText(text);v.setTextSize(size);v.setTextColor(-1);v.setGravity(Gravity.CENTER);return v;}
    private TextView action(String text,Runnable action){TextView v=label(text,15);v.setOnClickListener(w->{if(!saving)action.run();});return v;}
    private void addTool(LinearLayout row,String text,Runnable action){row.addView(action(text,action),new LinearLayout.LayoutParams(0,-1,1));}
    private void generate(){
        if(saving)return;saving=true;done.setEnabled(false);canvas.setEnabled(false);status.setText("正在本机识别主体…");
        final Bitmap input=canvas.photo.copy(Bitmap.Config.ARGB_8888,false);final RectF box=new RectF(canvas.box);
        final VivoSubjectMask.Job job=new VivoSubjectMask.Job();segmentation=job;
        new Thread(()->{Bitmap result=null;Exception failure=null;
            try{result=job.generate(host.getApplicationContext(),input,box);}catch(Exception|LinkageError e){failure=new Exception(e);}finally{input.recycle();}
            final Bitmap ready=result;final Exception error=failure;
            main.post(()->{if(disposed){if(ready!=null)ready.recycle();return;}saving=false;done.setEnabled(true);canvas.setEnabled(true);
                if(error!=null){status.setText("生成失败，请手动涂抹："+error.getMessage());return;}
                canvas.snapshot();new Canvas(canvas.mask).drawBitmap(ready,null,new Rect(0,0,canvas.mask.getWidth(),canvas.mask.getHeight()),null);ready.recycle();canvas.boxMode=false;canvas.invalidate();
                status.setText("已生成主体蒙版；请检查边缘，画笔添加、擦除修整");
            });
        },"VivoSubjectMask").start();
    }
    private void save(){
        if(saving)return;saving=true;done.setEnabled(false);canvas.setEnabled(false);
        Bitmap snapshot=canvas.mask.copy(Bitmap.Config.ARGB_8888,false);String name="vivo-"+UUID.randomUUID()+"-"+(subject?"subject":"paint")+".png";
        new Thread(()->{Exception error=null;try(FileOutputStream output=host.openFileOutput(name,0)){
            if(!snapshot.compress(Bitmap.CompressFormat.PNG,100,output))throw new java.io.IOException("蒙版保存失败");output.getFD().sync();
        }catch(Exception e){error=e;}finally{snapshot.recycle();}
        final Exception failure=error;main.post(()->{if(disposed||failure!=null){host.deleteFile(name);if(!disposed){saving=false;canvas.setEnabled(true);done.setEnabled(true);status.setText("保存失败，请重试");}return;}saved.save(name);dialog.dismiss();});
        },"VivoMaskSave").start();
    }
    private static final class CanvasView extends View implements AutoCloseable {
        final Bitmap photo,mask;
        final RectF rect=new RectF(),box=new RectF(0,0,1,1);
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),overlay=new Paint(Paint.FILTER_BITMAP_FLAG);
        final ArrayDeque<int[]> history=new ArrayDeque<>();
        boolean erase,boxMode;float brush=.03f,lastX,lastY;
        CanvasView(Activity host,Bitmap source,Bitmap initial){super(host);
            float scale=Math.min(1f,1280f/Math.max(source.getWidth(),source.getHeight()));
            int w=Math.max(1,Math.round(source.getWidth()*scale)),h=Math.max(1,Math.round(source.getHeight()*scale));
            photo=Bitmap.createScaledBitmap(source,w,h,true);if(photo!=source)source.recycle();
            mask=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);mask.eraseColor(Color.BLACK);
            if(initial!=null){new Canvas(mask).drawBitmap(initial,null,new Rect(0,0,w,h),paint);initial.recycle();}
            // Convert white mask coverage to a translucent cyan overlay; black stays transparent.
            overlay.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{0,0,0,0,40,0,0,0,0,180,0,0,0,0,255,.42f,0,0,0,0})));
        }
        @Override protected void onDraw(Canvas c){
            float scale=Math.min((float)getWidth()/photo.getWidth(),(float)getHeight()/photo.getHeight());
            float w=photo.getWidth()*scale,h=photo.getHeight()*scale;rect.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
            c.drawBitmap(photo,null,rect,paint);c.drawBitmap(mask,null,rect,overlay);
            if(boxMode){paint.setColor(0xff58c8ff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);
                c.drawRect(rect.left+box.left*rect.width(),rect.top+box.top*rect.height(),rect.left+box.right*rect.width(),rect.top+box.bottom*rect.height(),paint);paint.setStyle(Paint.Style.FILL);}
        }
        void snapshot(){int[] pixels=new int[mask.getWidth()*mask.getHeight()];mask.getPixels(pixels,0,mask.getWidth(),0,0,mask.getWidth(),mask.getHeight());if(history.size()==6)history.removeFirst();history.addLast(pixels);}
        void undo(){if(history.isEmpty())return;mask.setPixels(history.removeLast(),0,mask.getWidth(),0,0,mask.getWidth(),mask.getHeight());invalidate();}
        void clear(){snapshot();mask.eraseColor(Color.BLACK);invalidate();}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(!isEnabled()||rect.width()==0)return true;
            float x=Math.max(0,Math.min(1,(e.getX()-rect.left)/rect.width())),y=Math.max(0,Math.min(1,(e.getY()-rect.top)/rect.height()));
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(!rect.contains(e.getX(),e.getY()))return false;getParent().requestDisallowInterceptTouchEvent(true);lastX=x;lastY=y;if(!boxMode)snapshot();}
            if(boxMode){if(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP)box.set(Math.min(lastX,x),Math.min(lastY,y),Math.max(lastX,x),Math.max(lastY,y));}
            else if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){
                paint.setColor(erase?Color.BLACK:Color.WHITE);paint.setStrokeWidth(brush*Math.max(mask.getWidth(),mask.getHeight()));paint.setStrokeCap(Paint.Cap.ROUND);
                Canvas c=new Canvas(mask);c.drawLine(lastX*mask.getWidth(),lastY*mask.getHeight(),x*mask.getWidth(),y*mask.getHeight(),paint);c.drawCircle(x*mask.getWidth(),y*mask.getHeight(),paint.getStrokeWidth()/2,paint);lastX=x;lastY=y;
            }
            invalidate();return true;
        }
        @Override public void close(){photo.recycle();mask.recycle();history.clear();}
    }
}
