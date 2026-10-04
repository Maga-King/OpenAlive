package org.aliveclean;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Bind our destinations to the original personalization home layout. */
public final class HomeActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(I18n.wrap(base));}
    private SettingsUi ui;
    private final ArrayList<HomeScenePreview> previews=new ArrayList<>();
    private int previewLoad;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        // ColorOS can deny SystemUI's cold ContentProvider start even when the
        // module and provider are installed. Apply its own associated-startup
        // setting once after installation or an app-data reset.
        if(!getSharedPreferences("native_clock_startup",0).getBoolean("allowed",false)){
            new Thread(()->{
                try{
                    RootBridge.allowClockStartup(this);
                    getSharedPreferences("native_clock_startup",0).edit().putBoolean("allowed",true).apply();
                }catch(Exception failure){android.util.Log.w("OpenAliveClock","Clock startup setup unavailable",failure);}
            },"OpenAliveClockStartup").start();
        }
        try{
            ui=new SettingsUi(this);
            LinearLayout screen=SettingsScreen.create(this,ui,I18n.t("桌面、壁纸和个性化"));
            View root=ui.inflate("activity_system_customize_center",screen);
            ui.pageBackground(root);
            screen.addView(root,new LinearLayout.LayoutParams(-1,0,1));
            // Keep native constraints; GONE rows collapse using the official chain.
            String[] hidden={"appearance_manage_tips",
                "system_more_breath_lamp","system_text_wallpaper","system_more_alive","system_more_weather",
                "system_more_selection","system_more_historical_theme","system_title_more","system_center_more","system_center_other","system_f11_layout"};
            for(String id:hidden)ui.find(root,id).setVisibility(View.GONE);
            View manage=ui.find(root,"system_layout_manager_appearance");
            manage.setVisibility(View.GONE); // Meizu appearance-account management is not a local destination.
            // The native fixed-height slot reserves space for its remote preview.
            // Our local cards already measure their own image and caption heights.
            for(String id:new String[]{"system_layout_include","system_layout_container"}){
                View slot=ui.find(root,id);ViewGroup.LayoutParams slotLp=slot.getLayoutParams();
                slotLp.height=ViewGroup.LayoutParams.WRAP_CONTENT;slot.setLayoutParams(slotLp);
            }
            View wallpaper=ui.find(root,"system_center_wallpaper");
            topGap(wallpaper,12);
            ui.cardBackground(wallpaper);
            firstText(wallpaper).setText(I18n.t("动态壁纸"));wallpaper.setOnClickListener(v->startActivity(new Intent(this,DynamicLibraryActivity.class)));
            ViewGroup parent=(ViewGroup)wallpaper.getParent();
            View alive=ui.inflate("view_system_center_wallpaper",parent);alive.setId(View.generateViewId());
            ui.cardBackground(alive);
            ViewGroup.LayoutParams lp=wallpaper.getLayoutParams().getClass().getConstructor(ViewGroup.LayoutParams.class).newInstance(wallpaper.getLayoutParams());
            for(java.lang.reflect.Field field:lp.getClass().getFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&field.getType().isPrimitive())field.set(lp,field.get(wallpaper.getLayoutParams()));
            replaceAnchor(lp,ui.id("id","system_layout_container"),wallpaper.getId());
            ((ViewGroup.MarginLayoutParams)lp).topMargin=Math.round(12*getResources().getDisplayMetrics().density);
            parent.addView(alive,lp);firstText(alive).setText(I18n.t("Alive 壁纸"));alive.setOnClickListener(v->openEditor(1,true));
            View vivo=ui.inflate("view_system_center_wallpaper",parent);vivo.setId(View.generateViewId());ui.cardBackground(vivo);
            ViewGroup.LayoutParams vivoLp=lp.getClass().getConstructor(ViewGroup.LayoutParams.class).newInstance(lp);
            for(java.lang.reflect.Field field:lp.getClass().getFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&field.getType().isPrimitive())field.set(vivoLp,field.get(lp));
            replaceAnchor(vivoLp,wallpaper.getId(),alive.getId());parent.addView(vivo,vivoLp);firstText(vivo).setText(I18n.t("Vivo Alive 壁纸"));
            vivo.setOnClickListener(v->startActivity(new Intent(this,VivoLibraryActivity.class)));
            View xiaomi=ui.inflate("view_system_center_wallpaper",parent);xiaomi.setId(View.generateViewId());ui.cardBackground(xiaomi);
            ViewGroup.LayoutParams xiaomiLp=vivoLp.getClass().getConstructor(ViewGroup.LayoutParams.class).newInstance(vivoLp);
            for(java.lang.reflect.Field field:vivoLp.getClass().getFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&field.getType().isPrimitive())field.set(xiaomiLp,field.get(vivoLp));
            replaceAnchor(xiaomiLp,alive.getId(),vivo.getId());parent.addView(xiaomi,xiaomiLp);firstText(xiaomi).setText(I18n.t("小米Alive壁纸"));
            xiaomi.setOnClickListener(v->startActivity(new Intent(this,XiaomiLibraryActivity.class)));
            // The original tiles are pinned to opposite screen edges, while the
            // wallpaper bars have a centered content width. Put the original
            // tiles in one equally divided row with that same width instead.
            LinearLayout shortcuts=new LinearLayout(this);shortcuts.setId(View.generateViewId());
            shortcuts.setOrientation(LinearLayout.HORIZONTAL);shortcuts.setBaselineAligned(false);
            ViewGroup.LayoutParams shortcutLp=wallpaper.getLayoutParams().getClass().getConstructor(ViewGroup.LayoutParams.class).newInstance(wallpaper.getLayoutParams());
            for(java.lang.reflect.Field field:shortcutLp.getClass().getFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&field.getType().isPrimitive())field.set(shortcutLp,field.get(wallpaper.getLayoutParams()));
            replaceAnchor(shortcutLp,ui.id("id","system_layout_container"),xiaomi.getId());
            shortcutLp.height=ViewGroup.LayoutParams.WRAP_CONTENT;parent.addView(shortcuts,shortcutLp);
            int shortcutCount=0;
            for(boolean font:new boolean[]{false,true}){
                View tile=ui.find(root,font?"system_center_font":"system_center_theme");Intent destination=ThemeLinks.resolve(this,font);
                tile.setVisibility(destination==null?View.GONE:View.VISIBLE);
                parent.removeView(tile);
                LinearLayout.LayoutParams tileLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
                if(destination!=null&&shortcutCount>0)tileLp.setMarginStart(ui.getResources().getDimensionPixelSize(ui.id("dimen","common_15dp")));
                shortcuts.addView(tile,tileLp);
                ((TextView)ui.find(tile,"item_text")).setText(font?I18n.t("字体"):I18n.t("主题"));
                ((ImageView)ui.find(tile,"item_image")).setImageResource(ui.id("drawable",font?"icon_system_setting_font":"icon_system_setting_theme"));
                ui.cardBackground(tile);tile.setOnClickListener(v->ThemeLinks.open(this,destination));
                if(destination!=null)shortcutCount++;
            }
            shortcuts.setVisibility(shortcutCount==0?View.GONE:View.VISIBLE);
            int extensionAnchor=shortcutCount==0?xiaomi.getId():shortcuts.getId();
            View extensions=ui.find(root,"system_center_extension");ViewGroup.LayoutParams extensionsLp=extensions.getLayoutParams();
            replaceAnchor(extensionsLp,ui.id("id","system_center_theme"),extensionAnchor);extensions.setLayoutParams(extensionsLp);
            View light=ui.find(root,"system_more_light_effect");
            topGap(light,12);
            ui.cardBackground(light);
            ((ImageView)ui.find(light,"item_image")).setImageResource(ui.id("drawable","ic_system_setting_more_light_effect"));
            ((TextView)ui.find(light,"item_title")).setText(I18n.t("通知光效"));
            ((TextView)ui.find(light,"item_des")).setText(ui.getString(ui.id("string","system_setting_more_des_1")));
            light.setOnClickListener(v->startActivity(new Intent(this,NotificationSettingsActivity.class)));
            String[] cards={"system_layout_aod","system_layout_lock","system_layout_launcher"};
            String[] labels={I18n.t("息屏"),I18n.t("锁屏"),I18n.t("桌面")};
            for(int i=0;i<3;i++){
                final int mode=i;View card=ui.find(root,cards[i]);((TextView)ui.find(card,"item_text")).setText(labels[i]);
                ImageView image=(ImageView)ui.find(card,"item_image");ViewGroup holder=(ViewGroup)image.getParent();
                int index=holder.indexOfChild(image);ViewGroup.LayoutParams params=image.getLayoutParams();holder.removeView(image);
                HomeScenePreview preview=new HomeScenePreview(this,i);preview.setId(image.getId());holder.addView(preview,index,params);previews.add(preview);
                card.setOnClickListener(v->{if(!preview.openXiaomi())openEditor(mode,false);});card.setContentDescription(labels[i]+I18n.t("预览"));
            }
        }catch(Exception error){SettingsScreen.fail(this,error);}
    }
    private static TextView firstText(View view){
        if(view instanceof TextView)return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView result=firstText(group.getChildAt(i));if(result!=null)return result;}}
        return null;
    }
    private void topGap(View view,int dp){
        ViewGroup.MarginLayoutParams params=(ViewGroup.MarginLayoutParams)view.getLayoutParams();
        params.topMargin=Math.round(dp*getResources().getDisplayMetrics().density);view.setLayoutParams(params);
    }
    private static void replaceAnchor(ViewGroup.LayoutParams params,int previous,int next)throws Exception{
        // Preserve the original constraints even when the library's public fields
        // are obfuscated. Only replace references to the known sibling view.
        boolean changed=false;
        for(java.lang.reflect.Field field:params.getClass().getFields())if(field.getType()==int.class&&field.getInt(params)==previous){field.setInt(params,next);changed=true;}
        if(!changed)throw new IllegalStateException("Missing original layout anchor");
    }
    private void openEditor(int mode,boolean photo){
        Intent intent=new Intent(this,MainActivity.class).putExtra("scene",mode);
        if(photo)intent.putExtra("photo_editor",true);startActivity(intent);
    }
    @Override protected void onResume(){super.onResume();int request=++previewLoad;
        new Thread(()->{
            XiaomiHomePreview.Card[] cards;
            try{cards=XiaomiHomePreview.load(this);}catch(Exception error){android.util.Log.w("OpenAliveXiaomi","Home wallpaper selection",error);cards=new XiaomiHomePreview.Card[3];}
            XiaomiHomePreview.Card[] selected=cards;runOnUiThread(()->{if(isDestroyed()||request!=previewLoad)return;for(int i=0;i<previews.size();i++)previews.get(i).start(selected[i]);});
        },"HomeWallpaperPreviews").start();
    }
    @Override protected void onPause(){previewLoad++;for(HomeScenePreview preview:previews)preview.stop();super.onPause();}
}
