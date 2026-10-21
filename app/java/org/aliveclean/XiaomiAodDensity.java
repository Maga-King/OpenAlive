package org.aliveclean;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/** Xiaomi's AOD auto-density policy, without its global resource hooks. */
final class XiaomiAodDensity {
    static int resolve(Context host,ClassLoader loader){
        Display display=((WindowManager)host.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        DisplayMetrics m=new DisplayMetrics();display.getRealMetrics(m);
        int platformBaseline=platformDefaultDpi(display);
        try{
            Object config=loader.loadClass("miuix.autodensity.DisplayDensityConfig")
                .getConstructor(Context.class,Display.class).newInstance(host,display);
            Object target=config.getClass().getMethod("getTargetConfig").invoke(config);
            int dpi=target.getClass().getField("densityDpi").getInt(target);
            // On MIUI, keep its own device/resolution/zoom policy. Only replace
            // the denominator when the host supplies a current default DPI.
            // The largest supported mode does NOT prove which resolution
            // DENSITY_DEVICE_STABLE belongs to on an arbitrary vendor device.
            if(platformBaseline>0){
                java.lang.reflect.Field base=config.getClass().getDeclaredField("mCurrentDefaultDpi");base.setAccessible(true);
                dpi=correctTarget(dpi,base.getInt(config),platformBaseline);
            }
            if(dpi>=120&&dpi<=1000)return dpi;
        }catch(Exception|LinkageError unavailable){
            android.util.Log.w("OpenAliveXiaomi","Original auto-density unavailable; using phone policy",unavailable);
        }
        // DisplayDensityConfig.updatePPIOfDevice + AutoDensityPolicy.calcPhoneScale.
        // MIUI-only device/SKU APIs may be absent on ColorOS. Preserve the public
        // phone calculation rather than applying ColorOS's larger logical DPI.
        int baseline=platformBaseline>0?platformBaseline:
                display.getDisplayId()==Display.DEFAULT_DISPLAY?DisplayMetrics.DENSITY_DEVICE_STABLE:m.densityDpi;
        return phoneDensity(m.widthPixels,m.heightPixels,m.xdpi,m.ydpi,m.densityDpi,baseline);
    }
    private static int platformDefaultDpi(Display display){
        // ColorOS's own KStyleUtil.getInitialDisplayDensity uses this property
        // before WindowManager's initial density. Reuse that source instead of
        // assuming ro.sf.lcd_density belongs to the largest supported mode.
        // Never use a default-display property for a foldable's secondary screen.
        if(display.getDisplayId()!=Display.DEFAULT_DISPLAY)return 0;
        try{
            Class<?> properties=Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get=properties.getMethod("get",String.class,String.class);
            String oplus=(String)get.invoke(null,"ro.build.version.oplusrom","");
            String oppo=(String)get.invoke(null,"ro.build.version.opporom","");
            if(oplus.isEmpty()&&oppo.isEmpty())return 0;
            int dpi=(Integer)properties.getMethod("getInt",String.class,int.class).invoke(null,"persist.sys.display.density",0);
            if(dpi>0)return validDpi(dpi)?dpi:0;
            Class<?> global=Class.forName("android.view.WindowManagerGlobal");
            Object service=global.getMethod("getWindowManagerService").invoke(null);
            Class<?> manager=Class.forName("android.view.IWindowManager");
            dpi=(Integer)manager.getMethod("getInitialDisplayDensity",int.class).invoke(service,display.getDisplayId());
            return validDpi(dpi)?dpi:0;
        }catch(Exception|LinkageError unavailable){return 0;}
    }
    static int phoneDensity(int width,int height,float xdpi,float ydpi,int density,int baseline){
        if(!validDpi(density))return 160;
        if(width<=0||height<=0||xdpi<100||ydpi<100||!Float.isFinite(xdpi)||!Float.isFinite(ydpi))return density;
        if(baseline<=0)baseline=density;
        double longPx=Math.max(width,height),shortPx=Math.min(width,height);
        double longInches=longPx/Math.max(xdpi,ydpi),shortInches=shortPx/Math.min(xdpi,ydpi);
        double ppi=Math.hypot(longPx,shortPx)/Math.hypot(longInches,shortInches);
        double scale=Math.min(1,Math.min(longInches,shortInches)/2.8);
        double accessibility=(double)density/baseline;
        int dpi=(int)Math.round(ppi*1.1398963928222656*scale*accessibility);
        return validDpi(dpi)?dpi:density;
    }
    private static boolean validDpi(int dpi){return dpi>=120&&dpi<=1000;}
    static int correctTarget(int dpi,int originalBaseline,int normalizedBaseline){
        if(originalBaseline<=0||normalizedBaseline<=0)return dpi;
        return (int)Math.round((double)dpi*originalBaseline/normalizedBaseline);
    }
}
