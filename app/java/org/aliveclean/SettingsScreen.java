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
            type.getMethod("setNavigationContentDescription",CharSequence.class).invoke(toolbar,I18n.t("返回"));
            type.getMethod("setNavigationOnClickListener",View.OnClickListener.class).invoke(toolbar,(View.OnClickListener)v->activity.finish());
        }
        int height=ui.getResources().getDimensionPixelSize(ui.id("dimen","mz_action_bar_default_height_appcompat"));
        if(activity instanceof HomeActivity){
            LinearLayout bar=new LinearLayout(activity);bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.addView(toolbar,new LinearLayout.LayoutParams(0,-1,1));
            bar.addView(languageButton(activity,ui),new LinearLayout.LayoutParams(-2,-1));
            screen.addView(bar,new LinearLayout.LayoutParams(-1,height));
        }else screen.addView(toolbar,new LinearLayout.LayoutParams(-1,height));
        activity.setContentView(screen);return screen;
    }
    private static View languageButton(Activity activity,SettingsUi ui){
        float density=activity.getResources().getDisplayMetrics().density;int color=ui.color("colorOnSurface");
        TextView button=new TextView(activity);button.setText(I18n.t("语言"));button.setTextColor(color);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,15);button.setGravity(Gravity.CENTER_VERTICAL);button.setSingleLine(true);
        Drawable icon=activity.getDrawable(R.drawable.ic_language).mutate();icon.setTint(color);
        int size=Math.round(20*density);icon.setBounds(0,0,size,size);
        button.setCompoundDrawablesRelative(icon,null,null,null);button.setCompoundDrawablePadding(Math.round(6*density));
        button.setPaddingRelative(Math.round(12*density),0,Math.round(16*density),0);
        android.util.TypedValue ripple=new android.util.TypedValue();
        if(activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,ripple,true))button.setBackgroundResource(ripple.resourceId);
        button.setOnClickListener(v->I18n.choose(activity));
        return button;
    }
    static void fail(Activity activity,Exception error){
        android.util.Log.e("AliveClean","Official settings UI failed",error);
        new android.app.AlertDialog.Builder(activity).setTitle(I18n.t("页面无法打开")).setMessage(error.toString())
            .setPositiveButton(I18n.t("关闭"),(d,w)->activity.finish()).setOnCancelListener(d->activity.finish()).show();
    }
}
