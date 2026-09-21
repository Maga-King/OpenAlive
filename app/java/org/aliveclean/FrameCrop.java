package org.aliveclean;

/** A square in oriented source-image pixels, independent of preview resolution. */
final class FrameCrop {
    final float x,y,size,angle;
    FrameCrop(float x,float y,float size,float angle){this.x=x;this.y=y;this.size=size;this.angle=angle;}
    FrameCrop fit(int width,int height){
        float a=bound(angle,-45,45,0),r=(float)Math.toRadians(a);
        float extent=Math.abs((float)Math.cos(r))+Math.abs((float)Math.sin(r));
        float span=bound(size,.05f,1/extent,1/extent),shortSide=Math.min(width,height);
        float half=span*shortSide*extent/2;
        return new FrameCrop(bound(x,half/width,1-half/width,.5f),bound(y,half/height,1-half/height,.5f),span,a);
    }
    private static float bound(float n,float min,float max,float fallback){return Float.isNaN(n)||Float.isInfinite(n)?fallback:Math.max(min,Math.min(max,n));}
}
