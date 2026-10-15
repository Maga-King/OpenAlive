package org.aliveclean;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Home: the applied wallpaper live in its three screen states, then the wallpaper sources. */
public final class HomeActivity extends Activity {
    private Look look;
    private HomeActivationStatus activation;
    private final ArrayList<HomeScenePreview> previews=new ArrayList<>();
    private int previewLoad;
    private boolean darkAtCreate;
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
        look=new Look(this);darkAtCreate=look.dark;
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);look.window(this,screen);
        LinearLayout bar=look.bar(this,"OpenAlive",false,26);
        bar.addView(look.barAction(R.drawable.ic_oa_settings,"设置",v->startActivity(new Intent(this,AppSettingsActivity.class))));
        screen.addView(bar);
        ScrollView scroll=new ScrollView(this);scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,look.dp(8),0,look.dp(32));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(triptych());
        activation=new HomeActivationStatus(this,look,content);

        content.addView(look.heading("壁纸"));
        LinearLayout group=look.group();
        source(group,R.drawable.ic_oa_photo,"Alive 壁纸","用自己的照片，主体与时钟分层",v->openEditor(1,true));
        source(group,R.drawable.ic_oa_motion,"动态壁纸","魅族官方动态场景",v->startActivity(new Intent(this,DynamicLibraryActivity.class)));
        source(group,R.drawable.ic_oa_layers,"Vivo Alive 壁纸","Vivo 原生引擎的照片动效",v->startActivity(new Intent(this,VivoLibraryActivity.class)));
        source(group,R.drawable.ic_oa_orbit,"小米超级壁纸","地球、火星、土星等六款",v->startActivity(new Intent(this,XiaomiLibraryActivity.class)));
        content.addView(group);
        setContentView(screen);
    }
    private void source(LinearLayout group,int icon,String title,String summary,View.OnClickListener click){
        View row=look.row(icon,title,summary,look.chevron());row.setOnClickListener(click);group.addView(row);
    }
    /** Sleep, lock and home side by side, lock in the middle and largest. */
    private View triptych(){
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        float aspect=Math.max(metrics.widthPixels,metrics.heightPixels)/(float)Math.max(1,Math.min(metrics.widthPixels,metrics.heightPixels));
        int width=metrics.widthPixels-look.dp(40),center=Math.round(width*.42f),side=Math.round(width*.27f),gap=(width-center-2*side)/2;
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);
        row.setPadding(look.dp(20),look.dp(12),look.dp(20),0);
        String[] labels={"息屏","锁屏","桌面"};
        for(int i=0;i<3;i++){
            final int mode=i;int w=i==1?center:side;
            LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setGravity(Gravity.CENTER_HORIZONTAL);
            HomeScenePreview preview=new HomeScenePreview(this,i);preview.radius(i==1?18:14);previews.add(preview);
            preview.setContentDescription(labels[i]+"预览");preview.setForeground(look.pressable(0,i==1?18:14));
            preview.setOnClickListener(v->{if(!preview.openXiaomi())openEditor(mode,false);});
            column.addView(preview,new LinearLayout.LayoutParams(w,Math.round(w*aspect)));
            TextView label=look.text(labels[i],13,i==1?look.text:look.muted,i==1);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=look.dp(12);column.addView(label,lp);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(w,-2);if(i>0)cp.setMarginStart(gap);
            row.addView(column,cp);
        }
        return row;
    }
    private void openEditor(int mode,boolean photo){
        Intent intent=new Intent(this,MainActivity.class).putExtra("scene",mode);
        if(photo)intent.putExtra("photo_editor",true);startActivity(intent);
    }
    @Override protected void onResume(){super.onResume();int request=++previewLoad;
        // The accent follows the applied wallpaper; rebuild after it changes.
        Look now=new Look(this);if(now.accent!=look.accent||now.dark!=darkAtCreate){recreate();return;}
        if(activation!=null)activation.start();
        new Thread(()->{
            XiaomiHomePreview.Card[] cards;
            try{cards=XiaomiHomePreview.load(this);}catch(Exception error){android.util.Log.w("OpenAliveXiaomi","Home wallpaper selection",error);cards=new XiaomiHomePreview.Card[3];}
            // 自适应 AOD shows the lock stage under the ColorOS AOD clock.
            if(cards[0]!=null&&cards[1]!=null&&AdaptiveAod.active(this))cards[0]=cards[1];
            XiaomiHomePreview.Card[] selected=cards;runOnUiThread(()->{if(isDestroyed()||request!=previewLoad)return;for(int i=0;i<previews.size();i++)previews.get(i).start(selected[i]);});
        },"HomeWallpaperPreviews").start();
    }
    @Override protected void onPause(){previewLoad++;if(activation!=null)activation.stop();for(HomeScenePreview preview:previews)preview.stop();super.onPause();}
    @Override protected void onDestroy(){if(activation!=null)activation.close();super.onDestroy();}
}
