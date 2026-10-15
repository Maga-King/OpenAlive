package org.aliveclean;

/** A retained wallpaper buffer is sufficient only below a proven opaque music host. */
final class ImmersiveBackdropPolicy {
    static boolean covered(boolean selected,boolean music,boolean entered,boolean lockScene,
            boolean hostVisible,boolean opaque,boolean fullSize,boolean validSurface,
            boolean crossfading,float viewAlpha,float surfaceAlpha){
        return selected&&music&&entered&&lockScene&&hostVisible&&opaque&&fullSize&&validSurface
                &&!crossfading&&viewAlpha==1f&&surfaceAlpha==1f;
    }
}
