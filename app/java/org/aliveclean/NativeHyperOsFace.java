package org.aliveclean;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Original clock layers, with only their outer screen whitespace removed. */
final class NativeHyperOsFace extends FrameLayout implements AutoCloseable {
    private final OfficialHyperOsClockFace original;
    private final List<View> content=new ArrayList<>();
    private final List<View> numbers=new ArrayList<>();
    private int nativeHeight,cropTop;
    private final boolean fitArtwork;
    private float artworkScale=1,artworkX;

    @SuppressWarnings({"unchecked","rawtypes"})
    NativeHyperOsFace(OfficialHyperOsUi source,NativeHyperOsStyles.Style style,boolean aod,boolean compact)throws Exception{
        super(source);
        fitArtwork=style.template.equals("doodle");
        original=new OfficialHyperOsClockFace(source,style.template,style.variant,style.font,style.weight);
        original.scene(aod,compact,false);
        addView(original,new LayoutParams(-1,-1));
        Class<?> role=source.getClassLoader().loadClass("com.miui.clock.module.ClockViewType");
        for(int layer=0;layer<original.getChildCount();layer++){
            View face=original.getChildAt(layer);
            for(String name:new String[]{"HOUR1","HOUR2","MIN1","MIN2","COLON1","COLON2","FULL_HOUR","FULL_MINUTE","FULL_COLON","FULL_TIME","TIME_AREA","TIME_AREA2",
                    "TEXT_AREA","TEXT_AREA2","TEXT_AREA3","SIGNATURE_AREA","FULL_DATE","FULL_WEEK","FULL_YEAR","FULL_DATE_WEEK","NOTIFICATION_DATE","DATE","WEEK"}){
                View child=(View)face.getClass().getMethod("getIClockView",role).invoke(face,Enum.valueOf((Class)role,name));
                if(child==null||child==face)continue;
                collectLeaves(child,content);
                if(name.startsWith("HOUR")||name.startsWith("MIN")||name.startsWith("COLON")||name.equals("FULL_HOUR")||name.equals("FULL_MINUTE")||name.equals("FULL_COLON")||name.equals("FULL_TIME")||name.startsWith("TIME_AREA"))collectLeaves(child,numbers);
            }
            if(compact){
                // Some original notification renderers expose FULL_TIME;
                // Eastern B/C expose only their original XML IDs.
                View time=(View)face.getClass().getMethod("getIClockView",role).invoke(face,Enum.valueOf((Class)role,"FULL_TIME"));
                if(time!=null&&time!=face){collectLeaves(time,content);collectLeaves(time,numbers);}
                for(String name:new String[]{"time_hour","time_minute","time_hour_minute","date_and_week"}){
                    int id=getResources().getIdentifier(name,"id","com.miui.aod");View child=id==0?null:face.findViewById(id);
                    if(child!=null){collectLeaves(child,content);if(name.startsWith("time_"))collectLeaves(child,numbers);}
                }
            }
            if(style.template.equals("doodle")){
                for(String name:new String[]{"time_hour","time_minute","time_dot","data_day","data_month","data_image","week_today","week_todayis"}){
                    int id=getResources().getIdentifier(name,"id","com.miui.aod");View child=id==0?null:face.findViewById(id);
                    if(child!=null){collectLeaves(child,content);if(name.startsWith("time_"))collectLeaves(child,numbers);}
                }
            }
        }
        if(numbers.isEmpty())throw new IllegalStateException("Original clock has no numeric controls: "+style.id);
        if(fitArtwork){
            // The English sticker intentionally overhangs its original layout.
            // Keep that artwork intact inside our fitted outer face.
            for(View child:content)for(ViewParent p=child.getParent();p instanceof android.view.ViewGroup&&p!=this;p=p.getParent()){
                ((android.view.ViewGroup)p).setClipChildren(false);((android.view.ViewGroup)p).setClipToPadding(false);
            }
        }
    }
    private static void collectLeaves(View view,List<View> target){
        if(view instanceof TextView||view instanceof android.widget.ImageView||view.getClass().getName().equals("com.miui.clock.allInOne.TimeView")||view.getClass().getName().equals("com.miui.clock.MiuiClockNumberView")){
            if(!target.contains(view))target.add(view);
        }else if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)collectLeaves(group.getChildAt(i),target);
        }
    }
    void refresh(String zone,boolean format24)throws Exception{original.refresh(zone,format24);}
    void color(int value)throws Exception{original.color(value);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        nativeHeight=getResources().getDisplayMetrics().heightPixels;
        original.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(nativeHeight,MeasureSpec.EXACTLY));
        original.layout(0,0,width,nativeHeight);
        RectF body=new RectF();
        for(View view:content)if(visible(view,original)){
            RectF box=view instanceof TextView?new RectF(0,0,view.getWidth(),view.getHeight()):ink(view);
            if(box.isEmpty())continue;map(view,original,box);body.union(box);
        }
        cropTop=body.isEmpty()?0:(int)Math.floor(body.top);
        artworkScale=1;artworkX=0;
        if(fitArtwork&&!body.isEmpty()&&(body.left<0||body.right>width)){
            float margin=getResources().getDisplayMetrics().density*2;
            artworkScale=Math.min(1,(width-2*margin)/body.width());
            artworkX=Math.max(margin-body.left*artworkScale,Math.min(0,width-margin-body.right*artworkScale));
        }
        int height=body.isEmpty()?0:(int)Math.ceil((body.bottom-cropTop)*artworkScale);
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        original.layout(0,0,r-l,nativeHeight);
        original.setPivotX(0);original.setPivotY(0);
        original.setScaleX(artworkScale);original.setScaleY(artworkScale);
        original.setTranslationX(artworkX);original.setTranslationY(-cropTop*artworkScale);
    }
    void numberBounds(RectF out){
        out.setEmpty();
        for(View view:numbers){
            if(!visible(view,this))continue;
            RectF box=ink(view);if(!box.isEmpty()){map(view,this,box);out.union(box);}
        }
    }
    void contentBounds(RectF out){
        out.setEmpty();
        for(View view:content){
            if(!visible(view,this))continue;
            RectF box=ink(view);if(!box.isEmpty()){map(view,this,box);out.union(box);}
        }
    }
    private static RectF ink(View view){
        RectF result=new RectF();
        if(view instanceof TextView){
            TextView textView=(TextView)view;
            Layout layout=textView.getLayout();if(layout==null)return result;
            String text=textView.getText().toString();
            for(int line=0;line<layout.getLineCount();line++){
                int start=layout.getLineStart(line),end=layout.getLineEnd(line);
                if(end<=start)continue;
                Path path=new Path();
                float x=textView.getTotalPaddingLeft()+layout.getLineLeft(line)-view.getScrollX();
                float y=textView.getTotalPaddingTop()+layout.getLineBaseline(line)-view.getScrollY();
                textView.getPaint().getTextPath(text,start,end,x,y,path);
                RectF ink=new RectF();path.computeBounds(ink,true);
                if(!ink.isEmpty())result.union(ink);
            }
        }else if(view instanceof android.widget.ImageView){
            android.widget.ImageView image=(android.widget.ImageView)view;
            android.graphics.drawable.Drawable drawable=image.getDrawable();
            if(drawable==null)return result;
            result.set(drawable.getBounds());image.getImageMatrix().mapRect(result);
            result.offset(image.getPaddingLeft(),image.getPaddingTop());
        }else if(view.getClass().getName().equals("com.miui.clock.MiuiClockNumberView")){
            try{
                Class<?> type=view.getClass();
                float width=type.getField("mOriginImageWidth").getInt(view),height=type.getField("mOriginImageHeight").getInt(view);
                if(type.getField("mVectorDrawable").get(view)==null)return result;
                if(!"NONE".equals(type.getField("mDiffusionType").get(view).toString()))throw new IllegalStateException("Unsupported original diffusion geometry");
                result.set(type.getField("mOriginLeftEmpty").getInt(view),0,width-type.getField("mOriginRightEmpty").getInt(view),height);
                float angle=type.getField(type.getField("mHasUserDefined").getBoolean(view)?"mUserDefineAngel":"mOriginAngel").getFloat(view);
                float scale=type.getField("mScale").getFloat(view);
                Matrix draw=new Matrix();draw.setRotate(angle,width/2,height/2);draw.postScale(scale,scale);
                draw.postTranslate(type.getField("mTranslateX").getFloat(view),type.getField("mTranslateY").getFloat(view));draw.mapRect(result);
            }catch(ReflectiveOperationException error){throw new IllegalStateException("Original vector bounds unavailable",error);}
        }else{
            try{
                // Original TimeView computes its variable-font path in onDraw,
                // not onMeasure. The first AOD layout otherwise sees an empty
                // path, crops to the date alone and cannot animate the digits.
                // Run that original preparation before reading its own bounds.
                if(view.getClass().getName().equals("com.miui.clock.allInOne.TimeView"))
                    view.getClass().getMethod("handleParams").invoke(view);
                result.set((RectF)view.getClass().getMethod("getTextBoundsWithPosition").invoke(view));
            }
            catch(ReflectiveOperationException error){throw new IllegalStateException("Original time bounds unavailable",error);}
        }
        return result;
    }
    private static boolean visible(View child,View ancestor){
        for(View view=child;view!=ancestor;){
            if(view.getVisibility()!=View.VISIBLE||view.getAlpha()==0)return false;
            ViewParent parent=view.getParent();if(!(parent instanceof View))return false;view=(View)parent;
        }
        return true;
    }
    private static void map(View child,View ancestor,RectF box){
        Matrix local=new Matrix();child.transformMatrixToGlobal(local);
        Matrix parent=new Matrix(),inverse=new Matrix();ancestor.transformMatrixToGlobal(parent);
        if(!parent.invert(inverse))throw new IllegalStateException("Original clock transform is singular");
        local.postConcat(inverse);local.mapRect(box);
    }
    @Override public void close(){original.close();removeAllViews();content.clear();numbers.clear();}
}
