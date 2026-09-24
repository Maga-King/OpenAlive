package org.aliveclean;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import java.util.TimeZone;

/** Original HyperOS renderers plugged into the same ColorOS factory contract. */
final class NativeHyperOsClockPlugin extends NativeOriginalClockPlugin {
    static final String VERTICAL_ID="org.aliveclean.clock.hyperos.classic.vertical";
    static final String HORIZONTAL_ID="org.aliveclean.clock.hyperos.classic.horizontal";
    static boolean contains(String id){return NativeHyperOsStyles.find(id)!=null;}
    NativeHyperOsClockPlugin(Context host,String id)throws Exception{
        super(host,id,new Faces(host,id),NativeHyperOsStyles.find(id).vertical);
    }
    private static final class Faces implements NativeClockFaces{
        private final NativeHyperOsFace fullLock,smallLock,aod,smallAod;
        private NativeHyperOsFace active;
        Faces(Context host,String id)throws Exception{
            if(!contains(id))throw new IllegalArgumentException("Unknown HyperOS clock");
            OfficialHyperOsUi runtime=OfficialHyperOsUi.open(host);
            NativeHyperOsStyles.Style style=NativeHyperOsStyles.find(id);
            fullLock=new NativeHyperOsFace(runtime,style,false,false);
            smallLock=new NativeHyperOsFace(runtime,style,false,true);
            aod=new NativeHyperOsFace(runtime,style,true,false);
            smallAod=new NativeHyperOsFace(runtime,style,true,true);
            active=aod;
        }
        public void attach(FrameLayout parent){
            for(View face:new View[]{fullLock,smallLock,aod,smallAod})parent.addView(face,new FrameLayout.LayoutParams(-1,-2));
        }
        public void scene(int state,boolean compact){
            active=state==3||state==5?(compact?smallAod:aod):(compact?smallLock:fullLock);
            for(View face:new View[]{fullLock,smallLock,aod,smallAod})face.setVisibility(face==active?View.VISIBLE:View.GONE);
        }
        public View active(){return active;}
        public View lock(boolean compact){return compact?smallLock:fullLock;}
        public void update(long time,TimeZone zone,boolean format24){
            // Original HyperOS updateTime reads wall-clock time. The native host's
            // minute event is the trigger; no separate alarm/receiver is started.
            try{for(NativeHyperOsFace face:new NativeHyperOsFace[]{fullLock,smallLock,aod,smallAod})face.refresh(zone.getID(),format24);}
            catch(Exception failure){throw new IllegalStateException("Original HyperOS minute update",failure);}
        }
        public void color(int value)throws Exception{
            for(NativeHyperOsFace face:new NativeHyperOsFace[]{fullLock,smallLock,aod,smallAod})face.color(value);
        }
        public void numberBounds(RectF out){active.numberBounds(out);}
        public void close(){for(NativeHyperOsFace face:new NativeHyperOsFace[]{fullLock,smallLock,aod,smallAod})face.close();}
    }
}
