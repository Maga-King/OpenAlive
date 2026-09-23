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

/** Official wallpaper grid cells, backed by the locally supported catalogue. */
public final class DynamicLibraryActivity extends Activity {
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final ArrayList<Entry> entries=new ArrayList<>();
    private SettingsUi ui;
    private static final class Entry {int id;String name,thumbnail;Bitmap bitmap;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            ui=new SettingsUi(this);LinearLayout screen=SettingsScreen.create(this,ui,"动态壁纸");
            JSONArray catalogue=new JSONArray(AssetGl.text(getAssets(),"cosmic/catalog.json"));
            for(int i=0;i<catalogue.length();i++){
                JSONObject item=catalogue.getJSONObject(i);Entry entry=new Entry();entry.id=item.getInt("id");entry.name=item.getString("name");entry.thumbnail=item.getString("thumbnail");entries.add(entry);
            }
            GridView grid=new GridView(this);grid.setNumColumns(3);grid.setClipToPadding(false);
            float density=getResources().getDisplayMetrics().density;
            grid.setPadding((int)(15*density),0,(int)(15*density),(int)(24*density));grid.setHorizontalSpacing((int)(11*density));grid.setVerticalSpacing((int)(12*density));
            BaseAdapter adapter=new BaseAdapter(){
                public int getCount(){return entries.size();}public Object getItem(int p){return entries.get(p);}public long getItemId(int p){return entries.get(p).id;}
                public View getView(int p,View recycled,ViewGroup parent){
                    View cell=recycled==null?ui.inflate("item_live_paper_with_category",parent):recycled;
                    Entry entry=entries.get(p);ImageView image=(ImageView)ui.find(cell,"image_thumb");image.setImageBitmap(entry.bitmap);
                    // The original grid uses responsive square thumbnails for this catalogue.
                    int width=Math.max(1,(grid.getWidth()-grid.getPaddingLeft()-grid.getPaddingRight()-2*grid.getHorizontalSpacing())/3);
                    ViewGroup.LayoutParams lp=image.getLayoutParams();lp.width=lp.height=width;image.setLayoutParams(lp);
                    TextView label=(TextView)ui.find(cell,"tv_name");label.setText(entry.name);label.setGravity(Gravity.CENTER);
                    ViewGroup.LayoutParams labelLp=label.getLayoutParams();labelLp.width=width;label.setLayoutParams(labelLp);
                    ui.find(cell,"image_category").setVisibility(View.GONE);cell.setContentDescription(entry.name);return cell;
                }
            };
            grid.setAdapter(adapter);screen.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
            grid.setOnItemClickListener((parent,view,p,id)->startActivity(new Intent(this,MainActivity.class).putExtra("cosmic_variant",entries.get(p).id)));
            for(Entry entry:entries)decoder.execute(()->{
                if(Thread.currentThread().isInterrupted())return;
                try(InputStream input=getAssets().open("cosmic/"+entry.thumbnail)){
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap bitmap=BitmapFactory.decodeStream(input,null,options);
                    runOnUiThread(()->{if(isDestroyed()){if(bitmap!=null)bitmap.recycle();return;}entry.bitmap=bitmap;adapter.notifyDataSetChanged();});
                }catch(IOException error){android.util.Log.e("AliveClean","Wallpaper thumbnail unavailable",error);}
            });
        }catch(Exception error){SettingsScreen.fail(this,error);}
    }
    @Override protected void onDestroy(){decoder.shutdownNow();super.onDestroy();}
}
