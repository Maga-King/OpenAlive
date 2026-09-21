package org.aliveclean;

import android.opengl.Matrix;

/** Same inverse projection/view/model chain as the official lens effect; reusable storage. */
final class SceneMatrices {
    private final float width,height;
    private final float[] projection=new float[16],view=new float[16],translation=new float[16],
            origin=new float[16],scale=new float[16],rotation=new float[16],
            pivot=new float[16],unpivot=new float[16],result=new float[16],scratch=new float[16];
    SceneMatrices(int width,int height) {
        this.width=width;this.height=height;
        Matrix.orthoM(projection,0,-width/2f,width/2f,-height/2f,height/2f,1,3);inverse(projection);
        Matrix.setLookAtM(view,0,0,0,2,0,0,0,0,1,0);inverse(view);
    }
    private void inverse(float[] m){if(!Matrix.invertM(scratch,0,m,0))throw new IllegalStateException("Singular scene matrix");System.arraycopy(scratch,0,m,0,16);}
    private void append(float[] m){Matrix.multiplyMM(scratch,0,result,0,m,0);System.arraycopy(scratch,0,result,0,16);}
    private static void translate(float[] m,float x,float y){Matrix.setIdentityM(m,0);Matrix.translateM(m,0,x,y,0);}
    private static void scale(float[] m,float x,float y){Matrix.setIdentityM(m,0);Matrix.scaleM(m,0,x,y,1);}
    void write(float[] state) {
        int lens=SceneMotion.LENS;
        translate(translation,width*state[lens+5],height*state[lens+6]);inverse(translation);
        translate(origin,-width/2,-height/2);inverse(origin);
        scale(scale,width,height);inverse(scale);
        translate(pivot,0,-200);inverse(pivot);
        translate(unpivot,0,200);inverse(unpivot);
        Matrix.setRotateM(rotation,0,state[lens+9],0,0,1);inverse(rotation);
        Matrix.setIdentityM(result,0);
        append(scale);append(origin);append(unpivot);append(rotation);append(pivot);
        append(translation);append(view);append(projection);
        System.arraycopy(result,0,state,SceneMotion.PHOTO_MATRIX,16);

        float size=width*(state[lens+1]*2);
        translate(origin,-size/2,-size/2);inverse(origin);scale(scale,size,size);inverse(scale);
        Matrix.setRotateM(rotation,0,-state[lens+4],0,0,1);inverse(rotation);
        Matrix.setIdentityM(result,0);
        append(scale);append(origin);append(rotation);append(translation);append(view);append(projection);
        System.arraycopy(result,0,state,SceneMotion.DECOR_MATRIX,16);
    }
}
