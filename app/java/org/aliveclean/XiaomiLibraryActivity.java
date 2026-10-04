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

/** Original Xiaomi list shell and row widgets. Only the local data source is replaced. */
public final class XiaomiLibraryActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(I18n.wrap(base));}
    private XiaomiUi ui; private boolean busy;
    private int openRequest; private ProgressDialog preparing;
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        new Thread(()->{try{XiaomiUi.bundle(this);runOnUiThread(()->{if(!isDestroyed())showList();});}catch(Exception e){runOnUiThread(()->failure(e));}},"XiaomiUiLoad").start();
    }
    private void showList(){try{
        ui=new XiaomiUi(this);ui.theme.applyStyle(ui.id("style","superWallpaperList.NoBlur"),true);
        TypedArray bg=ui.obtainStyledAttributes(new int[]{android.R.attr.windowBackground});getWindow().setBackgroundDrawable(bg.getDrawable(0));bg.recycle();
        View shell=ui.inflate("miuix_appcompat_screen_action_bar"),list=ui.inflate("activity_super_wallpaper_list");
        ((ViewGroup)shell.findViewById(android.R.id.content)).addView(list,new ViewGroup.LayoutParams(-1,-1));
        View bar=ui.find(shell,"action_bar");XiaomiUi.call(bar,"setDisplayOptions",new Class[]{int.class},12);
        XiaomiUi.call(bar,"setTitle",new Class[]{CharSequence.class},ui.getString(ui.id("string","super_wallpaper_list_label")));
        XiaomiUi.call(bar,"setExpandState",new Class[]{int.class},2);XiaomiUi.call(bar,"setWindowCallback",new Class[]{Window.Callback.class},this);
        View recycler=ui.find(list,"super_wallpaper_list");Class<?> lm=ui.loader.loadClass("androidx.recyclerview.widget.RecyclerView$LayoutManager");
        Object layout=ui.loader.loadClass("androidx.recyclerview.widget.LinearLayoutManager").getConstructor(Context.class,int.class,boolean.class).newInstance(ui,1,false);
        XiaomiUi.call(recycler,"setLayoutManager",new Class[]{lm},layout);
        List<XiaomiPacks.Pack> packs=XiaomiPacks.all(this);
        java.util.function.BiConsumer<View,Integer> binder=(v,index)->{try{
            XiaomiPacks.Pack pack=packs.get(index);Drawable image;try(InputStream in=getAssets().open("xiaomi/"+pack.id+".banner")){image=Drawable.createFromStream(in,pack.id);}
            final Drawable banner=image;Class<?> fn=ui.loader.loadClass("kotlin.jvm.functions.Function1");Object unit=ui.loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
            Object setImage=Proxy.newProxyInstance(ui.loader,new Class[]{fn},(o,m,a)->{if(m.getName().equals("invoke"))((ImageView)a[0]).setImageDrawable(banner);return unit;});
            Object click=Proxy.newProxyInstance(ui.loader,new Class[]{fn},(o,m,a)->{if(m.getName().equals("invoke"))open(pack);return unit;});
            XiaomiUi.call(v,"k",new Class[]{boolean.class,boolean.class,boolean.class,fn,fn,String.class,String.class},false,false,true,setImage,click,I18n.t(pack.title),"");
            Class<?> complete=ui.loader.loadClass("com.android.thememanager.settings.superwallpaper.widget.DownloadViewHolderInterface$Companion$DownloadState$Complete");Object singleton=null;
            for(Field f:complete.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==complete){f.setAccessible(true);singleton=f.get(null);}
            XiaomiUi.call(v,"toq",new Class[]{complete.getSuperclass()},singleton);
        }catch(Exception e){failure(e);}};
        Object adapter=ui.loader.loadClass("org.aliveclean.XiaomiListAdapter").getConstructor(Context.class,int.class,int.class,java.util.function.BiConsumer.class).newInstance(ui,ui.id("layout","super_wallpaper_item_apk"),packs.size(),binder);
        XiaomiUi.call(recycler,"setAdapter",new Class[]{ui.loader.loadClass("androidx.recyclerview.widget.RecyclerView$Adapter")},adapter);setContentView(shell);
    }catch(Exception e){failure(e);}}
    private void open(XiaomiPacks.Pack pack){if(busy)return;busy=true;
        int request=++openRequest;
        main.postDelayed(()->{if(request!=openRequest||!busy||isFinishing()||isDestroyed())return;
            preparing=new ProgressDialog(this);preparing.setMessage(I18n.t("正在准备")+I18n.t(pack.title)+I18n.t("，首次使用请稍候…"));preparing.setIndeterminate(true);
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
    private void failure(Exception e){String reason=XiaomiFailure.describe(this,"Xiaomi catalog",e);android.util.Log.e("OpenAliveXiaomi","Xiaomi catalog",e);if(!isDestroyed())new AlertDialog.Builder(this).setTitle(I18n.t("小米壁纸暂时无法打开")).setMessage(reason).setPositiveButton(I18n.t("确定"),null).show();}
}
