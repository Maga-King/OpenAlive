package org.aliveclean;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Local ROM gallery. Thumbnails are decoded away from the UI/render threads. */
final class WallpaperLibrary {
    interface Selection { void choose(String asset); }
    private static final class Entry {
        String image,thumbnail,label;
        Bitmap bitmap;
    }

    static void show(Activity activity,OfficialDialogs dialogs,Selection selection) {
        ArrayList<Entry> entries=new ArrayList<>();
        try(InputStream in=activity.getAssets().open("wallpapers/catalog.json");ByteArrayOutputStream data=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)data.write(buffer,0,n);
            JSONArray catalog=new JSONArray(data.toString("UTF-8"));
            for(int i=0;i<catalog.length();i++){
                JSONObject item=catalog.getJSONObject(i);Entry entry=new Entry();
                entry.image=item.getString("image");entry.thumbnail=item.getString("thumbnail");
                if(!safe(entry.image)||!safe(entry.thumbnail))throw new IOException("Invalid catalog path");
                entry.label="魅族壁纸 "+(i+1)+("night".equals(item.optString("variant"))?" · 深色":"");
                entries.add(entry);
            }
        }catch(IOException|JSONException e){Toast.makeText(activity,"无法读取内置壁纸",Toast.LENGTH_LONG).show();return;}
        android.content.Context dialogContext=new ContextThemeWrapper(activity,R.style.Theme_Clean_Dialog);
        GridView grid=new GridView(dialogContext);grid.setNumColumns(3);grid.setHorizontalSpacing(12);grid.setVerticalSpacing(12);grid.setPadding(16,16,16,16);
        BaseAdapter adapter=new BaseAdapter(){
            @Override public int getCount(){return entries.size();}
            @Override public Object getItem(int position){return entries.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View recycled,ViewGroup parent){
                ImageView image=recycled instanceof ImageView?(ImageView)recycled:new ImageView(activity);
                image.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,Math.round(140*activity.getResources().getDisplayMetrics().density)));
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff292929);
                Entry entry=entries.get(position);image.setImageBitmap(entry.bitmap);image.setContentDescription(entry.label);
                return image;
            }
        };
        grid.setAdapter(adapter);
        LinearLayout content=new LinearLayout(dialogContext);content.setOrientation(LinearLayout.VERTICAL);
        content.addView(grid,new LinearLayout.LayoutParams(-1,Math.round(activity.getResources().getDisplayMetrics().heightPixels*.6f)));
        Dialog dialog=dialogs.content("魅族壁纸",content);
        ExecutorService decoder=Executors.newSingleThreadExecutor();
        dialog.setOnDismissListener(d->decoder.shutdownNow());
        grid.setOnItemClickListener((parent,view,position,id)->{String image=entries.get(position).image;dialog.dismiss();selection.choose("wallpapers/"+image);});
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,Math.round(activity.getResources().getDisplayMetrics().heightPixels*0.8f));
        for(Entry entry:entries)decoder.execute(()->{
            if(Thread.currentThread().isInterrupted())return;
            try{
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(InputStream in=activity.getAssets().open("wallpapers/"+entry.thumbnail)){BitmapFactory.decodeStream(in,null,options);}
                options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>320)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;
                Bitmap bitmap;
                try(InputStream in=activity.getAssets().open("wallpapers/"+entry.thumbnail)){bitmap=BitmapFactory.decodeStream(in,null,options);}
                activity.runOnUiThread(()->{if(dialog.isShowing()){entry.bitmap=bitmap;adapter.notifyDataSetChanged();}else if(bitmap!=null)bitmap.recycle();});
            }catch(IOException e){android.util.Log.e("AliveClean","Wallpaper thumbnail failed",e);}
        });
    }

    private static boolean safe(String path){return !path.startsWith("/")&&!path.contains("..")&&!path.contains("\\")&&path.matches("[A-Za-z0-9_./-]+");}
}
