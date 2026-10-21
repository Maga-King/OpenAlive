package org.aliveclean;

import android.content.Context;
import android.content.pm.*;
import android.content.res.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import java.io.*;
import java.util.*;

/** Original per-stage thumbnails; miniature cards never start extra native players. */
final class XiaomiHomePreview {
    static final class Card {
        final XiaomiPacks.Pack pack;Bitmap image;
        Card(XiaomiPacks.Pack pack){this.pack=pack;}
    }
    static Bundle metadata(Context c,File apk)throws IOException {
        PackageInfo archive=c.getPackageManager().getPackageArchiveInfo(apk.getPath(),PackageManager.GET_SERVICES|PackageManager.GET_META_DATA);
        if(archive!=null&&archive.services!=null)for(ServiceInfo s:archive.services)
            if(!s.name.contains("Preview")&&s.metaData!=null&&s.metaData.getBoolean("is_super_wallpaper"))return s.metaData;
        throw new IOException("Original wallpaper metadata missing");
    }
    static int resource(Bundle metadata,int mode,int land,boolean dark){
        String name=mode==0?"aod_small_preview":mode==1?"lockscreen_small_preview":"home_small_preview_"+land;
        int normal=metadata.getInt(name);int night=metadata.getInt(name+"_dark");return dark&&night!=0?night:normal;
    }
    // Called off the main thread. Verify each shared lock/home pack only once.
    static Card[] load(Context c)throws Exception {
        Card[] cards=new Card[3];Map<String,File> files=new HashMap<>();Map<String,Resources> resources=new HashMap<>();Map<String,Bundle> manifests=new HashMap<>();
        XiaomiPacks.Pack lock=XiaomiState.selected(c,1),home=XiaomiState.selected(c,2);
        boolean dark=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        for(int mode=0;mode<3;mode++){
            XiaomiPacks.Pack pack=mode==2?home:lock;if(pack==null)continue;
            Card card=cards[mode]=new Card(pack);
            try{
                if(!files.containsKey(pack.id)){
                    File apk=XiaomiPacks.find(c,pack);if(apk==null)throw new IOException("Wallpaper pack unavailable");
                    files.put(pack.id,apk);resources.put(pack.id,XiaomiPacks.resources(c,pack,apk));manifests.put(pack.id,metadata(c,apk));
                }
                int id=resource(manifests.get(pack.id),mode,XiaomiPacks.land(c,pack),dark);
                if(id==0)throw new IOException("Original stage thumbnail missing");
                BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inJustDecodeBounds=true;
                BitmapFactory.decodeResource(resources.get(pack.id),id,options);
                options.inSampleSize=1;while(options.outWidth/options.inSampleSize>640)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;card.image=BitmapFactory.decodeResource(resources.get(pack.id),id,options);
                if(card.image==null)throw new IOException("Original stage thumbnail unreadable");
            }catch(Exception error){android.util.Log.w("OpenAliveXiaomi","Home preview "+pack.id+"/"+mode,error);}
        }
        return cards;
    }
    static final class Clock extends View {
        private final XiaomiAodPreview.Renderer renderer;
        private final ClockUpdates updates;
        private final int width,height;
        Clock(Context c,Bundle metadata)throws Exception {
            super(c);android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();c.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
            width=metrics.widthPixels;height=metrics.heightPixels;renderer=new XiaomiAodPreview.Renderer(c,metadata);
            renderer.whenBatteryChanged(this::invalidate);
            updates=new ClockUpdates(c,true,time->{try{renderer.update();invalidate();}catch(Exception error){android.util.Log.w("OpenAliveXiaomi","Home clock refresh",error);}});
        }
        void active(boolean value){renderer.active(value);if(value)updates.start();else updates.stop();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(getWidth()==0||getHeight()==0)return;
            renderer.view.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
            renderer.view.layout(0,0,width,height);int save=canvas.save();canvas.scale(getWidth()/(float)width,getHeight()/(float)height);renderer.view.draw(canvas);canvas.restoreToCount(save);
        }
        @Override protected void onDetachedFromWindow(){renderer.active(false);updates.stop();super.onDetachedFromWindow();}
    }
}
