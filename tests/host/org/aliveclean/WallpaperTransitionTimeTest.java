package org.aliveclean;

public final class WallpaperTransitionTimeTest {
    private static void equal(float actual,float expected){
        if(actual!=expected)throw new AssertionError("Expected "+expected+" ms, got "+actual);
    }
    public static void main(String[] args){
        WallpaperTransitionTime time=new WallpaperTransitionTime();
        equal(time.advance(0),0);
        equal(time.advance(200_000_000L),200);
        // App/shade hides the wallpaper mid-collapse, then AOD displays it again.
        time.pause();
        equal(time.advance(10_000_000_000L),200);
        equal(time.advance(10_100_000_000L),300);
        // Fingerprint wake can reverse a collapse while still hidden.
        time.pause();time.restart();
        equal(time.advance(20_000_000_000L),0);
        equal(time.advance(20_150_000_000L),150);
        // Repeated visibility callbacks must not reset completed play time.
        time.pause();time.pause();
        equal(time.advance(30_000_000_000L),150);
        equal(time.advance(31_000_000_000L),1150);
        System.out.println("Wallpaper visible transition time checks passed");
    }
}
