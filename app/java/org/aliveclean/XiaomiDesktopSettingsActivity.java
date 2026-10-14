package org.aliveclean;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/** Shared desktop controls for all original Xiaomi wallpaper players. */
public final class XiaomiDesktopSettingsActivity extends Activity {
    private SettingsUi ui;
    private int amplitude,responsiveness;
    private final SeekBar[] sliders=new SeekBar[2];
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService saves=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean saveQueued;
    private final Runnable pendingSave=()->{saveQueued=false;persist();};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            ui=new SettingsUi(this);
            Bundle current=XiaomiState.call(this,"motionRead",null);
            amplitude=current.getInt("amplitude");responsiveness=current.getInt("responsiveness");
            LinearLayout screen=SettingsScreen.create(this,ui,"桌面设置");
            ScrollView scroll=new ScrollView(this);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(panel);
            TextView title=text("小米超级壁纸",20);title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);panel.addView(title);
            TextView summary=text("设置适用于全部小米超级壁纸，保存后立即生效。0 为小米默认效果。",14);summary.setPadding(0,dp(12),0,dp(20));panel.addView(summary);
            slider(panel,0,"桌面滑动幅度","−100 静止 · 0 默认 · +100 放大",amplitude);
            slider(panel,1,"桌面滑动跟手度","−100 延后 · 0 默认 · +100 即时跟手",responsiveness);
            TextView restore=text("恢复小米默认",16);restore.setTextColor(0xff1687ff);restore.setGravity(Gravity.CENTER);restore.setMinHeight(dp(52));
            panel.addView(restore);restore.setOnClickListener(v->{amplitude=responsiveness=0;sliders[0].setProgress(100);sliders[1].setProgress(100);save();});
        }catch(Exception error){SettingsScreen.fail(this,error);}
    }
    private void slider(LinearLayout panel,int index,String title,String hint,int value){
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(16),dp(16),dp(16));
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setCornerRadius(dp(20));background.setColor(ui.color("colorSurface"));card.setBackground(background);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);panel.addView(card,lp);
        TextView label=text(title+"  "+signed(value),17);card.addView(label);
        SeekBar slider=sliders[index]=new SeekBar(this);slider.setMax(200);slider.setProgress(value+100);slider.setContentDescription(title);
        LinearLayout.LayoutParams sliderLp=new LinearLayout.LayoutParams(-1,dp(48));card.addView(slider,sliderLp);
        card.addView(text(hint,13));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){int next=progress-100;label.setText(title+"  "+signed(next));if(index==0)amplitude=next;else responsiveness=next;if(user)save();}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){flush();}
        });
    }
    private void save(){if(!saveQueued){saveQueued=true;main.postDelayed(pendingSave,40);}}
    private void flush(){if(saveQueued){main.removeCallbacks(pendingSave);saveQueued=false;persist();}}
    private void persist(){Bundle b=new Bundle();b.putInt("amplitude",amplitude);b.putInt("responsiveness",responsiveness);
        android.content.Context app=getApplicationContext();
        saves.execute(()->{try{XiaomiState.call(app,"motionSave",b);}catch(RuntimeException error){
            main.post(()->{if(!isDestroyed())Toast.makeText(this,"桌面设置保存失败",Toast.LENGTH_SHORT).show();});
        }});
    }
    @Override protected void onPause(){flush();super.onPause();}
    @Override protected void onDestroy(){flush();saves.shutdown();super.onDestroy();}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(ui.color("colorOnSurface"));return t;}
    private static String signed(int value){return value>0?"+"+value:String.valueOf(value);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
