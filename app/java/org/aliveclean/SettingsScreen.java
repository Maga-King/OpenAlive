package org.aliveclean;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;

/** Original Flyme toolbar and page resources, with host-owned navigation. */
final class SettingsScreen {
    static LinearLayout create(Activity activity,SettingsUi ui,String title)throws Exception{
        LinearLayout screen=new LinearLayout(activity);screen.setOrientation(LinearLayout.VERTICAL);
        int background=ui.pageColor();screen.setBackgroundColor(background);
        activity.getWindow().setStatusBarColor(background);activity.getWindow().setNavigationBarColor(background);
        boolean light=android.graphics.Color.luminance(background)>.5f;
        screen.setSystemUiVisibility(light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        screen.setFitsSystemWindows(true);
        Class<?> type=ui.getClassLoader().loadClass("flyme.support.v7.widget.Toolbar");
        View toolbar=(View)type.getConstructor(android.content.Context.class,android.util.AttributeSet.class).newInstance(ui,null);
        toolbar.setBackgroundColor(background);
        type.getMethod("setTitle",CharSequence.class).invoke(toolbar,title);
        type.getMethod("setTitleTextColor",int.class).invoke(toolbar,ui.color("colorOnSurface"));
        if(activity instanceof HomeActivity){
            type.getMethod("setNavigationIcon",Drawable.class).invoke(toolbar,new Object[]{null});
        }else{
            Drawable back=ui.getDrawable(ui.id("drawable","ic_back_wallpaper_apply")).mutate();
            back.setTint(ui.color("colorOnSurface"));
            type.getMethod("setNavigationIcon",Drawable.class).invoke(toolbar,back);
            type.getMethod("setNavigationContentDescription",CharSequence.class).invoke(toolbar,"返回");
            type.getMethod("setNavigationOnClickListener",View.OnClickListener.class).invoke(toolbar,(View.OnClickListener)v->activity.finish());
        }
        screen.addView(toolbar,new LinearLayout.LayoutParams(-1,ui.getResources().getDimensionPixelSize(ui.id("dimen","mz_action_bar_default_height_appcompat"))));
        activity.setContentView(screen);return screen;
    }
    static void fail(Activity activity,Exception error){
        android.util.Log.e("AliveClean","Official settings UI failed",error);
        new android.app.AlertDialog.Builder(activity).setTitle("页面无法打开").setMessage(error.toString())
            .setPositiveButton("关闭",(d,w)->activity.finish()).setOnCancelListener(d->activity.finish()).show();
    }
}
