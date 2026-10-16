package org.aliveclean;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import android.widget.FrameLayout;

/** Shared desktop controls for all original Xiaomi wallpaper players. */
public final class XiaomiDesktopSettingsActivity extends Activity {
    private Look look;
    private int amplitude,responsiveness;
    private final SeekBar[] sliders=new SeekBar[2];
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService saves=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean saveQueued;
    private final Runnable pendingSave=()->{saveQueued=false;persist();};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        look=new Look(this);
        FrameLayout root=look.page(this,false);LinearLayout content=look.column(root);
        content.addView(look.bar(this,"桌面滑动效果",true));
        TextView intro=look.text("适用于全部小米超级壁纸，拖动即生效。0 为小米默认效果。",14,look.muted,false);
        intro.setPadding(look.dp(24),look.dp(8),look.dp(24),look.dp(20));content.addView(intro);
        try{
            Bundle current=XiaomiState.call(this,"motionRead",null);
            amplitude=current.getInt("amplitude");responsiveness=current.getInt("responsiveness");
        }catch(RuntimeException error){SettingsScreen.fail(this,error);return;}
        slider(content,0,"滑动幅度","左侧静止，右侧放大",amplitude);
        slider(content,1,"跟手程度","左侧延后，右侧即时跟手",responsiveness);
        TextView restore=look.text("恢复小米默认",16,look.accent,500);restore.setGravity(Gravity.CENTER);
        restore.setBackground(look.pressable(look.round(look.surface,16),16));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,look.dp(52));rp.leftMargin=rp.rightMargin=look.dp(16);rp.topMargin=look.dp(8);
        content.addView(restore,rp);
        restore.setOnClickListener(v->{amplitude=responsiveness=0;sliders[0].setProgress(100);sliders[1].setProgress(100);save();});
        setContentView(root);
    }
    private void slider(LinearLayout content,int index,String title,String hint,int value){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(look.dp(20),look.dp(18),look.dp(20),look.dp(16));
        card.setBackground(look.round(look.surface,16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.leftMargin=lp.rightMargin=look.dp(16);lp.bottomMargin=look.dp(14);content.addView(card,lp);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(look.text(title,16.5f,look.text,500),new LinearLayout.LayoutParams(0,-2,1));
        TextView number=look.text(signed(value),22,look.accent,600);head.addView(number);card.addView(head);
        SeekBar slider=sliders[index]=look.slider();slider.setMax(200);slider.setProgress(value+100);slider.setContentDescription(title);
        LinearLayout.LayoutParams sliderLp=new LinearLayout.LayoutParams(-1,look.dp(44));sliderLp.topMargin=look.dp(6);card.addView(slider,sliderLp);
        card.addView(look.text(hint,13,look.muted,false));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){int next=progress-100;number.setText(signed(next));if(index==0)amplitude=next;else responsiveness=next;if(user)save();}
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
    private static String signed(int value){return value>0?"+"+value:String.valueOf(value);}
}
