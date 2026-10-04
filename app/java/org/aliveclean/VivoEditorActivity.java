package org.aliveclean;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Vivo wallpaper editor; system clocks, widgets and shortcuts remain system-owned. */
public final class VivoEditorActivity extends Activity implements TextureView.SurfaceTextureListener {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(I18n.wrap(base));}
    private VivoOptions options;
    private VivoEngineRuntime runtime;
    private VivoUi ui;
    private RenderLoop renderer;
    private Surface surface;
    private TextureView preview;
    private FrameLayout root;
    private TextView apply,status;
    private final PhotoViewport crop=new PhotoViewport();
    private int imageWidth=1080,imageHeight=2400,previousId;
    private boolean busy,awaitingApply,resumed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Dialog panel;
    private Intent pendingMedia;
    private int pendingMediaRequest;
    private final Runnable saveCrop=()->saveDraft();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);
        getWindow().getDecorView().setSystemUiVisibility(0);
        int family=getIntent().getIntExtra("family",1);if(family<1||family>3)family=1;
        options=VivoOptions.parse(state==null?getSharedPreferences("vivo.editor",0).getString("family."+family,""):state.getString("options"));
        if(state!=null){pendingMedia=state.getParcelable("pendingMedia");pendingMediaRequest=state.getInt("pendingMediaRequest");}
        if(options==null||options.family!=family)options=new VivoOptions(family);
        new SceneOptions(getSharedPreferences(SceneOptions.APPLIED,0)).save(getSharedPreferences(SceneOptions.DRAFT,0));saveDraft();
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        VivoUi.fitSystemBars(root);
        status=new TextView(this);status.setTextColor(-1);status.setGravity(Gravity.CENTER);status.setText(I18n.t("正在加载壁纸"));root.addView(status,new FrameLayout.LayoutParams(-1,-1));
        new Thread(()->{try{VivoEngineRuntime ready=VivoEngineRuntime.prepare(this).get();runOnUiThread(()->{if(!isDestroyed()){runtime=ready;showEditor();loadImageSize();}});}
            catch(Exception e){runOnUiThread(()->status.setText(I18n.t("无法加载壁纸：")+e.getMessage()));}},"VivoEditorResources").start();
    }
    private void showEditor(){
        ui=new VivoUi(this,runtime);root.removeAllViews();
        LinearLayout column=new LinearLayout(this);column.setOrientation(1);root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout header=new FrameLayout(this);column.addView(header,new LinearLayout.LayoutParams(-1,ui.dp(54)));
        TextView cancel=button(I18n.t("取消"));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(ui.dp(84),ui.dp(36),Gravity.START|Gravity.CENTER_VERTICAL);cp.leftMargin=ui.dp(18);header.addView(cancel,cp);cancel.setOnClickListener(v->finish());
        apply=button(I18n.t("应用"));FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(ui.dp(84),ui.dp(36),Gravity.END|Gravity.CENTER_VERTICAL);ap.rightMargin=ui.dp(18);header.addView(apply,ap);apply.setOnClickListener(v->applyWallpaper());
        TextView scene=ui.text(I18n.t("锁屏"),18);column.addView(scene,new LinearLayout.LayoutParams(-1,ui.dp(36)));
        FrameLayout previewSlot=new FrameLayout(this){@Override protected void onMeasure(int ws,int hs){super.onMeasure(ws,hs);int maxW=getMeasuredWidth()-ui.dp(74),maxH=getMeasuredHeight()-ui.dp(10);float aspect=(float)getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().heightPixels;int w=Math.min(maxW,Math.round(maxH*aspect)),h=Math.min(maxH,Math.round(w/aspect));if(getChildCount()>0)getChildAt(0).measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));}};
        column.addView(previewSlot,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout frame=new FrameLayout(this);GradientDrawable outline=new GradientDrawable();outline.setColor(Color.BLACK);outline.setCornerRadius(ui.dp(28));outline.setStroke(ui.dp(1),0xff444444);frame.setBackground(outline);frame.setClipToOutline(true);
        previewSlot.addView(frame,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        preview=new TextureView(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));preview.setSurfaceTextureListener(this);installGestures();
        TextView reset=ui.text(I18n.t("复位"),13);reset.setBackground(ui.capsule());FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(ui.dp(56),ui.dp(30),Gravity.BOTTOM|Gravity.END);rp.setMargins(0,0,ui.dp(12),ui.dp(12));frame.addView(reset,rp);
        reset.setOnClickListener(v->{crop.reset();updateCrop();saveDraft();});
        TextView hint=ui.text(I18n.t("双指可缩放或移动壁纸"),12);hint.setTextColor(0xffbdbdbd);column.addView(hint,new LinearLayout.LayoutParams(-1,ui.dp(28)));
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER);column.addView(footer,new LinearLayout.LayoutParams(-1,ui.dp(98)));
        if(options.family==VivoOptions.RASTER){footer(footer,I18n.t("图集光栅"),"ic_aod_edit",()->showMediaChoices(false));footer(footer,I18n.t("视频光栅"),"ic_aod_add",()->showMediaChoices(true));footer(footer,I18n.t("效果"),"ic_unlock_decorate",()->showPanel(0));}
        else {footer(footer,I18n.t("壁纸"),"ic_decorate_refresh",()->showMediaChoices(false));footer(footer,I18n.t("装饰"),"ic_unlock_decorate",()->showPanel(0));}
        renderer=new RenderLoop(this,SceneOptions.DRAFT);renderer.visible(resumed);renderer.mode(1,false);
        if(pendingMedia!=null){Intent ready=pendingMedia;int request=pendingMediaRequest;pendingMedia=null;onActivityResult(request,RESULT_OK,ready);}
    }
    private TextView button(String name){TextView t=ui.text(name,16);t.setBackground(ui.official("ic_theme_editer_title_bg"));return t;}
    private void footer(LinearLayout row,String title,String icon,Runnable action){
        LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setGravity(Gravity.CENTER);row.addView(item,new LinearLayout.LayoutParams(ui.dp(104),-1));
        ImageView circle=new ImageView(this);circle.setImageDrawable(ui.official(icon));circle.setScaleType(ImageView.ScaleType.FIT_CENTER);int pad=ui.dp(icon.equals("ic_unlock_decorate")?0:16);circle.setPadding(pad,pad,pad,pad);circle.setBackground(ui.capsule());item.addView(circle,new LinearLayout.LayoutParams(ui.dp(58),ui.dp(58)));
        TextView label=ui.text(title,13);label.setTextColor(0xffbcbcbc);item.addView(label,new LinearLayout.LayoutParams(-1,ui.dp(28)));item.setOnClickListener(v->action.run());
    }
    private void installGestures(){
        ScaleGestureDetector scale=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){crop.scale(d.getScaleFactor(),d.getFocusX()/preview.getWidth(),d.getFocusY()/preview.getHeight());updateCrop();return true;}
        });
        preview.setOnTouchListener(new View.OnTouchListener(){float x,y;boolean dragging;
            public boolean onTouch(View v,MotionEvent e){scale.onTouchEvent(e);if(e.getPointerCount()>=2){if(!dragging&&renderer!=null)renderer.touch(MotionEvent.ACTION_CANCEL,0,0);float nx=(e.getX(0)+e.getX(1))/2,ny=(e.getY(0)+e.getY(1))/2;if(dragging&&e.getActionMasked()==MotionEvent.ACTION_MOVE){crop.drag((nx-x)/preview.getWidth(),(ny-y)/preview.getHeight());updateCrop();}x=nx;y=ny;dragging=true;}else if(!dragging&&renderer!=null)renderer.touch(e.getActionMasked(),e.getX()/preview.getWidth(),e.getY()/preview.getHeight());
                if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;main.removeCallbacks(saveCrop);main.postDelayed(saveCrop,80);}return true;}
        });
    }
    private void updateCrop(){options.x=crop.centerX();options.y=crop.centerY();options.zoom=crop.zoom();if(renderer!=null)renderer.vivoCrop(options.x,options.y,options.zoom);}
    private void loadImageSize(){final VivoOptions selected=VivoOptions.parse(options.json());new Thread(()->{try(VivoImages images=VivoImages.load(this,runtime,selected)){
        int w=images.photos.get(0).getWidth(),h=images.photos.get(0).getHeight();runOnUiThread(()->{if(isDestroyed()||!selected.json().equals(options.json()))return;imageWidth=w;imageHeight=h;configureCrop();});
    }catch(Exception e){runOnUiThread(()->toast(I18n.t("照片加载失败：")+e.getMessage()));}},"VivoPhotoSize").start();}
    private void configureCrop(){if(preview==null||preview.getWidth()==0)return;crop.dimensions(imageWidth,imageHeight,preview.getWidth(),preview.getHeight());crop.restore(options.x,options.y,options.zoom);}
    private void saveDraft(){
        if(options==null)return;
        getSharedPreferences("vivo.editor",0).edit().putString("family."+options.family,options.json()).apply();
        getSharedPreferences(SceneOptions.DRAFT,0).edit().putString("vivo",options.json()).putInt("cosmic",0).putInt("aod",101).putInt("lock",0).putInt("home",6).apply();
    }
    private void showPanel(int tab){
        if(panel!=null)panel.dismiss();panel=new Dialog(this);LinearLayout body=new LinearLayout(ui);body.setOrientation(1);body.setPadding(0,ui.dp(14),0,ui.dp(18));
        GradientDrawable background=new GradientDrawable();background.setColor(0xfffafafa);background.setCornerRadii(new float[]{ui.dp(28),ui.dp(28),ui.dp(28),ui.dp(28),0,0,0,0});body.setBackground(background);
        FrameLayout heading=new FrameLayout(ui);body.addView(heading,new LinearLayout.LayoutParams(-1,ui.dp(48)));
        TextView title=ui.panelText(options.family==VivoOptions.RASTER?I18n.t("光栅效果"):I18n.t("装饰"),20);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new FrameLayout.LayoutParams(-1,-1));
        ImageView close=new ImageView(this);close.setImageDrawable(ui.official("ic_decorate_dialog_close"));close.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));close.setContentDescription(I18n.t("关闭"));FrameLayout.LayoutParams closeLp=new FrameLayout.LayoutParams(ui.dp(48),ui.dp(48),Gravity.END);closeLp.rightMargin=ui.dp(12);heading.addView(close,closeLp);close.setOnClickListener(v->panel.dismiss());
        LinearLayout tabs=new LinearLayout(ui);tabs.setGravity(Gravity.CENTER);body.addView(tabs,new LinearLayout.LayoutParams(-1,ui.dp(44)));
        if(options.family!=VivoOptions.RASTER){tab(tabs,I18n.t("动效范围"),tab==0,()->showPanel(0));tab(tabs,I18n.t("动态效果"),tab==1,()->showPanel(1));}
        else tabs.setVisibility(View.GONE);
        try{
            if(options.family==VivoOptions.RASTER)rasterOptions(body);
            else if(tab==0)areaOptions(body);
            else effectOptions(body);
        }catch(Exception e){TextView error=ui.text(I18n.t("选项加载失败：")+e.getMessage(),14);body.addView(error);android.util.Log.e("AliveClean","Vivo panel failed",e);}
        if(options.family!=VivoOptions.LIQUID)sensitivity(body);
        Switch zoom=new Switch(ui);zoom.setText(I18n.t("息屏与亮屏壁纸缩放"));zoom.setTextColor(0xff111111);zoom.setTextSize(14);zoom.setPadding(ui.dp(24),ui.dp(8),ui.dp(24),ui.dp(8));zoom.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[0]},new int[]{0xff0f77ff,0xffeeeeee}));zoom.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[0]},new int[]{0x660f77ff,0xffcccccc}));zoom.setChecked(options.wallpaperZoom);body.addView(zoom,new LinearLayout.LayoutParams(-1,ui.dp(52)));zoom.setOnCheckedChangeListener((v,on)->{options.wallpaperZoom=on;saveDraft();});
        panel.setContentView(body);Window window=panel.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);panel.show();window.setLayout(-1,-2);
    }
    private void tab(LinearLayout row,String text,boolean selected,Runnable click){
        LinearLayout column=new LinearLayout(ui);column.setOrientation(1);column.setGravity(Gravity.CENTER);row.addView(column,new LinearLayout.LayoutParams(0,-1,1));
        TextView label=ui.panelText(text,17);label.setTextColor(selected?0xff111111:0xff888888);column.addView(label,new LinearLayout.LayoutParams(-1,0,1));
        View underline=new View(ui);underline.setBackgroundColor(selected?0xff0f77ff:Color.TRANSPARENT);column.addView(underline,new LinearLayout.LayoutParams(ui.dp(26),ui.dp(3)));column.setOnClickListener(v->click.run());
    }
    private LinearLayout originalRow(LinearLayout parent){View layout=ui.inflate("flashcard_effect_select_layout",parent);parent.addView(layout);return (LinearLayout)layout.findViewById(ui.id("id","ll_options"));}
    private void option(LinearLayout row,String path,String label,boolean selected,Runnable action)throws Exception{
        LinearLayout item=new LinearLayout(ui);item.setOrientation(1);item.setGravity(Gravity.CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(76),-2);lp.setMargins(ui.dp(6),0,ui.dp(6),0);row.addView(item,lp);
        View tile=ui.inflate("item_flashcard_effect_option",item);item.addView(tile,new LinearLayout.LayoutParams(ui.dp(66),ui.dp(66)));
        ImageView icon=(ImageView)tile.findViewById(ui.id("id","iv_icon"));icon.setImageBitmap(VivoImages.asset(runtime,path));tile.findViewById(ui.id("id","iv_selected")).setVisibility(selected?View.VISIBLE:View.GONE);
        if(label!=null)item.addView(ui.panelText(label,12),new LinearLayout.LayoutParams(-1,ui.dp(28)));item.setOnClickListener(v->action.run());
    }
    private String category(){return options.family==VivoOptions.LIQUID?"liquid":"flash";}
    private void areaOptions(LinearLayout parent)throws Exception{
        LinearLayout row=originalRow(parent);String[]names={I18n.t("自定义"),I18n.t("主体"),I18n.t("背景"),I18n.t("全图")},icons={"custom_paint","main_body","background","full_pic"};
        int tileWidth=Math.min(ui.dp(86),(getResources().getDisplayMetrics().widthPixels-ui.dp(64))/4);
        for(int i=0;i<4;i++){final int area=i+1;View tile=ui.inflate("item_flashcard_interact_option",row);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(tileWidth,ui.dp(130));lp.setMargins(ui.dp(4),ui.dp(6),ui.dp(4),ui.dp(12));row.addView(tile,lp);
            ((ImageView)tile.findViewById(ui.id("id","iv_icon"))).setImageBitmap(VivoImages.asset(runtime,"vivo/ui/"+category()+"/effect_area/light_"+icons[i]+".webp"));
            ((TextView)tile.findViewById(ui.id("id","tv_label"))).setText(names[i]);tile.findViewById(ui.id("id","iv_selected")).setVisibility(options.area==area?View.VISIBLE:View.GONE);
            tile.findViewById(ui.id("id","iv_edit_btn")).setVisibility(area==1&&options.area==1?View.VISIBLE:View.GONE);
            tile.setOnClickListener(v->{
            if(area==1&&!options.photos.isEmpty()&&options.paint.isEmpty()){panel.dismiss();paintMask();return;}
            if((area==2||area==3)&&!options.photos.isEmpty()&&options.subject.isEmpty()){toast(I18n.t("这张照片尚未生成主体蒙版，可先使用自定义涂抹"));return;}
            options.area=area;saveDraft();showPanel(0);
        });}
    }
    private void effectOptions(LinearLayout parent)throws Exception{
        LinearLayout row=originalRow(parent);int count=options.family==VivoOptions.FLASH?5:4;
        for(int i=1;i<=count;i++){final int style=i;String label=options.family==VivoOptions.FLASH?runtime.getString(ui.id("string","flash_style_"+i)):null;
            option(row,"vivo/ui/"+category()+"/effect_type/"+i+".webp",label,options.style==i,()->{options.style=style;saveDraft();showPanel(1);});}
        if(options.family==VivoOptions.LIQUID&&options.style>1){TextView label=ui.panelText(I18n.t("折射"),14);parent.addView(label);SeekBar seek=new SeekBar(ui);seek.setMax(3);seek.setProgress(options.refraction-1);parent.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(user){options.refraction=p+1;main.removeCallbacks(saveCrop);main.postDelayed(saveCrop,90);}}public void onStopTrackingTouch(SeekBar s){saveDraft();}});}
        if(options.family==VivoOptions.FLASH&&options.style==3){
            JSONArray list=new JSONObject(AssetGl.text(runtime.getAssets(),"vivo/ui/flash/panelConfig.json")).getJSONArray("FlashCardEffect").getJSONObject(2).getJSONArray("patternIcons");
            LinearLayout patterns=originalRow(parent);for(int i=0;i<list.length();i++){JSONObject j=list.getJSONObject(i);String name=j.getString("name");option(patterns,"vivo/ui/flash/"+j.getString("darkIcon"),null,name.equals(options.pattern),()->{options.pattern=name;saveDraft();showPanel(1);});}
        }
    }
    private void rasterOptions(LinearLayout parent)throws Exception{
        HorizontalScrollView scroll=new HorizontalScrollView(ui);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(ui);row.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16));scroll.addView(row);parent.addView(scroll);
        VivoCalls api=new VivoCalls(runtime);
        for(int i=1;i<=4;i++){final int style=i;View tile=ui.inflate("raster_trans_list_item_layout",row);row.addView(tile);tile.findViewById(ui.id("id","iv_selected")).setVisibility(options.style==i?View.VISIBLE:View.GONE);
            Object pag=tile.findViewById(ui.id("id","iv_style_img"));Object composition=api.type("org.libpag.PAGFile").getMethod("Load",android.content.res.AssetManager.class,String.class).invoke(null,runtime.getAssets(),"vivo/ui/raster/icon_"+i+".pag");
            api.call(pag,"setComposition",new Class<?>[]{api.type("org.libpag.PAGComposition")},composition);api.call(pag,"setScaleMode",new Class<?>[]{int.class},3);api.call(pag,"setRepeatCount",new Class<?>[]{int.class},1);
            ((View)pag).post(()->{try{api.call(pag,"setCurrentFrame",new Class<?>[]{int.class},0);api.call(pag,"flush");}catch(Exception e){android.util.Log.w("AliveClean","Vivo option preview failed",e);}});
            tile.setContentDescription(I18n.t("光栅效果 ")+i);tile.setOnClickListener(v->{options.style=style;saveDraft();showPanel(0);});}
    }
    private void sensitivity(LinearLayout parent){
        TextView label=ui.panelText(sensitivityLabel(),14);parent.addView(label);
        SeekBar seek=new SeekBar(ui);ui.tintSlider(seek);seek.setMax(100);seek.setProgress(options.sensitivity+50);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(38));lp.setMargins(ui.dp(24),0,ui.dp(24),0);parent.addView(seek,lp);
        seek.setContentDescription(I18n.t("传感器灵敏度，负50至正50，0为Vivo默认"));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean user){if(user){options.sensitivity=p-50;label.setText(sensitivityLabel());main.removeCallbacks(saveCrop);main.postDelayed(saveCrop,120);}}public void onStopTrackingTouch(SeekBar s){main.removeCallbacks(saveCrop);saveDraft();}});
        LinearLayout ends=new LinearLayout(ui);ends.setPadding(ui.dp(26),0,ui.dp(26),0);parent.addView(ends);
        TextView low=ui.text(I18n.t("−50  更缓"),12),high=ui.text(I18n.t("更灵敏  +50"),12);low.setGravity(Gravity.START);high.setGravity(Gravity.END);low.setTextColor(0xffbdbdbd);high.setTextColor(0xffbdbdbd);ends.addView(low,new LinearLayout.LayoutParams(0,-2,1));ends.addView(high,new LinearLayout.LayoutParams(0,-2,1));
        TextView reset=ui.text(I18n.t("恢复默认（0）"),13);reset.setTextColor(0xff70aeff);parent.addView(reset,new LinearLayout.LayoutParams(-1,ui.dp(32)));reset.setOnClickListener(v->{main.removeCallbacks(saveCrop);options.sensitivity=0;seek.setProgress(50);label.setText(sensitivityLabel());saveDraft();});
    }
    private String sensitivityLabel(){return I18n.t("灵敏度  ")+(options.sensitivity>0?"+":"")+options.sensitivity+(options.sensitivity==0?I18n.t("（Vivo 默认）"):"");}
    private void showMediaChoices(boolean video){
        if(busy)return;
        boolean raster=options.family==VivoOptions.RASTER;
        String[] choices=raster?new String[]{I18n.t("恢复默认搭配"),I18n.t("选择封面"),video?I18n.t("选择视频"):I18n.t("选择光栅图片")}:new String[]{I18n.t("恢复默认搭配"),I18n.t("选择壁纸")};
        new AlertDialog.Builder(this).setTitle(raster?(video?I18n.t("视频光栅"):I18n.t("图集光栅")):I18n.t("壁纸"))
            .setItems(choices,(dialog,which)->{
                if(which==0){options=VivoMediaSelection.defaults(options);saveDraft();loadImageSize();return;}
                chooseMedia(!raster?VivoMediaSelection.WALLPAPER:which==1?VivoMediaSelection.COVER:video?VivoMediaSelection.VIDEO:VivoMediaSelection.ALBUM);
            }).setNegativeButton(I18n.t("取消"),null).show();
    }
    private void chooseMedia(int kind){
        try{startActivityForResult(PhotoSources.vivoMedia(this,kind==VivoMediaSelection.VIDEO?"video/*":"image/*",false),kind);}
        catch(ActivityNotFoundException|SecurityException e){toast(I18n.t("无法打开相册，请检查相册是否可用"));}
    }
    private void applyWallpaper(){
        if(busy)return;saveDraft();WallpaperManager manager=WallpaperManager.getInstance(this);android.app.WallpaperInfo info=manager.getWallpaperInfo();
        if(info!=null&&new ComponentName(this,CleanWallpaper.class).equals(info.getComponent())){commit();return;}
        previousId=manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM);awaitingApply=true;
        try{startActivityForResult(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,CleanWallpaper.class)),20);}catch(RuntimeException e){awaitingApply=false;toast(I18n.t("无法打开应用页面：")+e.getMessage());}
    }
    private void commit(){
        if(!new SceneOptions(getSharedPreferences(SceneOptions.DRAFT,0)).save(getSharedPreferences(SceneOptions.APPLIED,0))){toast(I18n.t("保存失败，请重试"));return;}
        SceneProvider.changed(this);busy=true;apply.setEnabled(false);toast(I18n.t("壁纸已应用，正在配置息屏"));
        new Thread(()->{String message;try{message=RootBridge.prepareAod(this);}catch(Exception e){message=I18n.t("壁纸已应用，息屏配置未完成：")+e.getMessage();}final String done=message;runOnUiThread(()->{busy=false;if(!isDestroyed()){apply.setEnabled(true);toast(done);}});},"VivoApply").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==20&&awaitingApply){awaitingApply=false;WallpaperManager m=WallpaperManager.getInstance(this);android.app.WallpaperInfo info=m.getWallpaperInfo();if(info!=null&&new ComponentName(this,CleanWallpaper.class).equals(info.getComponent())&&(result==RESULT_OK||m.getWallpaperId(WallpaperManager.FLAG_SYSTEM)!=previousId))commit();return;}
        if(result!=RESULT_OK||data==null||request<10||request>13||busy)return;
        if(runtime==null||apply==null){pendingMedia=data;pendingMediaRequest=request;return;}
        ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return;
        VivoMediaSelection selection=new VivoMediaSelection(getApplicationContext(),options);
        busy=true;apply.setEnabled(false);
        new Thread(()->{try{
            selection.importUris(runtime,request,uris);
            runOnUiThread(()->{if(isDestroyed()){selection.discard();return;}options=selection.result;saveDraft();loadImageSize();busy=false;apply.setEnabled(true);});
        }catch(Exception e){selection.discard();runOnUiThread(()->{busy=false;if(!isDestroyed()){apply.setEnabled(true);toast(I18n.t("素材导入失败：")+e.getMessage());}});}},"VivoImport").start();
    }
    private void paintMask(){toast(I18n.t("正在接入涂抹编辑器"));}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){surface=new Surface(texture);if(renderer!=null)renderer.attach(surface,width,height);configureCrop();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){if(renderer!=null&&surface!=null)renderer.attach(surface,width,height);configureCrop();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){if(renderer!=null)renderer.detach();if(surface!=null){surface.release();surface=null;}return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
    @Override protected void onResume(){super.onResume();resumed=true;if(renderer!=null)renderer.visible(true);}
    @Override protected void onPause(){resumed=false;if(renderer!=null)renderer.visible(false);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("options",options.json());if(pendingMedia!=null){state.putParcelable("pendingMedia",pendingMedia);state.putInt("pendingMediaRequest",pendingMediaRequest);}}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);if(panel!=null)panel.dismiss();if(renderer!=null)renderer.close();super.onDestroy();}
}
