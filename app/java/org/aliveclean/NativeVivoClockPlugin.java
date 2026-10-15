package org.aliveclean;
import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import java.util.TimeZone;

final class NativeVivoClockPlugin extends NativeOriginalClockPlugin {
    NativeVivoClockPlugin(Context host,String id)throws Exception{super(host,id,new Faces(host,id),false);}
    private static final class Faces implements NativeClockFaces {
        private final NativeVivoClockFace[] all;
        private NativeVivoClockFace active;
        Faces(Context host,String id)throws Exception{
            NativeVivoClockStyles.Style style=NativeVivoClockStyles.find(host,id);OfficialVivoClockUi source=new OfficialVivoClockUi(host);
            all=new NativeVivoClockFace[]{new NativeVivoClockFace(host,source,style,false,false),new NativeVivoClockFace(host,source,style,false,true),new NativeVivoClockFace(host,source,style,true,false),new NativeVivoClockFace(host,source,style,true,true)};active=all[2];
        }
        public void attach(FrameLayout parent){for(View face:all)parent.addView(face,new FrameLayout.LayoutParams(-1,-2));}
        public void scene(int state,boolean compact){active=all[(state==3||state==5?2:0)+(compact?1:0)];for(View face:all)face.setVisibility(face==active?View.VISIBLE:View.GONE);}
        public View active(){return active;}
        public View lock(boolean compact){return all[compact?1:0];}
        public void update(long time,TimeZone zone,boolean format24){for(NativeVivoClockFace face:all)face.update(time,zone,format24);}
        public void color(int color){for(NativeVivoClockFace face:all)face.color(color);}
        public void numberBounds(RectF out){active.numberBounds(out);}
        public float lockDigitHeight(){return all[0].digitHeight();}
        public boolean lockDigitsStacked(){return all[0].digitsStacked();}
        public void close(){}
    }
}
