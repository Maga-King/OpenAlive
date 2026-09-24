package org.aliveclean;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import java.util.TimeZone;

/** Independent Flyme artistic clocks using the same native host transport. */
final class NativeFlymeArtworkPlugin extends NativeOriginalClockPlugin {
    static final String PERSPECTIVE="org.aliveclean.clock.flyme.distinctive_self";
    static boolean contains(String id){return PERSPECTIVE.equals(id);}
    NativeFlymeArtworkPlugin(Context context,String id)throws Exception{super(context,id,new Faces(context,id),false);}
    private static final class Faces implements NativeClockFaces {
        private final NativeFlymePerspectiveFace[] faces=new NativeFlymePerspectiveFace[4];
        private int selected;
        Faces(Context context,String id)throws Exception{
            if(!contains(id))throw new IllegalArgumentException("Unknown Flyme artwork clock");
            for(int i=0;i<faces.length;i++)faces[i]=new NativeFlymePerspectiveFace(context,i>=2,(i&1)!=0);
        }
        public void attach(FrameLayout parent){for(View face:faces)parent.addView(face,new FrameLayout.LayoutParams(-1,-2));}
        public void scene(int state,boolean compact){
            selected=((state==3||state==5)?2:0)+(compact?1:0);
            for(int i=0;i<faces.length;i++)faces[i].setVisibility(i==selected?View.VISIBLE:View.GONE);
        }
        public View active(){return faces[selected];}
        public View lock(boolean compact){return faces[compact?1:0];}
        public void update(long time,TimeZone zone,boolean format24){for(NativeFlymePerspectiveFace face:faces)face.update(time,zone,format24);}
        public void color(int value){for(NativeFlymePerspectiveFace face:faces)face.color(value);}
        public void numberBounds(RectF out){faces[selected].numberBounds(out);}
        public void close(){for(NativeFlymePerspectiveFace face:faces)face.close();}
    }
}
