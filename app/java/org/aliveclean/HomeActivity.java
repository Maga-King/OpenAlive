package org.aliveclean;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Home: the applied wallpaper in its three screen states, then the wallpaper sources. */
public final class HomeActivity extends Activity {
    private Look look;
    private FrameLayout root;
    private HomeActivationStatus activation;
    private final ArrayList<HomeScenePreview> previews=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
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
        root=look.page(this);LinearLayout content=look.column(root);
        LinearLayout bar=look.bar(this,"OpenAlive",false);
        Look.barRow(bar).addView(look.orb(R.drawable.ic_oa_settings,"设置",v->startActivity(new Intent(this,AppSettingsActivity.class))));
        content.addView(bar);
        content.addView(triptych());
        activation=new HomeActivationStatus(this,look,content);

        TextView section=look.text("壁纸",20,look.text,600);section.setPadding(look.dp(24),look.dp(30),look.dp(24),look.dp(14));content.addView(section);
        int width=getResources().getDisplayMetrics().widthPixels-look.dp(32),gap=look.dp(12);
        FrameLayout xiaomi=source(content,Covers.XIAOMI,"小米超级壁纸","地球、火星、土星等六款",width,Math.round(width*.46f),
            v->startActivity(new Intent(this,XiaomiLibraryActivity.class)));
        LinearLayout pair=new LinearLayout(this);pair.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pairLp=new LinearLayout.LayoutParams(-1,-2);pairLp.leftMargin=pairLp.rightMargin=look.dp(16);pairLp.topMargin=gap;
        content.addView(pair,pairLp);
        int half=(width-gap)/2,tall=Math.round(half*1.28f);
        FrameLayout alive=card(Covers.ALIVE,"Alive 壁纸","你的照片，主体浮于时钟之上",half,tall,v->openEditor(1,true));
        FrameLayout dynamic=card(Covers.DYNAMIC,"动态壁纸","魅族官方动态场景",half,tall,v->startActivity(new Intent(this,DynamicLibraryActivity.class)));
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(half,tall),right=new LinearLayout.LayoutParams(half,tall);right.setMarginStart(gap);
        pair.addView(alive,left);pair.addView(dynamic,right);
        FrameLayout vivo=look.cover(null,"Vivo Alive 壁纸","液态、趣味光栅与闪卡",22);
        vivo.setOnClickListener(v->startActivity(new Intent(this,VivoLibraryActivity.class)));
        LinearLayout.LayoutParams vivoLp=new LinearLayout.LayoutParams(width,Math.round(width*.46f));vivoLp.leftMargin=look.dp(16);vivoLp.topMargin=gap;
        content.addView(vivo,vivoLp);
        Covers.vivo(this,"vivo/default/liquid.jpg",image->runOnUiThread(()->{if(!isDestroyed()&&image!=null)Look.coverImage(vivo,image);}));
        setContentView(root);
    }
    private FrameLayout card(String asset,String title,String subtitle,int w,int h,View.OnClickListener click){
        FrameLayout card=look.cover(null,title,subtitle,22);card.setOnClickListener(click);card.setContentDescription(title);
        new Thread(()->{Bitmap image=Covers.asset(this,asset,w);runOnUiThread(()->{if(!isDestroyed())Look.coverImage(card,image);});},"Cover").start();
        return card;
    }
    private FrameLayout source(LinearLayout content,String asset,String title,String subtitle,int w,int h,View.OnClickListener click){
        FrameLayout card=card(asset,title,subtitle,w,h,click);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(w,h);lp.leftMargin=look.dp(16);content.addView(card,lp);return card;
    }
    /** Sleep, lock and home: lock raised in front, the others tucked behind it. */
    private View triptych(){
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        float aspect=Math.max(metrics.widthPixels,metrics.heightPixels)/(float)Math.max(1,Math.min(metrics.widthPixels,metrics.heightPixels));
        int width=metrics.widthPixels-look.dp(40),center=Math.round(width*.5f),side=Math.round(width*.36f);
        FrameLayout stage=new FrameLayout(this);stage.setClipChildren(false);stage.setClipToPadding(false);
        stage.setPadding(look.dp(20),look.dp(18),look.dp(20),look.dp(10));
        String[] labels={"息屏","锁屏","桌面"};
        int[] order={0,2,1};
        for(int i:order){
            final int mode=i;int w=i==1?center:side,h=Math.round(w*aspect);
            FrameLayout frame=new FrameLayout(this);
            HomeScenePreview preview=new HomeScenePreview(this,i);preview.radius(i==1?24:20);previews.add(preview);
            frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
            TextView label=look.text(labels[i],12.5f,0xffffffff,600);label.setGravity(Gravity.CENTER);
            label.setBackground(look.round(0x59000000,14));label.setPadding(look.dp(12),look.dp(6),look.dp(12),look.dp(6));
            FrameLayout.LayoutParams labelLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);labelLp.bottomMargin=look.dp(12);
            frame.addView(label,labelLp);
            look.lift(frame,i==1?24:20,i==1?22:8);
            frame.setForeground(look.pressable(null,i==1?24:20));frame.setContentDescription(labels[i]+"预览");
            frame.setOnClickListener(v->{if(!preview.openXiaomi())openEditor(mode,false);});
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w,h,i==1?Gravity.CENTER:Gravity.CENTER_VERTICAL|(i==0?Gravity.START:Gravity.END));
            if(i!=1){frame.setCameraDistance(8000*getResources().getDisplayMetrics().density);frame.setRotationY(i==0?14:-14);frame.setAlpha(.92f);}
            stage.addView(frame,lp);
        }
        int center0=Math.round(center*aspect);
        stage.setMinimumHeight(center0+look.dp(28));
        return stage;
    }
    private void openEditor(int mode,boolean photo){
        Intent intent=new Intent(this,MainActivity.class).putExtra("scene",mode);
        if(photo)intent.putExtra("photo_editor",true);startActivity(intent);
    }
    private final Runnable captureBackdrop=()->{
        // previews: [0]=AOD, [1]=desk, [2]=lock (added in that order).
        HomeScenePreview lock=null;for(HomeScenePreview p:previews)if(p.mode()==1)lock=p;
        Bitmap frame=lock==null?null:lock.snapshot();
        if(frame!=null){Look.backdrop=frame;look.backdrop(root,frame);}
    };
    @Override protected void onResume(){super.onResume();int request=++previewLoad;
        // The accent follows the applied wallpaper; rebuild after it changes.
        Look now=new Look(this);if(now.accent!=look.accent||now.dark!=darkAtCreate){recreate();return;}
        if(activation!=null)activation.start();
        new Thread(()->{
            XiaomiHomePreview.Card[] cards;
            try{cards=XiaomiHomePreview.load(this);}catch(Exception error){android.util.Log.w("OpenAliveXiaomi","Home wallpaper selection",error);cards=new XiaomiHomePreview.Card[3];}
            // 自适应 AOD shows the lock stage under the ColorOS AOD clock.
            if(cards[0]!=null&&cards[1]!=null&&AdaptiveAod.active(this))cards[0]=cards[1];
            XiaomiHomePreview.Card[] selected=cards;runOnUiThread(()->{
                if(isDestroyed()||request!=previewLoad)return;
                for(HomeScenePreview preview:previews)preview.start(selected[preview.mode()]);
                main.removeCallbacks(captureBackdrop);main.postDelayed(captureBackdrop,900);
            });
        },"HomeWallpaperPreviews").start();
    }
    @Override protected void onPause(){previewLoad++;main.removeCallbacks(captureBackdrop);if(activation!=null)activation.stop();for(HomeScenePreview preview:previews)preview.stop();super.onPause();}
    @Override protected void onDestroy(){if(activation!=null)activation.close();super.onDestroy();}
}
