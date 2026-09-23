package org.aliveclean;

import android.opengl.*;

/** Cosmic alone needs multisampling for the original mesh silhouette. */
final class CosmicEgl {
    static EGLConfig choose(EGLDisplay display,int surfaceType){
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        // Match the official minimum (2 samples); drivers may offer 4 instead.
        // Fall back only when multisampled window/pbuffer configs are unavailable.
        for(int samples:new int[]{2,0}){
            int[] attrs={EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,surfaceType,
                EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,16,
                EGL14.EGL_SAMPLE_BUFFERS,samples==0?0:1,EGL14.EGL_SAMPLES,samples,EGL14.EGL_NONE};
            if(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)&&count[0]>0)return configs[0];
        }
        throw new IllegalStateException("No Cosmic GLES3 EGL config");
    }
}
