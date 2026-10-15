package org.aliveclean;

import android.content.Context;
import android.content.res.Resources;

/** Native host insets, resolved from the installed plugin for this display configuration. */
final class NativeClockGeometry {
    private static final String PACKAGE="com.oplus.keyguard.personality.clocks";
    private final Resources resources;
    private final Context plugin;
    NativeClockGeometry(Context host)throws Exception {
        plugin=host.createPackageContext(PACKAGE,Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        resources=plugin.getResources();
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    float minimumDigitHeight(boolean vertical){
        try{
            ClassLoader loader=plugin.getClassLoader();
            Class<?> style=loader.loadClass("com.oplus.keyguard.config.ClockStyle");
            Object selected=Enum.valueOf((Class)style,vertical?"CENTER_VERTICAL_SINGLE_CLOCK":"CENTER_HORIZONTAL_SINGLE_CLOCK");
            // Installed TextFontHeightUtil owns device-specific font limits and resources.
            // Its getMinClockHeight uses getMinFontHeight + getMinFontHeightTextSize.
            float height=((Number)loader.loadClass("com.oplus.keyguard.clock.digital.util.TextFontHeightUtil")
                    .getMethod("getMinClockHeight",Context.class,style).invoke(null,plugin,selected)).floatValue();
            return Float.isFinite(height)&&height>0?height:0;
        }catch(ReflectiveOperationException|LinkageError|IllegalArgumentException unavailable){return 0;}
    }
    int top(boolean aod,boolean compact,boolean vertical){
        if(aod)return dimension(vertical&&!compact
                ?"digital_clock_single_center_vertical_aod_view_date_message_margin_top"
                :"digital_clock_single_center_horizontal_aod_view_date_message_margin_top");
        if(compact)return dimension("digital_clock_time_small_clock_margin_top");
        return dimension(vertical?"digital_clock_single_center_vertical_big_view_date_message_margin_top_classic"
                :"digital_clock_single_center_horizontal_big_view_date_message_margin_top");
    }
    int widgetInset(){return dimension("digital_clock_style_widget_margin_horizonal_locate_center");}
    int immersedTop(){return immersedDimension("digital_clock_single_center_horizontal_immersed_view_date_message_margin_top",80);}
    int immersedDateSize(){return immersedDimension("digital_clock_date_message_txt_size",18);}
    int immersedTimeSize(){return immersedDimension("digital_clock_immersed_view_clock_time_fixed_text_size",40);}
    private int immersedDimension(String name,int fallbackDp){
        int id=resources.getIdentifier(name,"dimen",PACKAGE);
        return id==0?Math.round(fallbackDp*resources.getDisplayMetrics().density):resources.getDimensionPixelSize(id);
    }
    int widgetGap(){return dimension("digital_clock_style_widget_margin_top_center_horizonal_clock");}
    int widgetExtent(int rows){
        if(rows<=0)return 0;
        // The native grid includes its card insets. Use the installed one/two-row
        // dimensions rather than a fixed reservation for two rows.
        int one=dimension("digital_clock_center_horizontal_one_row_widget_height");
        int two=dimension("digital_clock_center_horizontal_two_row_widget_height");
        return widgetGap()+one+(rows-1)*(two-one);
    }
    private int dimension(String name){
        int id=resources.getIdentifier(name,"dimen",PACKAGE);
        if(id==0)throw new Resources.NotFoundException("Native clock geometry: "+name);
        return resources.getDimensionPixelSize(id);
    }
}
