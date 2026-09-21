package org.aliveclean;

import android.view.animation.*;

/** ROM photo-effect parameters; each channel retains its own timing and curve. */
final class PhotoStyle {
    final int id,size,frames,fps,endMicros;
    final String folder;
    final float rotation,blur,scale,content;
    final long[] durations;
    private final Track[][] tracks=new Track[4][8];
    static final class Track {
        final long duration,delay;final Interpolator curve;
        Track(long duration,long delay,float x1,float y1,float x2,float y2){this.duration=duration;this.delay=delay;curve=new PathInterpolator(x1,y1,x2,y2);}
    }
    private PhotoStyle(int id,String folder,int size,int frames,int fps,int end,float rotation,float blur,float scale,float content,long... durations){
        this.id=id;this.folder=folder;this.size=size;this.frames=frames;this.fps=fps;endMicros=end;
        this.rotation=rotation;this.blur=blur;this.scale=scale;this.content=content;this.durations=durations;
    }
    private void set(int transition,Track track,int... slots){for(int i:slots)tracks[transition][i]=track;}
    Track track(int transition,int slot){return transition<4?tracks[transition][slot]:null;}
    int index(long micros){return micros<=0?frames:micros>=endMicros?frames+1:Math.min(frames-1,Math.round(micros*fps/1000000f));}
    private static Track t(long duration,long delay,float x1,float y1,float x2,float y2){return new Track(duration,delay,x1,y1,x2,y2);}
    static boolean supported(int id){return id>=1&&id<=5;}
    static PhotoStyle get(int id){
        PhotoStyle s;
        switch(id){
            case 1:s=new PhotoStyle(1,"leave_light",720,61,120,483667,-5,0,.70486f,1,567,530,567,530,800,400);break;
            case 2:s=new PhotoStyle(2,"moon",720,32,60,516667,0,22,.986f,2.34f,533,743,553,743,800,400);break;
            case 3:s=new PhotoStyle(3,"door",720,41,60,616667,0,100,.917f,2.34f,717,733,717,733,800,400);break;
            case 4:s=new PhotoStyle(4,"mountain",1080,43,60,716667,0,100,.833f,2.34f,750,684,750,684,800,400);break;
            case 5:s=new PhotoStyle(5,"galaxy",1080,56,60,916667,0,100,.833f,2.34f,950,800,950,800,800,400);break;
            default:throw new IllegalArgumentException("Photo style "+id);
        }
        for(int tr=0;tr<4;tr++){
            boolean open=tr==0||tr==2;
            switch(id){
                case 1:
                    s.set(tr,t(open?517:450,open?50:0,.28f,0,.33f,1),2,4,5);
                    s.set(tr,open?t(167,0,.33f,0,.67f,1):t(450,0,.28f,0,.33f,1),1);break;
                case 2:
                    s.set(tr,t(open?367:467,open?0:176,.32f,.01f,.1f,1),4,5);
                    s.set(tr,open?t(367,0,.32f,.03f,.1f,.99f):t(567,176,.32f,-.04f,.1f,1.01f),2);
                    s.set(tr,open?t(tr==0?533:553,0,.37f,.01f,.34f,1):t(600,0,.33f,0,.67f,1),0,3);break;
                case 3:
                    s.set(tr,open?t(250,133,.06f,.12f,.25f,1):t(433,200,.25f,0,.25f,1),4,5);
                    s.set(tr,open?t(267,133,.06f,.12f,.25f,1):t(533,200,.25f,0,.25f,1),2);
                    s.set(tr,open?t(717,0,.17f,0,.25f,1):t(667,0,.33f,0,.67f,1),3);
                    s.set(tr,open?t(717,0,.25f,0,.25f,1):t(667,0,.33f,0,.67f,1),0);break;
                case 4:
                    s.set(tr,open?t(483,0,.25f,0,.25f,1):t(433,217,.14f,0,.33f,.95f),4,5);
                    s.set(tr,open?t(483,0,.25f,0,.25f,1):t(467,217,.14f,0,.67f,1),2);
                    s.set(tr,t(open?750:667,0,.25f,0,.25f,1),0,3);break;
                case 5:
                    s.set(tr,open?t(450,383,.25f,0,.25f,1):t(433,267,.14f,0,.1f,1),4,5);
                    s.set(tr,open?t(517,317,.25f,0,.25f,1):t(533,267,.14f,0,.1f,1),2);
                    s.set(tr,t(600,open?350:0,.25f,0,.25f,1),0,3);break;
            }
        }
        return s;
    }
}
