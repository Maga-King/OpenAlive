package org.aliveclean;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** The three Vivo Alive families as tall cover cards. */
public final class VivoLibraryActivity extends Activity {
    private static final String[] NAMES={"液态","趣味光栅","闪卡"};
    private static final String[] NOTES={"液态流动效果","光栅切换效果","闪卡光泽效果"};
    private static final String[] ASSETS={"vivo/default/liquid.jpg","vivo/default/raster-0.jpg","vivo/default/flash.jpg"};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Look look=new Look(this);
        FrameLayout root=look.page(this,false);LinearLayout content=look.column(root);
        content.addView(look.bar(this,"Vivo Alive 壁纸",true));
        TextView intro=look.text("由 Vivo 原生引擎渲染，选择一种效果后再挑照片",14,look.muted,false);
        intro.setPadding(look.dp(24),look.dp(8),look.dp(24),look.dp(20));content.addView(intro);
        int width=getResources().getDisplayMetrics().widthPixels-look.dp(32);
        for(int i=0;i<3;i++){
            final int family=i+1;
            FrameLayout cover=look.cover(null,NAMES[i],NOTES[i],22);cover.setContentDescription(NAMES[i]);
            cover.setOnClickListener(v->startActivity(new Intent(this,VivoEditorActivity.class).putExtra("family",family)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,Math.round(width*.62f));lp.leftMargin=look.dp(16);lp.bottomMargin=look.dp(14);
            content.addView(cover,lp);
            Covers.vivo(this,ASSETS[i],image->runOnUiThread(()->{if(!isDestroyed()&&image!=null)Look.coverImage(cover,image);}));
        }
        setContentView(root);
    }
}
