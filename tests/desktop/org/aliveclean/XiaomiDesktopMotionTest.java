package org.aliveclean;

public final class XiaomiDesktopMotionTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        int count=0;
        for(int adjustment=-100;adjustment<=100;adjustment++){
            float previous=-1;
            for(int input=0;input<=100;input++){
                float output=XiaomiDesktopMotion.offset(input,adjustment);
                check(output>=0&&output<=100,"native range");
                check(output>=previous,"monotonic input");previous=output;
                check(Math.abs(output+XiaomiDesktopMotion.offset(100-input,adjustment)-100)<.001f,"centered symmetry");
                if(adjustment==0)check(output==input,"zero preserves original samples");
                if(adjustment==-100)check(output==50,"minimum stops desktop motion");
                count++;
            }
            check(XiaomiDesktopMotion.offset(50,adjustment)==50,"center fixed");
            check(XiaomiDesktopMotion.delay(adjustment)==100-adjustment,"response mapping");
        }
        check(XiaomiDesktopMotion.offset(60,100)==70,"double gain");
        check(Math.abs(XiaomiDesktopMotion.offset(62.5f,50)-68.75f)<.0001f,"fractional launcher input is preserved");
        check(XiaomiDesktopMotion.offset(25,100)==0&&XiaomiDesktopMotion.offset(75,100)==100,"double gain reaches native boundaries at quarter pages");
        check(XiaomiDesktopMotion.offset(0,100)==0&&XiaomiDesktopMotion.offset(100,100)==100,"native camera bounds are never exceeded");
        check(XiaomiDesktopMotion.offset(0,-50)==25&&XiaomiDesktopMotion.offset(100,-50)==75,"half gain uses the centered half of original range");
        check(XiaomiDesktopMotion.offset(Float.NaN,0)==50,"invalid launcher input");
        check(XiaomiDesktopMotion.offset(Float.POSITIVE_INFINITY,100)==50&&XiaomiDesktopMotion.offset(Float.NEGATIVE_INFINITY,-100)==50,"invalid infinities return center");
        check(XiaomiDesktopMotion.delay(-1000)==200&&XiaomiDesktopMotion.delay(1000)==0,"corrupt settings clamp");
        System.out.println("Desktop motion passed: "+count+" offset combinations");
    }
}
