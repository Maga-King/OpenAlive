package org.aliveclean;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;

/** Original Flyme notification layout, with OpenAlive settings and owned previews. */
public final class NotificationSettingsActivity extends Activity {
    private OfficialUi ui;
    private OfficialDialogs dialogs;
    private SharedPreferences preferences;
    private FrameLayout root,preview;
    private LinearLayout panel;
    private TextView option;
    private TextView previewAction;
    private final View[] cards=new View[5];
    private FlymeNotificationLight light;
    private HomeScenePreview aod;
    private int generation;
    private boolean resumed;
    private static final int[] COLORS={NotificationOptions.RING_BLUE,0xffe42d22,0xffffc45c};
    private static final String[] COLOR_NAMES={"Flyme 蓝","红色","金色","自定义"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            ui=new OfficialUi(this,true);dialogs=new OfficialDialogs(ui);
            preferences=NotificationOptions.preferences(this);
            root=(FrameLayout)inflatePage();root.setBackgroundColor(Color.BLACK);setContentView(root);
            preview=(FrameLayout)ui.find(root,"aod_preview");panel=(LinearLayout)ui.find(root,"effect_panel");
            for(String id:new String[]{"breathing_ring","breathing_edge_left","breathing_edge_right"})ui.find(root,id).setVisibility(View.GONE);
            ImageView back=(ImageView)ui.find(root,"back_icon");back.setColorFilter(Color.WHITE);back.setContentDescription("返回");back.setOnClickListener(v->finish());
            bind(0,"item_effect_none","iv_none","跟随系统");
            bind(1,"item_effect_screen_on","iv_screen_on","短暂显示息屏");
            bind(3,"item_effect_breathing_ring","iv_breathing_ring",null);
            bind(4,"item_effect_breathing_edge","iv_breathing_edge",null);
            View source=inflatePage();View extra=ui.find(source,"item_effect_breathing_edge");
            ((ViewGroup)extra.getParent()).removeView(extra);
            ((ViewGroup)cards[4].getParent()).addView(extra);cards[2]=extra;
            firstText(extra).setText("系统光效");extra.setContentDescription("系统光效");corners((ImageView)ui.find(extra,"iv_breathing_edge"));
            extra.setOnClickListener(v->select(2));
            option=new TextView(ui);option.setTextSize(14);option.setTextColor(0xffcccccc);
            option.setPadding(dp(18),dp(8),dp(18),dp(18));option.setMinHeight(dp(48));panel.addView(option);
            option.setOnClickListener(v->options());preview.setOnClickListener(v->replay());
            previewAction=new TextView(ui);previewAction.setText("预览 Flyme 光效");previewAction.setTextSize(14);previewAction.setTextColor(NotificationOptions.RING_BLUE);
            previewAction.setPadding(dp(18),dp(8),dp(18),dp(16));previewAction.setMinHeight(dp(48));panel.addView(previewAction);previewAction.setOnClickListener(v->replay());
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets nav=insets.getInsets(WindowInsets.Type.navigationBars());panel.setPadding(0,0,0,nav.bottom);return insets;
            });
            Window window=getWindow();window.setStatusBarColor(Color.BLACK);window.setNavigationBarColor(Color.BLACK);
            WindowManager.LayoutParams lp=window.getAttributes();lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;window.setAttributes(lp);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            refresh();
        }catch(Exception error){SettingsScreen.fail(this,error);}
    }
    private View inflatePage(){
        LayoutInflater inflater=LayoutInflater.from(ui).cloneInContext(ui);
        // Preserve XML and controls; replace only the original automatic SVGA
        // loaders. Our preview uses verified camera geometry and bounded ownership.
        inflater.setFactory2(new LayoutInflater.Factory2(){
            public View onCreateView(View parent,String name,android.content.Context context,AttributeSet attrs){return onCreateView(name,context,attrs);}
            public View onCreateView(String name,android.content.Context context,AttributeSet attrs){return name.equals("com.opensource.svgaplayer.SVGAImageView")?new FrameLayout(context,attrs):null;}
        });
        return inflater.inflate(ui.id("layout","activity_notification_settings"),null,false);
    }
    private void bind(int mode,String item,String image,String label)throws Exception {
        View card=ui.find(root,item);cards[mode]=card;if(label!=null)firstText(card).setText(label);
        corners((ImageView)ui.find(root,image));card.setContentDescription(firstText(card).getText());card.setOnClickListener(v->select(mode));
    }
    private void corners(ImageView image)throws Exception {
        float radius=ui.getResources().getDimension(ui.id("dimen","notification_effect_item_corner_radius"));
        image.getClass().getMethod("a",float.class,float.class).invoke(image,radius,radius);
    }
    private void select(int mode){preferences.edit().putInt("mode",mode).apply();SceneProvider.changed(this);refresh();replay();}
    private int mode(){return NotificationOptions.mode(preferences.getInt("mode",0));}
    private void refresh(){
        int mode=mode();for(int i=0;i<cards.length;i++)if(cards[i]!=null)selected(cards[i],i==mode);
        if(mode==1)option.setText("显示时长："+NotificationPulseWindow.seconds(preferences.getInt("seconds",10))+" 秒  ›");
        else if(mode==3)option.setText("光环颜色："+colorName(preferences.getInt("ring_color",NotificationOptions.RING_BLUE))+"  ›");
        else if(mode==2)option.setText("系统光效颜色  ›");
        else if(mode==4)option.setText("轻点预览区域重播");
        else option.setText("沿用系统的通知提醒设置");
        option.setEnabled(mode!=0);
        previewAction.setVisibility(mode==3||mode==4?View.VISIBLE:View.GONE);
    }
    private static void selected(View view,boolean selected){view.setSelected(selected);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)selected(((ViewGroup)view).getChildAt(i),selected);}
    private void options(){
        int mode=mode();
        if(mode==1)dialogs.choices("息屏显示时长",new String[]{"5 秒","10 秒","15 秒"},(d,i)->saveInt("seconds",new int[]{5,10,15}[i])).show();
        else if(mode==3)dialogs.choices("光环颜色",COLOR_NAMES,(d,i)->{if(i<COLORS.length)saveInt("ring_color",COLORS[i]);else customColor();}).show();
        else if(mode==2)dialogs.choices("系统光效颜色",new String[]{"蓝色","红色","金色"},(d,i)->{preferences.edit().putString("color",new String[]{"blue","red","gold"}[i]).apply();SceneProvider.changed(this);refresh();}).show();
        else replay();
    }
    private void customColor(){
        LinearLayout content=new LinearLayout(ui);content.setOrientation(1);content.setPadding(dp(20),dp(4),dp(20),dp(12));
        EditText input=new EditText(ui);input.setSingleLine();input.setTextColor(Color.WHITE);input.setHintTextColor(0xff999999);input.setHint("#RRGGBB");
        input.setText(String.format(java.util.Locale.ROOT,"#%06X",preferences.getInt("ring_color",NotificationOptions.RING_BLUE)&0xffffff));content.addView(input);
        TextView apply=new TextView(ui);apply.setText("应用颜色");apply.setTextColor(NotificationOptions.RING_BLUE);apply.setTextSize(16);apply.setGravity(Gravity.CENTER);apply.setPadding(0,dp(16),0,dp(16));content.addView(apply);
        Dialog dialog=dialogs.content("自定义光环颜色",content);
        apply.setOnClickListener(v->{String value=input.getText().toString().trim();if(!value.startsWith("#"))value="#"+value;
            if(!value.matches("#[0-9a-fA-F]{6}")){input.setError("请输入六位颜色，例如 #1F7FFB");return;}
            saveInt("ring_color",Color.parseColor(value));dialog.dismiss();});
        dialog.show();
    }
    private void saveInt(String key,int value){preferences.edit().putInt(key,value).apply();SceneProvider.changed(this);refresh();replay();}
    private void stopPreview(){generation++;if(light!=null){light.stop();light=null;}if(aod!=null){aod.stop();aod=null;}if(preview!=null){preview.animate().cancel();preview.removeAllViews();preview.setAlpha(1f);}}
    private void replay(){
        stopPreview();if(!resumed||preview==null)return;
        int mode=mode();final int ticket=generation;
        if(mode==1){
            aod=new HomeScenePreview(this,0);preview.addView(aod,new FrameLayout.LayoutParams(-1,-1));aod.start();
            preview.setAlpha(0f);preview.animate().alpha(1).setDuration(300).start();
            preview.postDelayed(()->{if(ticket==generation&&resumed)preview.animate().alpha(0).setDuration(300).withEndAction(()->{if(ticket==generation&&aod!=null)aod.stop();}).start();},NotificationPulseWindow.seconds(preferences.getInt("seconds",10))*1000L);
        }else if(mode==3||mode==4){
            final boolean ring=mode==3;
            new Thread(()->{
                try{
                    FlymeNotificationLight.Model model=FlymeNotificationLight.load(this,ring);
                    runOnUiThread(()->{
                        if(ticket!=generation||!resumed)return;
                        try{
                            light=new FlymeNotificationLight(this,model,ring);light.setRingColor(preferences.getInt("ring_color",NotificationOptions.RING_BLUE));
                            preview.addView(light,new FrameLayout.LayoutParams(-1,-1));
                            preview.post(()->{
                                if(ticket!=generation||light==null||!resumed)return;
                                try{Point size=new Point();getDisplay().getRealSize(size);int[] xy=new int[2];preview.getLocationOnScreen(xy);
                                    light.layoutForDisplay(size.x,size.y,getDisplay().getCutout(),xy[0],xy[1]);playAgain(ticket);
                                }catch(Exception error){stopPreview();Toast.makeText(this,"光效预览暂不可用",Toast.LENGTH_SHORT).show();}
                            });
                        }catch(Exception error){stopPreview();Toast.makeText(this,"光效预览暂不可用",Toast.LENGTH_SHORT).show();}
                    });
                }catch(Exception error){runOnUiThread(()->{if(ticket==generation&&resumed)Toast.makeText(this,"光效资源无法读取",Toast.LENGTH_SHORT).show();});}
            },"NotificationPreview").start();
        }
    }
    private void playAgain(int ticket){if(ticket==generation&&resumed&&light!=null)light.play(()->preview.postDelayed(()->playAgain(ticket),700));}
    @Override protected void onResume(){super.onResume();resumed=true;if(root!=null)root.post(this::replay);}
    @Override protected void onPause(){resumed=false;stopPreview();super.onPause();}
    @Override protected void onDestroy(){stopPreview();if(dialogs!=null)dialogs.close();if(ui!=null)ui.close();super.onDestroy();}
    private static String colorName(int color){for(int i=0;i<COLORS.length;i++)if(COLORS[i]==color)return COLOR_NAMES[i];return "自定义";}
    private static TextView firstText(View view){if(view instanceof TextView)return (TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView text=firstText(group.getChildAt(i));if(text!=null)return text;}}return null;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
