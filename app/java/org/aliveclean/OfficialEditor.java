package org.aliveclean;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;

/** Host bindings for the original editor layouts and controls in the pinned ROM. */
final class OfficialEditor implements AutoCloseable {
    interface Actions {
        void scene(int scene);
        void choose(String key,int value);
        void photo();
        void crop();
        void example();
        void follow();
        void apply();
        default void keepLock(boolean value){}
        default void continuous(String key,boolean value){}
    }
    private final Activity activity;
    private final OfficialUi ui;
    final OfficialDialogs dialogs;
    private final Actions actions;
    final ViewGroup root;
    final TextureView preview;
    private final View card,tabView;
    private final EditorClockView clock;
    private final View[] bars=new View[3];
    private final Button apply;
    private final View tabs;
    private final Panel aodPanel,effectPanel;
    private final HashMap<String,Drawable.ConstantState> thumbnails=new HashMap<>();
    private SceneOptions options;
    private int mode,topInset,bottomInset;
    private boolean selecting,busy,closed;
    private Panel visiblePanel;
    private final LinearLayout cosmicControls;
    private final View cosmicSettings;
    private final CompoundButton keepLockSwitch;
    private final CompoundButton cosmicAodSwitch,cosmicHomeSwitch;
    private boolean bindingKeepLock;

