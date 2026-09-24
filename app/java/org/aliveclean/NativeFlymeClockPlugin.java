package org.aliveclean;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import java.util.TimeZone;

/** Original Flyme controls and ALIVE templates behind the native ColorOS transport. */
final class NativeFlymeClockPlugin extends NativeOriginalClockPlugin {
    static final String ID="org.aliveclean.clock.flyme.alive.vertical";
    static final String HORIZONTAL_ID="org.aliveclean.clock.flyme.alive.horizontal";
    final FlymeAliveAodFace face,compactFace;
    final OfficialClockFace lockFace,compactLockFace;

    NativeFlymeClockPlugin(Context context)throws Exception{this(context,ID);}
    NativeFlymeClockPlugin(Context context,String id)throws Exception{this(context,id,new Faces(context,id));}
    private NativeFlymeClockPlugin(Context context,String id,Faces faces)throws Exception{
        super(context,id,faces,ID.equals(id));
        face=faces.face;compactFace=faces.compactFace;lockFace=faces.lockFace;compactLockFace=faces.compactLockFace;
    }
    private static final class Faces implements NativeClockFaces {
        final FlymeAliveAodFace face,compactFace;
        final OfficialClockFace lockFace,compactLockFace;
        private boolean aod=true,compact;
        Faces(Context context,String id)throws Exception{
            if(!ID.equals(id)&&!HORIZONTAL_ID.equals(id))throw new IllegalArgumentException("Unknown Flyme clock");
            face=new FlymeAliveAodFace(context,context.getAssets(),ID.equals(id)?"flyme.alive.hverticaltime":"flyme.alive.hhorizontaltime");
            compactFace=new FlymeAliveAodFace(context,context.getAssets(),"flyme.alive.hhorizontaltime");
            lockFace=new OfficialClockFace(context,false);lockFace.layoutStyle(ID.equals(id)?2:0);
            compactLockFace=new OfficialClockFace(context,false);compactLockFace.layoutStyle(0);compactLockFace.textSize(32f);
        }
        public void attach(FrameLayout parent){
            for(View view:new View[]{face,compactFace,lockFace,compactLockFace})parent.addView(view,new FrameLayout.LayoutParams(-1,-2));
        }
        public void scene(int state,boolean compact){
            this.compact=compact;aod=state==3||state==5;
            for(View view:new View[]{face,compactFace,lockFace,compactLockFace})view.setVisibility(view==active()?View.VISIBLE:View.GONE);
        }
        public View active(){return aod?(compact?compactFace:face):(compact?compactLockFace:lockFace);}
        public View lock(boolean compact){return compact?compactLockFace:lockFace;}
        public void update(long time,TimeZone zone,boolean format24){
            face.update(time,zone,format24);compactFace.update(time,zone,format24);
            lockFace.update(time,zone,format24);compactLockFace.update(time,zone,format24);
        }
        public void color(int color)throws Exception{face.color(color);compactFace.color(color);lockFace.color(color);compactLockFace.color(color);}
        public void numberBounds(RectF out){if(aod)((FlymeAliveAodFace)active()).numberBounds(out);else((OfficialClockFace)active()).numberBounds(out);}
        public void close(){lockFace.close();compactLockFace.close();}
    }
}
