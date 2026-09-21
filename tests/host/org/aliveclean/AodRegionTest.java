package org.aliveclean;

public final class AodRegionTest {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
    private static void close(float expected,float actual){check(Math.abs(expected-actual)<0.000001f);}
    public static void main(String[] args){
        // Independently specified screen positions; includes the reference default rectangle.
        AodRegion original=AodRegion.parse(0,1080,2400,"324|504|756|936");
        close(0,original.sceneX());close(.2f,original.sceneY());
        AodRegion center=AodRegion.create(0,1440,3168,420,1284,1020,1884);
        close(0,center.sceneX());close(0,center.sceneY());
        AodRegion shifted=AodRegion.create(0,1440,3168,433,1301,1033,1901);
        close(13f/1440,shifted.sceneX());close(-17f/3168,shifted.sceneY());
        // Position must not be offset a second time when the source already includes burn-in.
        close(733,1440*(shifted.sceneX()+.5f));close(1601,3168*(.5f-shifted.sceneY()));
        AodRegion odd=AodRegion.create(0,1080,2400,325,505,756,936);
        close(0,odd.sceneX());close(.2f,odd.sceneY());
        check(original.fits(0,720,1600));check(!original.fits(1,1080,2400));
        check(!original.fits(0,2400,1080));check(!original.fits(0,1080,0));
        for(String bad:new String[]{null,"","1|2|3","1|2|3|4|5","a|2|3|4","4|2|3|4","1|4|3|2","-1|0|3|4","0|0|1081|2400","0|0|1080|2401","0|0|2147483647|2400"})check(AodRegion.parse(0,1080,2400,bad)==null);
        check(AodRegion.create(-1,1080,2400,1,2,3,4)==null);
        check(AodRegion.create(0,0,2400,1,2,3,4)==null);
        System.out.println("AOD geometry checks passed: "+checks);
    }
}