    OfficialEditor(Activity activity,Actions actions,TextureView.SurfaceTextureListener listener)throws Exception {
        this.activity=activity;this.actions=actions;ui=new OfficialUi(activity);dialogs=new OfficialDialogs(ui);
        root=(ViewGroup)ui.inflate("activity_editor",null);
        root.setBackgroundColor(0xff000000);
        // The original service draws into this placeholder. Our TextureView owns
        // the existing preview renderer, so no second Surface/BufferQueue is needed.
        for(int i=root.getChildCount()-1;i>=0;i--)if(root.getChildAt(i) instanceof SurfaceView)root.removeViewAt(i);
        FrameLayout wallpaper=(FrameLayout)find("fl_wallpaper_preview_container");
        preview=new TextureView(activity);preview.setSurfaceTextureListener(listener);
        wallpaper.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        clock=new EditorClockView(activity,null);
        ((FrameLayout)find("sysui_legacy_aod_preview_container")).addView(clock,new FrameLayout.LayoutParams(-1,-1));
        card=find("editor_container");
        apply=(Button)find("btn_apply");apply.setOnClickListener(v->actions.apply());
        find("btn_cancel").setOnClickListener(v->activity.finish());
        tabView=ui.inflate("view_editor_tab",(ViewGroup)find("fl_tab_container"));
        ((ViewGroup)find("fl_tab_container")).addView(tabView,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        tabs=ui.find(tabView,"tab_group");
        Class<?> tabListener=Class.forName("Z3.d",true,ui.getClassLoader());
        Object selected=Proxy.newProxyInstance(ui.getClassLoader(),new Class<?>[]{tabListener},(object,method,args)->{
            if(method.getDeclaringClass()==Object.class){
                if(method.getName().equals("hashCode"))return System.identityHashCode(object);
                if(method.getName().equals("equals"))return object==args[0];
                return "Editor scene selection";
            }
            if(!selecting){closePanel();actions.scene((Integer)call(tabs,"getSelectedTabPosition",new Class<?>[0]));}
            return null;
        });
        call(tabs,"setOnTabSelectedListener",new Class<?>[]{tabListener},selected);
        bars[0]=add("fl_aod_button_container","editor_aod_button");
        bars[1]=add("fl_lockscreen_button_container","editor_lockscreen_button_photo_wp_legacy_sysui");
        bars[2]=add("fl_launcher_button_container","editor_launcher_button_photo_wp_legacy_sysui");
        aodPanel=new Panel(true);effectPanel=new Panel(false);
        // Alive always includes AOD. Remove the original system-wide switch and
        // its divider; this editor never changes the user's global AOD switch.
        View switchView=ui.find(bars[0],"aod_switch");
        ViewGroup switchRow=(ViewGroup)switchView.getParent();
        switchRow.removeViewAt(1);switchRow.removeView(switchView);
        cosmicControls=new LinearLayout(activity);cosmicControls.setOrientation(LinearLayout.VERTICAL);
        keepLockSwitch=toggle(cosmicControls,"桌面保持锁屏效果");
        cosmicAodSwitch=toggle(cosmicControls,"息屏动画持续播放");
        cosmicHomeSwitch=toggle(cosmicControls,"锁屏与桌面动画持续播放");
        View settingsBar=ui.inflate("editor_lockscreen_button_photo_wp_legacy_sysui",null);
        cosmicSettings=ui.find(settingsBar,"btn_alive_texture");
        ((ViewGroup)cosmicSettings.getParent()).removeView(cosmicSettings);
        ((TextView)ui.find(cosmicSettings,"tv_alive_texture")).setText("动画设置");
        ((FrameLayout)find("fl_button_container")).addView(cosmicSettings,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        cosmicSettings.setVisibility(View.GONE);
        cosmicSettings.setOnClickListener(v->{
            if(busy)return;
            if(cosmicControls.getParent()!=null)((ViewGroup)cosmicControls.getParent()).removeView(cosmicControls);
            dialogs.content("动画设置",cosmicControls).show();
        });
        keepLockSwitch.setOnCheckedChangeListener((button,checked)->{if(!bindingKeepLock&&!busy&&options!=null&&options.cosmic!=0)actions.keepLock(checked);});
        cosmicAodSwitch.setOnCheckedChangeListener((button,checked)->{if(!bindingKeepLock&&!busy&&options!=null&&options.cosmic!=0)actions.continuous("cosmic_continuous_aod",checked);});
        cosmicHomeSwitch.setOnCheckedChangeListener((button,checked)->{if(!bindingKeepLock&&!busy&&options!=null&&options.cosmic!=0)actions.continuous("cosmic_continuous_home",checked);});
        click(bars[0],"aod_style",()->open(aodPanel));
        // Notification routing remains the native ColorOS setting; this button
        // has no portable notification-style backend yet.
        ui.find(bars[0],"aod_notification_switch").setVisibility(View.GONE);
        click(bars[1],"btn_wallpaper_picker",actions::photo);
        click(bars[1],"btn_alive_texture",()->open(effectPanel));
        ui.find(bars[1],"btn_image_filter").setVisibility(View.GONE);
        click(bars[2],"btn_image_picker",actions::photo);
        click(bars[2],"btn_following_lockscreen",actions::follow);
        click(bars[2],"btn_alive_effect",()->open(effectPanel));
        ui.find(bars[2],"btn_solid_color").setVisibility(View.GONE);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            topInset=insets.getSystemWindowInsetTop();bottomInset=insets.getSystemWindowInsetBottom();
            for(String id:new String[]{"btn_cancel","btn_apply"}){
                View button=find(id);ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)button.getLayoutParams();
                lp.topMargin=dimension("editor_top_button_margin_top")+topInset;button.setLayoutParams(lp);
            }
            View bar=find("fl_button_container");ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)bar.getLayoutParams();
            lp.bottomMargin=dp(48)+bottomInset;bar.setLayoutParams(lp);
            aodPanel.sheet.setPadding(0,0,0,bottomInset);effectPanel.sheet.setPadding(0,0,0,bottomInset);
            fit();return insets;
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->fit());
        activity.setContentView(root);
    }

    private View find(String name){return ui.find(root,name);}
    private CompoundButton toggle(LinearLayout parent,String label){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(4),dp(16),dp(4));
        TextView text=new TextView(ui);text.setText(label);text.setTextColor(-1);text.setTextSize(15);
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        View panel=ui.inflate("view_sysui_aod_plugin_panel",null);
        CompoundButton button=(CompoundButton)ui.find(panel,"switch_sync_lockscreen_plugins");
        ((ViewGroup)button.getParent()).removeView(button);button.setContentDescription(label);
        row.addView(button,new LinearLayout.LayoutParams(-2,dp(44)));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return button;
    }
    private int dp(int n){return Math.round(n*ui.getResources().getDisplayMetrics().density);}
    private int dimension(String name){return ui.getResources().getDimensionPixelSize(ui.id("dimen",name));}
    private View add(String container,String layout){
        FrameLayout parent=(FrameLayout)find(container);View view=ui.inflate(layout,parent);
        parent.addView(view,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER_HORIZONTAL));return view;
    }
    private void click(View view,String id,Runnable action){ui.find(view,id).setOnClickListener(v->{if(!busy)action.run();});}
    private static Object call(Object target,String name,Class<?>[] types,Object...args){
        try{return target.getClass().getMethod(name,types).invoke(target,args);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Official UI binding: "+name,e);}
    }
    void update(SceneOptions chosen,int scene,boolean importing){
        options=chosen;mode=scene;busy=importing;
        selecting=true;
        try{
            Object tab=call(tabs,"h",new Class<?>[]{int.class},mode);
            call(tabs,"l",new Class<?>[]{tab.getClass(),boolean.class},tab,true);
        }finally{selecting=false;}
        for(int i=0;i<3;i++)bars[i].setVisibility(chosen.cosmic==0&&i==mode?View.VISIBLE:View.GONE);
        if(chosen.cosmic!=0)closePanel();
        cosmicSettings.setVisibility(chosen.cosmic!=0?View.VISIBLE:View.GONE);cosmicSettings.setEnabled(!busy);
        bindingKeepLock=true;keepLockSwitch.setChecked(chosen.cosmicKeepLock);
        cosmicAodSwitch.setChecked(chosen.cosmicContinuousAod);cosmicHomeSwitch.setChecked(chosen.cosmicContinuousHome);
        bindingKeepLock=false;setEnabled(cosmicControls,!busy);
        apply.setEnabled(!busy);
        for(View bar:bars)setEnabled(bar,!busy);
        clock.scene(chosen.aod==1&&mode==0);
        ui.find(bars[2],"btn_following_lockscreen").setVisibility(chosen.pairedFrame()||chosen.cosmic!=0?View.GONE:View.VISIBLE);
        ImageView following=(ImageView)ui.find(bars[2],"iv_following_lockscreen");
        following.setImageDrawable(ui.getDrawable(ui.id("drawable","ic_wallpaper_picker")));following.setSelected(chosen.followLock);
        ui.find(bars[1],"btn_alive_texture").setVisibility(chosen.cosmic==0?View.VISIBLE:View.GONE);
        ui.find(bars[2],"btn_alive_effect").setVisibility(chosen.cosmic==0?View.VISIBLE:View.GONE);
        ui.find(bars[2],"split_line").setVisibility(chosen.cosmic==0?View.VISIBLE:View.GONE);
        // Keep the official photo button but make the shared-photo destination explicit.
        ((TextView)ui.find(bars[1],"tv_wallpaper_picker")).setText(chosen.pairedFrame()?"锁屏与桌面照片":"壁纸");
        if(visiblePanel!=null)visiblePanel.refresh();
        fit();
    }
    private static void setEnabled(View view,boolean enabled){
        view.setEnabled(enabled);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)setEnabled(group.getChildAt(i),enabled);}
    }
    private void fit(){
        int height=root.getHeight();if(height==0)return;
        float top=topInset+dimension("editor_preview_margin_top");
        float bottom=bottomInset+dimension("editor_preview_margin_bottom");
        if(visiblePanel!=null&&visiblePanel.sheet.getTop()>0)bottom=Math.max(bottom,height-visiblePanel.sheet.getTop()+dp(12));
        float scale=Math.max(.2f,(height-top-bottom)/height);
        card.setPivotX(root.getWidth()/2f);card.setPivotY(0);card.setScaleX(scale);card.setScaleY(scale);card.setTranslationY(top);
        // The tab is hosted inside the original preview container. Counter-scale
        // its controls so their physical text and touch target sizes stay native.
        tabView.setPivotX(tabView.getWidth()/2f);tabView.setPivotY(tabView.getHeight());
        tabView.setScaleX(1f/scale);tabView.setScaleY(1f/scale);
    }
    private void open(Panel panel){
        if(busy)return;
        if(visiblePanel!=null&&visiblePanel!=panel)visiblePanel.state(5);
        visiblePanel=panel;panel.refresh();panel.container.setVisibility(View.VISIBLE);
        find("fl_button_container").setVisibility(View.INVISIBLE);tabView.setVisibility(View.INVISIBLE);
        panel.state(3);
    }
    boolean closePanel(){if(visiblePanel==null)return false;visiblePanel.state(5);return true;}

    private final class Panel {
        final boolean aod;
        final View container;
        final LinearLayout sheet;
        final Object behavior;
        final RecyclerView list;
        final Items adapter;
        final ArrayList<Choice> choices=new ArrayList<>();
        final LinearLayout sailControls;
        final CompoundButton sailSwitch;
        private boolean bindingSail;
        Panel(boolean aod)throws Exception{
            this.aod=aod;
            FrameLayout slot=(FrameLayout)find(aod?"fl_aod_panel_container":"fl_customization_panel_container");
            container=ui.inflate(aod?"view_aod_panel":"view_customization_panel",slot);
            slot.addView(container,new FrameLayout.LayoutParams(-1,-1));
            sheet=(LinearLayout)ui.find(container,"bottom_sheet");
            sheet.setBackground(ui.getDrawable(ui.id("drawable","panel_background_dark")));
            TextView title=(TextView)ui.find(container,"tv_title");title.setTextColor(-1);
            if(aod){
                title.setCompoundDrawables(null,null,null,null);
                TextView done=(TextView)ui.find(container,"btn_confirm");done.setText("完成");done.setOnClickListener(v->closePanel());
            }
            title.setOnClickListener(v->closePanel());
            FrameLayout body=(FrameLayout)ui.find(container,aod?"aod_panel_container":"custom_panel_container");
            View content=ui.inflate(aod?"view_editor_aod_panel":"view_editor_launcher_effect_panel",body);
            if(aod){
                LinearLayout column=new LinearLayout(activity);column.setOrientation(LinearLayout.VERTICAL);
                sailControls=new LinearLayout(activity);sailControls.setOrientation(LinearLayout.VERTICAL);
                sailSwitch=toggle(sailControls,"息屏动画持续播放");
                sailSwitch.setOnCheckedChangeListener((button,checked)->{if(!bindingSail&&!busy&&options!=null&&options.cosmic==0&&options.aod==0)actions.continuous("sail_continuous_aod",checked);});
                column.addView(sailControls,new LinearLayout.LayoutParams(-1,-2));column.addView(content,new LinearLayout.LayoutParams(-1,0,1));
                body.addView(column,new FrameLayout.LayoutParams(-1,-1));
            }else{sailControls=null;sailSwitch=null;body.addView(content,new FrameLayout.LayoutParams(-1,-1));}
            list=(RecyclerView)ui.find(content,aod?"recycler_view":"recycler_view_launcher_effect");
            list.setLayoutManager(new GridLayoutManager(ui,3));list.setItemAnimator(null);
            adapter=new Items(this);list.setAdapter(adapter);
            Class<?> type=Class.forName("com.google.android.material.bottomsheet.BottomSheetBehavior",true,ui.getClassLoader());
            behavior=type.getMethod("h",LinearLayout.class).invoke(null,sheet);
            call(behavior,"b",new Class<?>[]{G3.d.class},new G3.d(){
                @Override public void a(View view){if(visiblePanel==Panel.this)fit();}
                @Override public void b(int state,View view){
                    if((state==5||state==4)&&visiblePanel==Panel.this){
                        visiblePanel=null;container.setVisibility(View.INVISIBLE);
                        find("fl_button_container").setVisibility(View.VISIBLE);tabView.setVisibility(View.VISIBLE);fit();
                    }
                }
            });
            state(5);container.setVisibility(View.INVISIBLE);
        }
        void state(int state){call(behavior,"o",new Class<?>[]{int.class},state);}
        void refresh(){
            choices.clear();
            if(aod){sailControls.setVisibility(options.cosmic==0&&options.aod==0?View.VISIBLE:View.GONE);bindingSail=true;sailSwitch.setChecked(options.sailContinuousAod);bindingSail=false;sailSwitch.setEnabled(!busy);}
            ((TextView)ui.find(container,"tv_title")).setText(aod?"息屏":mode==1?"纹理":"特效");
            if(aod){
                if(options.pairedFrame()){
                    choices.add(new Choice(-10,"相框照片","ic_wallpaper_picker"));
                    choices.add(new Choice(-11,"调整取景","ic_wallpaper_texture_btn_icon"));
                    choices.add(new Choice(-12,"樱花示例","alive_photo_wallpaper_aod_leave_light.png"));
                }else choices.add(new Choice(-10,"更换照片","ic_wallpaper_picker"));
                choices.add(new Choice(0,"启航","alive_photo_wallpaper_aod_set_sail.png"));
                choices.add(new Choice(1,"留光","alive_photo_wallpaper_aod_leave_light.png"));
                choices.add(new Choice(2,"星月","alive_photo_wallpaper_aod_moon_and_star.png"));
                choices.add(new Choice(3,"轻启","alive_photo_wallpaper_aod_light_start.png"));
                choices.add(new Choice(4,"山脉","alive_photo_wallpaper_aod_mountain.png"));
                choices.add(new Choice(5,"银河","alive_photo_wallpaper_aod_galaxy.png"));
                choices.add(new Choice(101,"全屏 AOD","alive_photo_wallpaper_aod_fullscreen.png"));
            }else if(mode==1){
                choices.add(new Choice(0,"无","alive_photo_wallpaper_lockscreen_source.png"));
                choices.add(new Choice(1,"长虹","alive_photo_wallpaper_lockscreen_straight_line.png"));
                choices.add(new Choice(2,"波浪","alive_photo_wallpaper_lockscreen_curve.png"));
                choices.add(new Choice(3,"雾花","alive_photo_wallpaper_lockscreen_frosted_glass.png"));
            }else{
                choices.add(new Choice(6,"无","alive_photo_wallpaper_lockscreen_source.png"));
                choices.add(new Choice(7,"长虹","alive_photo_wallpaper_lockscreen_straight_line.png"));
                choices.add(new Choice(8,"波浪","alive_photo_wallpaper_lockscreen_curve.png"));
                choices.add(new Choice(9,"雾花","alive_photo_wallpaper_lockscreen_frosted_glass.png"));
            }
            adapter.notifyDataSetChanged();
        }
    }
    private static final class Choice {
        final int value;final String title,image;
        Choice(int value,String title,String image){this.value=value;this.title=title;this.image=image;}
    }
    private static final class Cell extends RecyclerView.ViewHolder {
        Cell(View view){super(view);}
    }
    private final class Items extends RecyclerView.Adapter<Cell> {
        final Panel panel;
        Items(Panel panel){this.panel=panel;}
        @Override public int getItemCount(){return panel.choices.size();}
        @Override public Cell onCreateViewHolder(ViewGroup parent,int type){
            View view=ui.inflate(panel.aod?"item_aod_main_panel_image_with_label":"item_launcher_main_panel_effect",parent);
            // Original 106 dp cells in the original three-column list.
            if(view instanceof LinearLayout)((LinearLayout)view).setGravity(Gravity.CENTER_HORIZONTAL);
            view.setPadding(0,0,0,dp(16));return new Cell(view);
        }
        @Override public void onBindViewHolder(Cell cell,int position){
            Choice choice=panel.choices.get(position);View view=cell.itemView;
            int selected=panel.aod?options.aod:mode==1?options.lock:options.home;
            view.setSelected(choice.value>=0&&choice.value==selected&&options.cosmic==0);
            view.setEnabled(!busy);view.setAlpha(busy?.5f:1);view.setFocusable(true);
            view.setContentDescription(choice.title+(view.isSelected()?"，已选择":""));
            TextView label=(TextView)ui.find(view,"tv_label");label.setText(choice.title);label.setTextColor(-1);
            if(panel.aod)ui.find(view,"iv_arrow_right").setVisibility(choice.value<0?View.VISIBLE:View.GONE);
            ImageView image=(ImageView)ui.find(view,"iv_preview");
            if(choice.image.endsWith(".png")){
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageDrawable(thumbnail(choice.image));
            }else{
                image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);image.setImageDrawable(ui.getDrawable(ui.id("drawable",choice.image)));
            }
            view.setOnClickListener(v->{
                if(busy)return;
                if(choice.value==-10){closePanel();actions.photo();}
                else if(choice.value==-11){closePanel();actions.crop();}
                else if(choice.value==-12)actions.example();
                else actions.choose(panel.aod?"aod":mode==1?"lock":"home",choice.value);
            });
        }
    }
    private Drawable thumbnail(String name){
        Drawable.ConstantState cached=thumbnails.get(name);if(cached!=null)return cached.newDrawable(activity.getResources());
        try(InputStream stream=activity.getAssets().open("editor/"+name)){
            Drawable image=Drawable.createFromStream(stream,name);
            if(image!=null&&image.getConstantState()!=null)thumbnails.put(name,image.getConstantState());return image;
        }catch(Exception e){android.util.Log.e("AliveClean","Editor thumbnail: "+name,e);return null;}
    }
    @Override public void close(){
        if(closed)return;closed=true;clock.scene(false);dialogs.close();thumbnails.clear();ui.close();
    }
}
