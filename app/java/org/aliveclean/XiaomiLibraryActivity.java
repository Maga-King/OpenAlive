package org.aliveclean;

import android.app.*;
import android.content.*;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** The original Xiaomi scenes as banner cards. */
public final class XiaomiLibraryActivity extends Activity {
    private boolean busy;
    private int openRequest; private ProgressDialog preparing;
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Look look=new Look(this);
        FrameLayout root=look.page(this,false);LinearLayout content=look.column(root);
        content.addView(look.bar(this,"小米超级壁纸",true));
        TextView intro=look.text("原版场景，息屏、锁屏和桌面连续过渡",14,look.muted,false);
        intro.setPadding(look.dp(24),look.dp(8),look.dp(24),look.dp(20));content.addView(intro);
        // Warm the original UI bundle the preview pages use.
        new Thread(()->{try{XiaomiUi.bundle(this);}catch(Exception e){android.util.Log.w("OpenAliveXiaomi","Xiaomi UI bundle",e);}},"XiaomiUiLoad").start();
        int width=getResources().getDisplayMetrics().widthPixels-look.dp(32);
        List<XiaomiPacks.Pack> packs;
        try{packs=XiaomiPacks.all(this);}catch(Exception e){setContentView(root);failure(e);return;}
        for(XiaomiPacks.Pack pack:packs){
            FrameLayout cover=look.cover(null,pack.title,null,22);cover.setContentDescription(pack.title);
            cover.setOnClickListener(v->open(pack));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,Math.round(width*.36f));lp.leftMargin=look.dp(16);lp.bottomMargin=look.dp(14);
            content.addView(cover,lp);
            new Thread(()->{android.graphics.Bitmap image=Covers.asset(this,"xiaomi/"+pack.id+".banner",width);runOnUiThread(()->{if(!isDestroyed()&&image!=null)Look.coverImage(cover,image);});},"XiaomiCover").start();
        }
        setContentView(root);
    }
    private void open(XiaomiPacks.Pack pack){if(busy)return;busy=true;
        int request=++openRequest;
        main.postDelayed(()->{if(request!=openRequest||!busy||isFinishing()||isDestroyed())return;
            preparing=new ProgressDialog(this);preparing.setMessage("正在准备"+pack.title+"，首次使用请稍候…");preparing.setIndeterminate(true);
            preparing.setOnCancelListener(dialog->{openRequest++;busy=false;preparing=null;});preparing.show();
        },300);
        new Thread(()->{try{XiaomiPacks.find(this,pack);runOnUiThread(()->completeOpen(request,pack,null));}catch(Exception e){runOnUiThread(()->completeOpen(request,pack,e));}},"XiaomiPackCheck").start();
    }
    private void completeOpen(int request,XiaomiPacks.Pack pack,Exception error){if(request!=openRequest)return;busy=false;dismissPreparing();
        if(isFinishing()||isDestroyed())return;if(error==null)launch(pack);else failure(error);
    }
    private void dismissPreparing(){if(preparing!=null){preparing.dismiss();preparing=null;}}
    @Override public void onDestroy(){openRequest++;busy=false;main.removeCallbacksAndMessages(null);dismissPreparing();super.onDestroy();}
    private void launch(XiaomiPacks.Pack p){startActivity(new Intent().setClassName(this,"org.aliveclean.XiaomiPreviewActivity$"+Character.toUpperCase(p.id.charAt(0))+p.id.substring(1)));}
    @Override public boolean onMenuItemSelected(int feature,MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onMenuItemSelected(feature,item);}
    private void failure(Exception e){String reason=XiaomiFailure.describe(this,"Xiaomi catalog",e);android.util.Log.e("OpenAliveXiaomi","Xiaomi catalog",e);if(!isDestroyed())new AlertDialog.Builder(this).setTitle("小米壁纸暂时无法打开").setMessage(reason).setPositiveButton("确定",null).show();}
}
