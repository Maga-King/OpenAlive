package org.aliveclean;

/** Normalized launcher input: zero adjustments retain Xiaomi's original motion. */
final class XiaomiDesktopMotion {
    static int clamp(int value){return Math.max(-100,Math.min(100,value));}
    static float offset(float value,int amplitude){
        if(!Float.isFinite(value))return 50f;
        return Math.max(0f,Math.min(100f,50f+(value-50f)*(1f+clamp(amplitude)/100f)));
    }
    // Xiaomi delays launcher samples by 100 ms. Keep every sample, only change
    // its delivery time: -100 = 200 ms, 0 = original, +100 = immediate.
    static long delay(int responsiveness){return 100L-clamp(responsiveness);}
}
