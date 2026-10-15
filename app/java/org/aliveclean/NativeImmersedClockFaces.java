package org.aliveclean;

import android.content.Context;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import java.util.TimeZone;

/** Vendor faces for normal scenes, the installed ColorOS plugin for lyrics. */
final class NativeImmersedClockFaces implements NativeClockFaces {
    private final NativeClockFaces original;
    final NativeOfficialClockFaces immersed;
    private final Context host;
    private final float horizontalMinimumDigitHeight,verticalMinimumDigitHeight;
    private NativeAdaptiveClockFace adaptive;
    private boolean showing;
    NativeImmersedClockFaces(Context host,NativeClockFaces original,NativeClockGeometry geometry)throws Exception{
        this.host=host;this.original=original;
        horizontalMinimumDigitHeight=geometry.minimumDigitHeight(false);verticalMinimumDigitHeight=geometry.minimumDigitHeight(true);
        immersed=new NativeOfficialClockFaces(host,geometry);
    }
    public void attach(FrameLayout parent){
        original.attach(parent);View big=original.lock(false);int index=parent.indexOfChild(big);parent.removeView(big);
        adaptive=new NativeAdaptiveClockFace(host,big,original,horizontalMinimumDigitHeight,verticalMinimumDigitHeight);
        parent.addView(adaptive,index,new FrameLayout.LayoutParams(-1,-2));
        parent.addView(immersed,new FrameLayout.LayoutParams(-1,-2));
    }
    public void scene(int state,boolean compact){scene(state,compact,false);}
    void scene(int state,boolean compact,boolean lyrics){
        original.scene(state,compact);showing=lyrics&&(state==2||state==5);
        if(showing)original.active().setVisibility(View.GONE);
        adaptive.setVisibility(!showing&&original.active()==original.lock(false)?View.VISIBLE:View.GONE);
        immersed.setVisibility(showing?View.VISIBLE:View.GONE);
    }
    boolean showing(){return showing;}
    public View active(){return showing?immersed:original.active()==original.lock(false)?adaptive:original.active();}
    View materialTarget(){return showing?immersed:original.active();}
    public View lock(boolean compact){return compact?original.lock(true):adaptive;}
    boolean budget(int bottom,int top,int widgets){return adaptive.budget(bottom,top,widgets);}
    int naturalBigHeight(){return adaptive.naturalHeight();}
    int minimumBigHeight(){return adaptive.minimumHeight();}
    public void update(long time,TimeZone zone,boolean format24){
        original.update(time,zone,format24);Bundle b=new Bundle();b.putLong("time",time);immersed.command("setTime",b);
    }
    public void color(int value)throws Exception{original.color(value);}
    public void numberBounds(RectF out){
        if(showing)immersed.numberBounds(out);
        else {original.numberBounds(out);if(original.active()==original.lock(false))adaptive.mapContentBounds(out);}
    }
    public void close(){try{immersed.close();}finally{original.close();}}
}
