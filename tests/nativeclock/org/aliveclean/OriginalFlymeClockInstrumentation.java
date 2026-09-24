package org.aliveclean;

import android.app.Instrumentation;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import java.io.*;
import java.util.*;

/** Original EditorClockTextView and original ALIVE template, without altering system settings. */
public final class OriginalFlymeClockInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Throwable[] failure={null};
        runOnMainSync(()->{try{verify();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null){result.putString("stream",android.util.Log.getStackTraceString(failure[0]));finish(0,result);}
        else{result.putString("stream","ORIGINAL_FLYME_OK layouts=6 time_zone=true host_time=true date_separate=true\n");finish(-1,result);}
    }
    private void verify()throws Exception{
        Bitmap sheet=Bitmap.createBitmap(1440,2100,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(sheet);canvas.drawColor(Color.BLACK);
        Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setColor(Color.GRAY);label.setTextSize(26);
        TimeZone zone=TimeZone.getTimeZone("Asia/Shanghai");Calendar time=Calendar.getInstance(zone);
        time.clear();time.set(2026,8,22,18,30);
        for(int style=0;style<6;style++){
            try(OfficialClockFace face=new OfficialClockFace(getTargetContext(),false)){
                face.update(time.getTimeInMillis(),zone,true);face.layoutStyle(style);
                layout(face);
                RectF numbers=new RectF(),date=new RectF();face.numberBounds(numbers);face.dateBounds(date);
                require(!numbers.isEmpty()&&date.bottom<=numbers.top,"date overlaps digits, style="+style);
                require(numbers.left>=0&&numbers.right<=face.getWidth()&&numbers.bottom<=face.getHeight(),"digits clipped");
                require(face.getContentDescription().toString().startsWith("18:30"),"style reset host time");
                int save=canvas.save();canvas.translate((style%2)*720,(style/2)*700);
                canvas.drawText("Flyme original "+style,25,40,label);canvas.translate(0,70);canvas.scale(0.5f,0.5f);face.draw(canvas);canvas.restoreToCount(save);
                face.update(time.getTimeInMillis(),zone,false);
                require(face.getContentDescription().toString().startsWith("06:30"),"12h conversion");
                face.update(time.getTimeInMillis(),TimeZone.getTimeZone("UTC"),true);
                require(face.getContentDescription().toString().startsWith("10:30"),"timezone conversion");
            }
        }
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"flyme-originals.png"))){sheet.compress(Bitmap.CompressFormat.PNG,100,out);}
        sheet.recycle();
    }
    private static void layout(View face){face.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.AT_MOST));face.layout(0,0,face.getMeasuredWidth(),face.getMeasuredHeight());}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
