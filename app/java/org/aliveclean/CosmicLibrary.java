package org.aliveclean;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Named official material variants; choosing one previews the complete three-state wallpaper. */
final class CosmicLibrary {
    interface Selection {void choose(int variant);}
    private static final class Entry {int id;String name,thumbnail;Bitmap bitmap;}
    static void show(Activity activity,OfficialDialogs dialogs,int selected,Selection selection){
        ArrayList<Entry> entries=new ArrayList<>();
        try{
            JSONArray data=new JSONArray(AssetGl.text(activity.getAssets(),"cosmic/catalog.json"));
            for(int i=0;i<data.length();i++){
                JSONObject item=data.getJSONObject(i);Entry entry=new Entry();entry.id=item.getInt("id");
                entry.name=item.getString("name");entry.thumbnail=item.getString("thumbnail");entries.add(entry);
            }
        }catch(IOException|JSONException error){Toast.makeText(activity,"动态壁纸目录无法打开",Toast.LENGTH_LONG).show();return;}
        float density=activity.getResources().getDisplayMetrics().density;
        GridView grid=new GridView(activity);grid.setNumColumns(3);grid.setHorizontalSpacing((int)(8*density));grid.setVerticalSpacing((int)(12*density));
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return entries.size();}
            public Object getItem(int p){return entries.get(p);}
            public long getItemId(int p){return entries.get(p).id;}
            public View getView(int p,View recycled,ViewGroup parent){
                LinearLayout cell;
                if(recycled instanceof LinearLayout)cell=(LinearLayout)recycled;
                else{
                    cell=new LinearLayout(activity);cell.setOrientation(LinearLayout.VERTICAL);cell.setPadding(4,4,4,4);
                    ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    cell.addView(image,new LinearLayout.LayoutParams(-1,(int)(112*density)));
                    TextView label=new TextView(activity);label.setGravity(Gravity.CENTER);label.setTextSize(13);label.setTextColor(-1);
                    cell.addView(label,new LinearLayout.LayoutParams(-1,(int)(32*density)));
                }
                Entry entry=entries.get(p);((ImageView)cell.getChildAt(0)).setImageBitmap(entry.bitmap);
                ((TextView)cell.getChildAt(1)).setText(entry.name);cell.setBackgroundColor(entry.id==selected?0xff167aca:0xff242424);
                cell.setContentDescription(entry.name+(entry.id==selected?"，已选择":""));return cell;
            }
        };
        grid.setAdapter(adapter);LinearLayout content=new LinearLayout(activity);content.setPadding(16,8,16,8);
        content.addView(grid,new LinearLayout.LayoutParams(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.6f)));
        Dialog dialog=dialogs.content("Alive 动态壁纸",content);ExecutorService decoder=Executors.newSingleThreadExecutor();
        dialog.setOnDismissListener(d->{decoder.shutdownNow();});
        grid.setOnItemClickListener((parent,view,p,id)->{dialog.dismiss();selection.choose(entries.get(p).id);});dialog.show();
        for(Entry entry:entries)decoder.execute(()->{
            if(Thread.currentThread().isInterrupted())return;
            try(InputStream in=activity.getAssets().open("cosmic/"+entry.thumbnail)){
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
                Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);
                activity.runOnUiThread(()->{if(dialog.isShowing()){entry.bitmap=bitmap;adapter.notifyDataSetChanged();}else if(bitmap!=null)bitmap.recycle();});
            }catch(IOException error){android.util.Log.e("AliveClean","Cosmic thumbnail unavailable",error);}
        });
    }
}
