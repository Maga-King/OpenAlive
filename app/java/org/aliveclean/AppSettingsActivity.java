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
    private Look look;
    private Switch adaptive;
    private TextView adaptiveStatus,playHint,notificationSummary;
    private LinearLayout chips;
    private final ContentObserver aodMode=new ContentObserver(new Handler(Looper.getMainLooper())){
        @Override public void onChange(boolean self){bind();}
    };
    private boolean binding;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        look=new Look(this);
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);look.window(this,screen);
        screen.addView(look.bar(this,"设置",true,20));
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,0,0,look.dp(32));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        content.addView(look.heading("息屏"));
        LinearLayout aod=look.group();
        adaptive=look.toggle();
        LinearLayout adaptiveRow=look.row(R.drawable.ic_oa_moon,"自适应 AOD","系统息屏选择全景沉浸时，息屏交给 ColorOS：保留系统时钟，壁纸停在锁屏画面",adaptive);
        adaptiveRow.setOnClickListener(v->adaptive.toggle());
        adaptive.setOnCheckedChangeListener((v,on)->{if(!binding)save(on,AdaptiveAod.playSeconds(this));});
        aod.addView(adaptiveRow);

        LinearLayout play=new LinearLayout(this);play.setOrientation(LinearLayout.VERTICAL);play.setPadding(look.dp(16),look.dp(16),look.dp(16),look.dp(18));
        LinearLayout playHead=look.row(R.drawable.ic_oa_play,"息屏后播放动画",null,null);playHead.setPadding(0,0,0,0);playHead.setMinimumHeight(0);playHead.setBackground(null);
        play.addView(playHead);
        playHint=look.text("",13,look.muted,false);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=look.dp(10);hp.setMarginStart(look.dp(56));play.addView(playHint,hp);
        HorizontalScrollView chipScroll=new HorizontalScrollView(this);chipScroll.setHorizontalScrollBarEnabled(false);chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chips.setPadding(look.dp(56),0,0,0);
        for(int seconds:AdaptiveAod.PLAY_CHOICES){
            TextView chip=look.text(AdaptiveAod.playLabel(seconds),14,look.text,false);chip.setTag(seconds);
            chip.setGravity(Gravity.CENTER);chip.setPadding(look.dp(14),0,look.dp(14),0);chip.setMinWidth(look.dp(52));
            chip.setOnClickListener(v->save(AdaptiveAod.enabled(this),seconds));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,look.dp(36));cp.setMarginEnd(look.dp(8));chips.addView(chip,cp);
        }
        chipScroll.addView(chips);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=look.dp(14);play.addView(chipScroll,sp);
        aod.addView(play);
        content.addView(aod);
        adaptiveStatus=look.text("",13,look.muted,false);adaptiveStatus.setPadding(look.dp(28),look.dp(10),look.dp(28),0);
        content.addView(adaptiveStatus);

        content.addView(look.heading("通知"));
        LinearLayout notification=look.group();
        View light=look.row(R.drawable.ic_oa_light,"通知光效","",look.chevron());notificationSummary=Look.summary(light);
        light.setOnClickListener(v->startActivity(new Intent(this,NotificationSettingsActivity.class)));
        notification.addView(light);content.addView(notification);

        content.addView(look.heading("小米超级壁纸"));
        LinearLayout xiaomi=look.group();
        View desktop=look.row(R.drawable.ic_xiaomi_official,"桌面滑动效果","翻页时的视差幅度和跟手程度",look.chevron());
        desktop.setOnClickListener(v->startActivity(new Intent(this,XiaomiDesktopSettingsActivity.class)));
        xiaomi.addView(desktop);content.addView(xiaomi);

        String version="";
        try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        TextView about=look.text("OpenAlive "+version,12,look.muted,false);about.setGravity(Gravity.CENTER);about.setPadding(0,look.dp(36),0,0);
        content.addView(about);
        setContentView(screen);
    }
    private void save(boolean enabled,int seconds){AdaptiveAod.save(this,enabled,seconds);bind();}
    private void bind(){
        binding=true;
        boolean enabled=AdaptiveAod.enabled(this),panoramic=AdaptiveAod.panoramic(this);int seconds=AdaptiveAod.playSeconds(this);
        adaptive.setChecked(enabled);
        adaptiveStatus.setText(!enabled?"已关闭：全景沉浸下使用各壁纸自带的息屏效果"
            :panoramic?"当前系统息屏为全景沉浸，已生效"
            :"当前系统息屏不是全景沉浸，局部息屏保持不变");
        playHint.setText(seconds==0?"息屏后壁纸立即定格"
            :seconds<0?"息屏期间一直播放，耗电明显增加"
            :"息屏后继续播放 "+seconds+" 秒，然后定格");
        for(int i=0;i<chips.getChildCount();i++){
            TextView chip=(TextView)chips.getChildAt(i);boolean on=(Integer)chip.getTag()==seconds;
            chip.setEnabled(enabled);chip.setAlpha(enabled?1:.4f);chip.setSelected(on);
            chip.setBackground(on?look.round(look.accent,18):look.pressable(look.raised,18));
            chip.setTextColor(on?look.onAccent:look.text);
            chip.setContentDescription("息屏后播放 "+AdaptiveAod.playLabel((Integer)chip.getTag())+(on?"，已选择":""));
        }
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
