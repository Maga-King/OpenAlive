package org.aliveclean;

import android.graphics.*;
import android.opengl.*;
import android.view.Surface;

/** Match bitmap primaries to the EGL surface; GL uploads do not carry ICC profiles. */
final class VivoColorSpace {
    // Vivo's shaders output already encoded RGB. Plain DISPLAY_P3 applies the
    // framebuffer transfer function again; PASSTHROUGH only tags the primaries.
    private static final int EGL_GL_COLORSPACE_KHR=0x309D,EGL_GL_COLORSPACE_DISPLAY_P3_PASSTHROUGH_EXT=0x3490;
    static boolean supportsDisplayP3(EGLDisplay display){
        String extensions=EGL14.eglQueryString(display,EGL14.EGL_EXTENSIONS);
        return extensions!=null&&(" "+extensions+" ").contains(" EGL_EXT_gl_colorspace_display_p3_passthrough ");
    }
    static EGLSurface window(EGLDisplay display,EGLConfig config,Surface surface){
        if(supportsDisplayP3(display)){
            EGLSurface wide=EGL14.eglCreateWindowSurface(display,config,surface,new int[]{EGL_GL_COLORSPACE_KHR,EGL_GL_COLORSPACE_DISPLAY_P3_PASSTHROUGH_EXT,EGL14.EGL_NONE},0);
            if(wide!=EGL14.EGL_NO_SURFACE)return wide;
            EGL14.eglGetError(); // Unsupported native-window format: retry the portable path.
        }
        return EGL14.eglCreateWindowSurface(display,config,surface,new int[]{EGL14.EGL_NONE},0);
    }
    static boolean isDisplayP3(EGLDisplay display,EGLSurface surface){
        if(!supportsDisplayP3(display))return false;
        int[] value={0};
        if(!EGL14.eglQuerySurface(display,surface,EGL_GL_COLORSPACE_KHR,value,0)){EGL14.eglGetError();return false;}
        return value[0]==EGL_GL_COLORSPACE_DISPLAY_P3_PASSTHROUGH_EXT;
    }
    static Bitmap convert(Bitmap source,boolean displayP3){
        ColorSpace target=ColorSpace.get(displayP3?ColorSpace.Named.DISPLAY_P3:ColorSpace.Named.SRGB);
        if(target.equals(source.getColorSpace())&&source.getConfig()==Bitmap.Config.ARGB_8888)return source;
        Bitmap converted=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888,source.hasAlpha(),target);
        new Canvas(converted).drawBitmap(source,0,0,null);
        return converted;
    }
    private VivoColorSpace(){}
}
