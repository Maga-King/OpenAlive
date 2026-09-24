package org.aliveclean;

import android.content.Context;
import android.content.res.Resources;

/** Native host insets, resolved from the installed plugin for this display configuration. */
final class NativeClockGeometry {
    private static final String PACKAGE="com.oplus.keyguard.personality.clocks";
    private final Resources resources;
    NativeClockGeometry(Context host)throws Exception {
        resources=host.createPackageContext(PACKAGE,0).getResources();
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
