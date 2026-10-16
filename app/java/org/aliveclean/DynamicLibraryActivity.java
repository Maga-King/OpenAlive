package org.aliveclean;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Flyme dynamic scenes from the locally supported catalogue. */
public final class DynamicLibraryActivity extends Activity {
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private static final class Entry {int id;String name,thumbnail;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Look look=new Look(this);
        FrameLayout root=look.page(this,false);LinearLayout content=look.column(root);
        content.addView(look.bar(this,"动态壁纸",true));
        TextView intro=look.text("魅族官方动态场景，锁屏、桌面和息屏之间连续过渡",14,look.muted,false);
        intro.setPadding(look.dp(24),look.dp(8),look.dp(24),look.dp(20));content.addView(intro);
        ArrayList<Entry> entries=new ArrayList<>();
        try{
            JSONArray catalogue=new JSONArray(AssetGl.text(getAssets(),"cosmic/catalog.json"));
            for(int i=0;i<catalogue.length();i++){
                JSONObject item=catalogue.getJSONObject(i);Entry entry=new Entry();entry.id=item.getInt("id");entry.name=item.getString("name");entry.thumbnail=item.getString("thumbnail");entries.add(entry);
            }
        }catch(Exception error){SettingsScreen.fail(this,error);return;}
        int columns=3,gap=look.dp(12),side=look.dp(16);
        int cell=(getResources().getDisplayMetrics().widthPixels-2*side-(columns-1)*gap)/columns;
        LinearLayout row=null;
        for(int i=0;i<entries.size();i++){
            if(i%columns==0){
                row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.leftMargin=lp.rightMargin=side;lp.bottomMargin=look.dp(18);
                content.addView(row,lp);
            }
            Entry entry=entries.get(i);
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout cover=look.cover(null,null,null,22);
            item.addView(cover,new LinearLayout.LayoutParams(cell,Math.round(cell*1.22f)));
            TextView name=look.text(entry.name,13.5f,look.text,500);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.topMargin=look.dp(10);item.addView(name,np);
            cover.setContentDescription(entry.name);
            cover.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("cosmic_variant",entry.id)));
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(cell,-2);if(i%columns>0)ip.setMarginStart(gap);
            row.addView(item,ip);
            decoder.execute(()->{
                if(Thread.currentThread().isInterrupted())return;
                Bitmap bitmap=Covers.asset(this,entry.thumbnail.indexOf('/')>=0?entry.thumbnail:"cosmic/"+entry.thumbnail,cell);
                runOnUiThread(()->{if(!isDestroyed()&&bitmap!=null)Look.coverImage(cover,bitmap);});
            });
        }
        setContentView(root);
    }
    @Override protected void onDestroy(){decoder.shutdownNow();super.onDestroy();}
}
