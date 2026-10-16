package org.aliveclean;
final class ColorOsBridge {
    static boolean selected=true;
    static int failures;
    static boolean ownsWallpaper(){return selected;}
    static void failure(String where,Throwable error){failures++;}
}
