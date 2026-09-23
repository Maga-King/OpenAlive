package org.aliveclean;

import android.graphics.Matrix;
import android.view.View;

/** Convert the occupied screen-space floor to the notification stack's local Y. */
final class AodStackSpace {
    private final Matrix transform=new Matrix(),inverse=new Matrix();
    private final float[] point=new float[2];
    int padding(View stack,int screenBottom){
        if(stack==null||screenBottom<=0||stack.getWidth()<=0)return 0;
        transform.reset();stack.transformMatrixToGlobal(transform);
        if(!transform.invert(inverse))return 0;
        point[0]=stack.getWidth()*.5f;point[1]=0;transform.mapPoints(point);
        point[1]=screenBottom;inverse.mapPoints(point);
        return Float.isFinite(point[1])?Math.max(0,(int)Math.ceil(point[1])):0;
    }
}
