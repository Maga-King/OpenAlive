package org.aliveclean;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class VivoLibraryActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(I18n.wrap(base));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff000000);setContentView(root);
        VivoUi.fitSystemBars(root);
        TextView loading=new TextView(this);loading.setText(I18n.t("正在加载壁纸"));loading.setTextColor(-1);root.addView(loading);
        new Thread(()->{try{VivoEngineRuntime runtime=VivoEngineRuntime.prepare(this).get();runOnUiThread(()->{if(!isDestroyed())show(root,runtime);});}
            catch(Exception e){runOnUiThread(()->loading.setText(I18n.t("壁纸资源无法加载：")+e.getMessage()));}},"VivoLibrary").start();
    }
    private void show(LinearLayout root,VivoEngineRuntime runtime){
        root.removeAllViews();VivoUi ui=new VivoUi(this,runtime);
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=ui.text("‹",38);title.addView(back,new LinearLayout.LayoutParams(ui.dp(52),ui.dp(60)));back.setOnClickListener(v->finish());
        title.addView(ui.text(I18n.t("Vivo Alive 壁纸"),22));root.addView(title);
        LinearLayout row=new LinearLayout(this);row.setPadding(ui.dp(14),ui.dp(24),ui.dp(14),0);root.addView(row);
        String[]names={I18n.t("液态"),I18n.t("趣味光栅"),I18n.t("闪卡")};String[]assets={"vivo/default/liquid.jpg","vivo/default/raster-0.jpg","vivo/default/flash.jpg"};
        for(int i=0;i<3;i++){
            final int family=i+1;LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setGravity(Gravity.CENTER);
            row.addView(item,new LinearLayout.LayoutParams(0,-2,1));
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(ui.capsule());image.setClipToOutline(true);
            try{image.setImageBitmap(VivoImages.asset(runtime,assets[i]));}catch(Exception e){android.util.Log.w("AliveClean","Vivo category preview failed",e);}
            item.addView(image,new LinearLayout.LayoutParams(ui.dp(88),ui.dp(88)));
            TextView label=ui.text(names[i],16);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(44));item.addView(label,lp);
            item.setOnClickListener(v->startActivity(new Intent(this,VivoEditorActivity.class).putExtra("family",family)));
        }
    }
}
