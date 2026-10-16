package org.aliveclean;

import android.app.Activity;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Settings that apply to every wallpaper: notification light and 自适应 AOD. */
public final class AppSettingsActivity extends Activity {
    private static final String[] NOTIFICATION={"跟随系统","短暂显示息屏","系统光效","Flyme 呼吸光环","Flyme 边缘光效"};
    private static final String[] PLAY={"关闭","3 秒","5 秒","10 秒","30 秒","一直"};
    private Look look;
    private Switch adaptive;
    private TextView aodFooter,playFooter,notificationSummary;
    private FrameLayout playSlot;
    private LinearLayout playGroup;
    private final ContentObserver aodMode=new ContentObserver(new Handler(Looper.getMainLooper())){
        @Override public void onChange(boolean self){bind();}
    };
    private boolean binding;
    private FrameLayout shrinkSlot,widthSlot;
    private Switch shortcuts,largeClock;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        look=new Look(this);
        FrameLayout screen=look.page(this,false);LinearLayout content=look.column(screen);
        content.addView(look.bar(this,"设置",true));

        content.addView(look.heading("息屏"));
        LinearLayout aod=look.group();
        adaptive=look.toggle();
        LinearLayout adaptiveRow=look.row(0,"自适应 AOD",null,adaptive);
        adaptiveRow.setOnClickListener(v->adaptive.toggle());
        adaptive.setOnCheckedChangeListener((v,on)->{if(!binding)save(on,AdaptiveAod.playSeconds(this));});
        aod.addView(adaptiveRow);
        content.addView(aod);
        aodFooter=look.footer("");content.addView(aodFooter);

        content.addView(look.heading("息屏后播放动画"));
        playGroup=look.group();
        playSlot=new FrameLayout(this);playSlot.setPadding(look.dp(12),look.dp(12),look.dp(12),look.dp(12));
        playGroup.addView(playSlot);content.addView(playGroup);
        playFooter=look.footer("");content.addView(playFooter);

        content.addView(look.heading("通知"));
        LinearLayout notification=look.group();
        View light=look.row(0,"通知光效","",look.chevron());notificationSummary=Look.summary(light);
        light.setOnClickListener(v->startActivity(new Intent(this,NotificationSettingsActivity.class)));
        notification.addView(light);content.addView(notification);

        content.addView(look.heading("锁屏与息屏"));
        LinearLayout lock=look.group();
        LinearLayout shrink=new LinearLayout(this);shrink.setOrientation(LinearLayout.VERTICAL);shrink.setPadding(look.dp(16),look.dp(14),look.dp(16),look.dp(12));
        shrink.addView(look.text("息屏时元素缩小",16,look.text,400));
        shrinkSlot=new FrameLayout(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=look.dp(12);shrink.addView(shrinkSlot,sp);
        lock.addView(shrink);
        shortcuts=look.toggle();View shortcutRow=look.row(0,"息屏保留快捷入口",null,shortcuts);shortcutRow.setOnClickListener(v->shortcuts.toggle());
        shortcuts.setOnCheckedChangeListener((v,on)->{if(!binding)LockTweaks.save(this,"aod_shortcuts",on);});lock.addView(shortcutRow);
        largeClock=look.toggle();View clockRow=look.row(0,"通知下沉时保持大时钟",null,largeClock);clockRow.setOnClickListener(v->largeClock.toggle());
        largeClock.setOnCheckedChangeListener((v,on)->{if(!binding)LockTweaks.save(this,"sink_large_clock",on);});lock.addView(clockRow);
        LinearLayout width=new LinearLayout(this);width.setOrientation(LinearLayout.VERTICAL);width.setPadding(look.dp(16),look.dp(14),look.dp(16),look.dp(12));
        width.addView(look.text("通知宽度",16,look.text,400));
        widthSlot=new FrameLayout(this);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.topMargin=look.dp(12);width.addView(widthSlot,wp);
        lock.addView(width);
        content.addView(lock);
        content.addView(look.footer("进入全景沉浸息屏时，ColorOS 会把时钟、通知和底部区域缩小到 90%，并隐藏左下、右下快捷入口。开启「允许通知下沉」后，流体云展开会让时钟切成小号并顶到上方。通知宽度同时作用于锁屏和通知中心。以上默认保持系统行为。"));

        content.addView(look.heading("小米超级壁纸"));
        LinearLayout xiaomi=look.group();
        View desktop=look.row(0,"桌面滑动效果",null,look.chevron());
        desktop.setOnClickListener(v->startActivity(new Intent(this,XiaomiDesktopSettingsActivity.class)));
        xiaomi.addView(desktop);content.addView(xiaomi);

        String version="";
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        TextView about=look.text("OpenAlive "+version,12,look.faint,false);about.setGravity(Gravity.CENTER);about.setPadding(0,look.dp(40),0,0);
        content.addView(about);
        setContentView(screen);
    }
    private void save(boolean enabled,int seconds){AdaptiveAod.save(this,enabled,seconds);bind();}
    private void bind(){
        binding=true;
        boolean enabled=AdaptiveAod.enabled(this),panoramic=AdaptiveAod.panoramic(this);int seconds=AdaptiveAod.playSeconds(this);
        adaptive.setChecked(enabled);
        aodFooter.setText("系统息屏选择全景沉浸时，息屏交给 ColorOS：使用系统时钟，壁纸停在锁屏画面。局部息屏不受影响。\n"
            +(!enabled?"已关闭，全景沉浸下使用各壁纸自带的息屏效果。"
            :panoramic?"当前系统息屏为全景沉浸，正在生效。"
            :"当前系统息屏不是全景沉浸，暂不生效。"));
        int selected=0;for(int i=0;i<AdaptiveAod.PLAY_CHOICES.length;i++)if(AdaptiveAod.PLAY_CHOICES[i]==seconds)selected=i;
        playSlot.removeAllViews();
        playSlot.addView(look.segmented(PLAY,selected,i->{if(AdaptiveAod.enabled(this))save(true,AdaptiveAod.PLAY_CHOICES[i]);}));
        playGroup.setAlpha(enabled?1:.45f);
        playFooter.setText(!enabled?"需要先开启自适应 AOD。"
            :seconds==0?"息屏后壁纸立即定格，最省电。"
            :seconds<0?"息屏期间一直播放，耗电明显增加。"
            :"息屏后继续播放 "+seconds+" 秒，然后定格。");
        shrinkSlot.removeAllViews();
        shrinkSlot.addView(look.segmented(new String[]{"系统默认","减半","不缩小"},LockTweaks.aodScaleIndex(this),i->{LockTweaks.save(this,"aod_scale",i);bind();}));
        widthSlot.removeAllViews();
        widthSlot.addView(look.segmented(new String[]{"系统默认","稍窄","更窄"},LockTweaks.notificationInsetIndex(this),i->{LockTweaks.save(this,"notification_inset",i);bind();}));
        shortcuts.setChecked(LockTweaks.aodShortcuts(this));largeClock.setChecked(LockTweaks.sinkLargeClock(this));
        int mode=NotificationOptions.preferences(this).getInt("mode",0);
        notificationSummary.setText(NOTIFICATION[mode>=0&&mode<NOTIFICATION.length?mode:0]);
        binding=false;
    }
    @Override protected void onResume(){
        super.onResume();bind();
        for(String key:new String[]{"Setting_AodState","Setting_AodSwitchEnable"})
            try{getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(key),false,aodMode);}catch(RuntimeException ignored){}
    }
    @Override protected void onPause(){getContentResolver().unregisterContentObserver(aodMode);super.onPause();}
}
