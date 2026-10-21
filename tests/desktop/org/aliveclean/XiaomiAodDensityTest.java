package org.aliveclean;

public final class XiaomiAodDensityTest {
    static int checks;
    static void equal(int actual,int expected){checks++;if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
    static void near(double actual,double expected,double tolerance){checks++;if(Math.abs(actual-expected)>tolerance)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        equal(XiaomiAodDensity.correctTarget(330,640,480),440);
        equal(XiaomiAodDensity.correctTarget(440,480,480),440);
        equal(XiaomiAodDensity.correctTarget(586,640,640),586);
        // Display-size preference still scales the final result, independently
        // of native-vs-logical resolution: 528 instead of 480 is +10 percent.
        equal(XiaomiAodDensity.correctTarget(363,640,480),484);
        // Unknown baseline is not evidence for an extra resolution multiplier.
        equal(XiaomiAodDensity.correctTarget(440,480,0),440);
        equal(XiaomiAodDensity.correctTarget(440,0,480),440);
        equal(XiaomiAodDensity.correctTarget(330,640,0),330);
        // Phone matrix uses current-resolution defaults, never a model name.
        for(int width:new int[]{720,1080,1220,1440})for(double aspect:new double[]{16./9,20./9,2.2}){
            int height=(int)Math.round(width*aspect),baseline=(int)Math.round(width*4./9);
            float ppi=width/2.7f;
            int dpi=XiaomiAodDensity.phoneDensity(width,height,ppi,ppi,baseline,baseline);
            near((double)dpi/width,1.1398963928222656/2.8,.001);
            equal(XiaomiAodDensity.phoneDensity(height,width,ppi,ppi,baseline,baseline),dpi);
            int large=XiaomiAodDensity.phoneDensity(width,height,ppi,ppi,(int)Math.round(baseline*1.1),baseline);
            near((double)large/dpi,1.1,.004);
        }
        // Real host metrics, including unequal horizontal/vertical physical DPI.
        equal(XiaomiAodDensity.phoneDensity(1080,2376,386.36618f,381.96454f,480,480),440);
        int nativeDpi=XiaomiAodDensity.phoneDensity(1440,3168,386.36618f/0.75f,381.96454f/0.75f,640,640);
        near((double)nativeDpi/1440,440./1080,.001);
        // Malformed vendor metrics must not produce a zero-sized clock or NaN.
        equal(XiaomiAodDensity.phoneDensity(1080,2400,Float.NaN,400,480,480),480);
        equal(XiaomiAodDensity.phoneDensity(1080,2400,400,Float.POSITIVE_INFINITY,480,480),480);
        equal(XiaomiAodDensity.phoneDensity(0,2400,400,400,480,480),480);
        equal(XiaomiAodDensity.phoneDensity(1080,2400,0,0,0,0),160);
        equal(XiaomiAodDensity.phoneDensity(1080,2376,386.36618f,381.96454f,480,0),440);
        equal(XiaomiAodDensity.phoneDensity(1080,2400,400,400,480,1),480);
        equal(XiaomiAodDensity.phoneDensity(1080,2400,400,400,Integer.MAX_VALUE,480),160);
        System.out.println("XIAOMI_AOD_DENSITY_OK checks="+checks);
    }
}
