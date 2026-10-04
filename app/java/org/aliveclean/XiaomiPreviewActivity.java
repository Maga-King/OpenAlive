package org.aliveclean;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Icon;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Original Xiaomi preview/progress/landing controls bound to its original player. */
public class XiaomiPreviewActivity extends Activity implements SurfaceHolder.Callback {
    public static final class Earth extends XiaomiPreviewActivity {} public static final class Moon extends XiaomiPreviewActivity {}
    public static final class Mars extends XiaomiPreviewActivity {} public static final class Saturn extends XiaomiPreviewActivity {}
    public static final class Geometry extends XiaomiPreviewActivity {} public static final class Snowmountain extends XiaomiPreviewActivity {}
    private XiaomiUi ui; private XiaomiPacks.Pack pack; private XiaomiPlayer player;
    private View detail; private SurfaceView surface; private Object presenter,progress; private Object[] sceneTypes;
    private int scene,land; private boolean resumed,ready,choosing,busy;
    private boolean awaitingPicker;private int previousLand,previousWallpaperId,previousLockId;
    private final Handler main=new Handler(Looper.getMainLooper());
    private OfficialHyperOsClockFace clock; private ImageView aodClock;
    private Bundle metadata;
    private GestureDetector gestures;
    static Intent intent(Context c,XiaomiPacks.Pack pack,int scene){return new Intent().setClassName(c,"org.aliveclean.XiaomiPreviewActivity$"+Character.toUpperCase(pack.id.charAt(0))+pack.id.substring(1)).putExtra("scene",Math.max(0,Math.min(2,scene)));}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(5894);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return ready&&!choosing&&!busy;}
            @Override public boolean onSingleTapUp(MotionEvent e){if(!ready||choosing||busy)return false;stepScene(true,true);return true;}
            @Override public boolean onFling(MotionEvent first,MotionEvent last,float vx,float vy){
                if(first==null||last==null||!ready||choosing||busy)return false;
                // Original BaseSuperWallpaperDetailActivity: swipes stop at the
                // ends; taps and the original progress timer cycle all 3 scenes.
                if(first.getRawX()-last.getRawX()<100f)stepScene(false,false);
                else if(last.getRawX()-first.getRawX()<100f)stepScene(true,false);
                return true;
            }
        });
        scene=Math.max(0,Math.min(2,getIntent().getIntExtra("scene",0)));
        if(state!=null){scene=state.getInt("scene");land=state.getInt("land");choosing=state.getBoolean("choosing");awaitingPicker=state.getBoolean("picker");previousLand=state.getInt("previousLand");previousWallpaperId=state.getInt("previousId");previousLockId=state.getInt("previousLockId");busy=awaitingPicker;}
        new Thread(()->{try{
            XiaomiPacks.Pack p=XiaomiPacks.get(this,getClass().getSimpleName().toLowerCase(Locale.ROOT));File file=XiaomiPacks.find(this,p);
            XiaomiUi.bundle(this);XiaomiPlayer.Runtime runtime=XiaomiPlayer.prepare(this,p.id,file,p.digest);int saved=XiaomiPacks.land(this,p);
            runOnUiThread(()->{if(isDestroyed())return;try{pack=p;if(state==null)land=saved;ui=new XiaomiUi(this);setup(file,runtime);}catch(Exception e){failure(e);}});
        }catch(Exception e){runOnUiThread(()->failure(e));}},"XiaomiPreviewLoad").start();
    }
    private void setup(File file,XiaomiPlayer.Runtime runtime)throws Exception {
        detail=ui.inflate("activity_super_wallpaper_detail");ui.find(detail,"choose_container").setVisibility(View.GONE);
        PackageInfo archive=getPackageManager().getPackageArchiveInfo(file.getPath(),PackageManager.GET_SERVICES|PackageManager.GET_META_DATA);
        if(archive==null)throw new IOException("Cannot read original scene metadata");
        for(ServiceInfo service:archive.services)if(service.metaData!=null&&service.metaData.getBoolean("is_super_wallpaper")&&!service.name.contains("Preview")){metadata=service.metaData;break;}
        if(metadata==null)throw new IOException("Original wallpaper service metadata missing");
        Resources resources=XiaomiPacks.resources(this,pack,file);
        Class<?> contract=ui.loader.loadClass("com.android.thememanager.superwallpaper.base.BaseSuperWallpaperView");
        Object view=Proxy.newProxyInstance(ui.loader,new Class[]{contract},(o,m,a)->m.getName().equals("getActivity")?this:null);
        presenter=ui.loader.loadClass("com.android.thememanager.superwallpaper.presenter.UnitySuperWallpaperPresenter").getConstructor(contract,String.class).newInstance(view,pack.id);
        Class<?> base=ui.loader.loadClass("com.android.thememanager.superwallpaper.base.BaseSuperWallpaperPresenter"),landType=ui.loader.loadClass("com.android.thememanager.superwallpaper.data.LandPositionData");
        ArrayList<Object> lands=new ArrayList<>();
        Class<?> coordinates=ui.loader.loadClass("com.android.thememanager.settings.superwallpaper.activity.data.CoordinateValues");Map<String,String> values=(Map<String,String>)coordinates.getField("k").get(null);
        for(int i=0;i<pack.lands;i++){
            Object point=landType.getConstructor(int.class).newInstance(1);XiaomiUi.call(point,"ki",new Class[]{int.class},i);
            String[][] fields={{"position_title","zurt"},{"position_content","n7h"},{"view_height","fu4"},{"coordinate_longitude","h"},{"coordinate_latitude","kja0"}};
            for(String[] f:fields){String name=pack.id+"_"+f[0]+"_"+i;int id=resources.getIdentifier(name,"string",pack.pkg());String text=id==0?null:resources.getString(id);if(text!=null&&text.contains("%1$s")&&values.containsKey(name))text=String.format(text,values.get(name));XiaomiUi.call(point,f[1],new Class[]{String.class},text);}
            int banner=metadata.getInt("position_preview_"+i);int dark=metadata.getInt("position_preview_"+i+"_dark",banner);
            if(banner!=0){XiaomiUi.call(point,"i",new Class[]{Icon.class},icon(resources,banner));XiaomiUi.call(point,"t8r",new Class[]{Icon.class},icon(resources,dark==0?banner:dark));}
            lands.add(point);
        }
        Field points=base.getDeclaredField("zy");points.setAccessible(true);points.set(presenter,lands);
        Field selected=base.getDeclaredField("n");selected.setAccessible(true);selected.setInt(presenter,land);
        Object adapter=ui.loader.loadClass("com.android.thememanager.superwallpaper.recyclerview.UnityPositionDetailAdapter").getConstructor(Context.class,base).newInstance(ui,presenter);
        View recycler=ui.find(detail,"position_list");Object lm=ui.loader.loadClass("androidx.recyclerview.widget.LinearLayoutManager").getConstructor(Context.class,int.class,boolean.class).newInstance(ui,0,false);
        XiaomiUi.call(recycler,"setLayoutManager",new Class[]{ui.loader.loadClass("androidx.recyclerview.widget.RecyclerView$LayoutManager")},lm);
        XiaomiUi.call(recycler,"setAdapter",new Class[]{ui.loader.loadClass("androidx.recyclerview.widget.RecyclerView$Adapter")},adapter);
        Class<?> listener=ui.loader.loadClass(base.getName()+"$OnLandPositionChangedListener");
        XiaomiUi.call(presenter,"k",new Class[]{listener},Proxy.newProxyInstance(ui.loader,new Class[]{listener},(o,m,a)->{if(m.getName().equals("yz")){land=(Integer)a[0];send("Land_"+land);updateLand();}return null;}));
        progress=ui.find(detail,"progressbar_container");Class<?> scenes=ui.loader.loadClass("com.android.thememanager.superwallpaper.ISuperWallpaperScene");sceneTypes=ui.loader.loadClass(scenes.getName()+"$SceneType").getEnumConstants();
        // OpenAlive supplies AOD on ColorOS too. Xiaomi's device-only capability
        // check would otherwise hide this step and skip it after 300 ms.
        Field supportsAod=progress.getClass().getDeclaredField("p");supportsAod.setAccessible(true);supportsAod.setBoolean(progress,true);
        ui.find(detail,"super_wallpaper_setting_progressbar_aod").setVisibility(View.VISIBLE);
        Object controller=Proxy.newProxyInstance(ui.loader,new Class[]{scenes},(o,m,a)->{if(m.getName().equals("z"))return sceneTypes[scene];if(m.getName().equals("fti")){scene=(scene+((Boolean)a[0]?1:2))%3;changeScene(false);}return null;});
        XiaomiUi.call(progress,"setSuperWallpaperScene",new Class[]{scenes},controller);
        ui.find(detail,"back_btn").setOnClickListener(v->finish());ui.find(detail,"apply_btn").setEnabled(false);ui.find(detail,"apply_btn").setOnClickListener(v->apply());
        View landing=ui.find(detail,"landing_position_container");landing.setVisibility(pack.lands>1?View.VISIBLE:View.GONE);
        landing.setOnClickListener(v->{choosing=!choosing;scene=2;changeScene(false);showChoice();});
        FrameLayout content=new FrameLayout(this);surface=new SurfaceView(this);content.addView(surface,new FrameLayout.LayoutParams(-1,-1));content.addView(detail,new FrameLayout.LayoutParams(-1,-1));
        player=new XiaomiPlayer(runtime,surface.getHolder(),this);player.whenFailed(this::failure);surface.getHolder().addCallback(this);setContentView(content);
        detail.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){if(r>l&&b>t){v.removeOnLayoutChangeListener(this);showChoice();}}});
        // The same original HyperOS clock runtime already used by our system
        // clock plugin supplies the lock preview, not a painted imitation.
        try{clock=new OfficialHyperOsClockFace(OfficialHyperOsUi.open(this),"classic",0);((FrameLayout)ui.find(detail,"super_wallpaper_preview")).addView(clock,new FrameLayout.LayoutParams(-1,-1));clock.setVisibility(View.INVISIBLE);}catch(Exception e){android.util.Log.w("OpenAliveXiaomi","Original lock preview clock",e);}
        aodClock=(ImageView)ui.find(detail,"super_wallpaper_setting_aod_preview");loadAodClock();
    }
    private static Icon icon(Resources r,int id){Bitmap b=BitmapFactory.decodeResource(r,id);if(b==null)throw new IllegalArgumentException("Missing original landing image");return Icon.createWithBitmap(b);}
    private void loadAodClock(){new Thread(()->{try{
        Class<?> utils=ui.loader.loadClass("com.android.thememanager.settings.superwallpaper.utils.AodUtils");
        Bitmap image=(Bitmap)XiaomiUi.call(utils,"s",new Class[]{Context.class,float.class,float.class,float.class,float.class},ui,metadata.getFloat("clock_position_x"),metadata.getFloat("clock_position_y"),metadata.getFloat("dual_clock_position_x_anchor_right"),metadata.getFloat("dual_clock_position_y"));
        runOnUiThread(()->{if(isDestroyed())return;if(image!=null)aodClock.setImageBitmap(image);else offlineAodClock();});
    }catch(Exception e){runOnUiThread(this::offlineAodClock);}},"XiaomiAodPreview").start();}
    private void offlineAodClock(){if(isDestroyed()||detail==null)return;detail.post(()->{if(isDestroyed())return;try{
        aodClock.setImageBitmap(XiaomiAodPreview.render(this,metadata,detail.getWidth(),detail.getHeight()));
    }catch(Exception|LinkageError e){android.util.Log.w("OpenAliveXiaomi","Original offline AOD preview",e);}});}
    private void showChoice(){if(detail==null)return;try{ui.find(detail,"choose_container").setVisibility(choosing?View.VISIBLE:View.GONE);((View)progress).setVisibility(choosing?View.GONE:View.VISIBLE);
        View mask=ui.find(detail,"super_wallpaper_mask");if(mask.isLaidOut())XiaomiUi.call(mask,"toq",new Class[]{boolean.class},choosing);updateLand();updateProgress();
    }catch(Exception e){failure(e);}}
    private void updateLand(){try{
        Object point=((ArrayList<?>)XiaomiUi.call(presenter,"f7l8",new Class[0])).get(land);
        String[][] fields={{"position_title","p"},{"position_content","toq"},{"view_height_value","x2"},{"coordinate_longitude","q"},{"coordinate_latitude","zy"}};
        for(String[] f:fields){String text=(String)XiaomiUi.call(point,f[1],new Class[0]);TextView target=(TextView)ui.find(detail,f[0]);target.setText(text);target.setVisibility(text==null||text.isEmpty()?View.GONE:View.VISIBLE);}
        ui.find(detail,"coordinate_title").setVisibility(ui.find(detail,"coordinate_longitude").getVisibility());ui.find(detail,"view_height_title").setVisibility(ui.find(detail,"view_height_value").getVisibility());
    }catch(Exception e){failure(e);}}
    private void changeScene(boolean force){send((force?new String[]{"ForceAOD","ForceLock","ForceLand"}:new String[]{"AOD","Lock","Desk"})[scene]);try{
        XiaomiUi.call(progress,"toq",new Class[]{sceneTypes[0].getClass()},sceneTypes[scene]);if(clock!=null)clock.setVisibility(scene==1&&!choosing?View.VISIBLE:View.INVISIBLE);if(aodClock!=null)aodClock.setVisibility(scene==0&&!choosing?View.VISIBLE:View.INVISIBLE);
        // Native onSceneChanged/onSceneResume both enqueue ticks. Coalesce
        // after the original tick returns, including landing/pause transitions.
        main.removeCallbacks(progressRefresh);main.post(progressRefresh);
    }catch(Exception e){failure(e);}}
    private final Runnable progressRefresh=this::updateProgress;
    private void stepScene(boolean next,boolean wrap){int value=scene+(next?1:-1);if(!wrap&&(value<0||value>2))return;scene=(value+3)%3;changeScene(false);}
    @Override public boolean onTouchEvent(MotionEvent event){return gestures!=null&&gestures.onTouchEvent(event)||super.onTouchEvent(event);}
    private void updateProgress(){if(progress==null)return;try{XiaomiUi.call(progress,"k",new Class[0]);if(resumed&&ready&&!choosing&&!busy)XiaomiUi.call(progress,"zy",new Class[0]);}catch(Exception e){failure(e);}}
    private void send(String s){if(player!=null&&ready)try{player.send(s);}catch(Exception e){failure(e);}}
    public void surfaceCreated(SurfaceHolder h){}
    public void surfaceChanged(SurfaceHolder h,int f,int w,int height){try{
        player.surfaceChanged(w,height,getWindowManager().getDefaultDisplay().getRefreshRate());player.running(resumed);player.initialize(land,true);
        player.send((getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?"Night":"Day");
        player.whenReady(()->{if(isDestroyed())return;ready=true;ui.find(detail,"loading").setVisibility(View.GONE);ui.find(detail,"apply_btn").setEnabled(!busy);changeScene(true);updateProgress();});
    }catch(Exception e){failure(e);}}
    public void surfaceDestroyed(SurfaceHolder h){try{if(player!=null)player.surfaceDestroyed();}catch(Exception e){failure(e);}}
    private void apply(){if(busy||!ready)return;busy=true;ui.find(detail,"apply_btn").setEnabled(false);updateProgress();
        new Thread(()->{try{
            // Save only after pressing Apply; cancel never alters the installed scene.
            previousLand=XiaomiPacks.land(this,pack);if(!XiaomiPacks.saveLand(this,pack,land))throw new IOException(I18n.t("无法保存落地点"));
            try{XiaomiApply.apply(this,pack);}catch(Exception e){runOnUiThread(this::systemApply);return;}
            applied();
        }catch(Exception e){runOnUiThread(()->{busy=false;ui.find(detail,"apply_btn").setEnabled(true);updateProgress();failure(e);});}},"XiaomiApply").start();
    }
    private void systemApply(){if(isDestroyed())return;try{
        WallpaperManager m=WallpaperManager.getInstance(this);previousWallpaperId=m.getWallpaperId(WallpaperManager.FLAG_SYSTEM);previousLockId=m.getWallpaperId(WallpaperManager.FLAG_LOCK);awaitingPicker=true;
        startActivityForResult(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,pack.component())),77);
    }catch(RuntimeException e){awaitingPicker=false;cancelApply();failure(e);}}
    private void applied(){SceneProvider.changed(this);String message=I18n.t("壁纸已应用");
        if(getPackageManager().resolveContentProvider("com.oplus.aod.AodMachineHelperProvider",0)!=null)try{message=RootBridge.prepareAod(this);}catch(Exception e){message=I18n.t("壁纸已应用，息屏配置未完成：")+e.getMessage();}
        final String done=message;runOnUiThread(()->{Toast.makeText(this,done,Toast.LENGTH_LONG).show();finish();});
    }
    private void cancelApply(){XiaomiPacks.saveLand(this,pack,previousLand);busy=false;ui.find(detail,"apply_btn").setEnabled(ready);updateProgress();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=77||!awaitingPicker)return;awaitingPicker=false;
        WallpaperManager m=WallpaperManager.getInstance(this);WallpaperInfo current=m.getWallpaperInfo();ComponentName expected=new ComponentName(this,pack.component());
        boolean appliedHome=current!=null&&expected.equals(current.getComponent())&&(result==RESULT_OK||m.getWallpaperId(WallpaperManager.FLAG_SYSTEM)!=previousWallpaperId);
        WallpaperInfo lock=Build.VERSION.SDK_INT>=34?m.getWallpaperInfo(WallpaperManager.FLAG_LOCK):null;
        boolean appliedLock=lock!=null&&expected.equals(lock.getComponent())&&(result==RESULT_OK||m.getWallpaperId(WallpaperManager.FLAG_LOCK)!=previousLockId);
        if(appliedHome||appliedLock)new Thread(this::applied,"XiaomiAodApply").start();else cancelApply();
    }
    @Override public void onBackPressed(){if(choosing){choosing=false;showChoice();changeScene(false);}else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();resumed=true;if(player!=null)try{player.running(true);}catch(Exception e){failure(e);}updateProgress();}
    @Override protected void onPause(){resumed=false;updateProgress();try{if(player!=null)player.running(false);}catch(Exception e){failure(e);}super.onPause();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);send((c.uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?"Night":"Day");}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("scene",scene);b.putInt("land",land);b.putBoolean("choosing",choosing);b.putBoolean("picker",awaitingPicker);b.putInt("previousLand",previousLand);b.putInt("previousId",previousWallpaperId);b.putInt("previousLockId",previousLockId);}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);try{if(clock!=null)clock.close();if(player!=null)player.close();}catch(Exception e){android.util.Log.w("OpenAliveXiaomi","Preview cleanup",e);}super.onDestroy();}
    private void failure(Exception e){String reason=XiaomiFailure.describe(this,"Preview "+getClass().getSimpleName(),e);android.util.Log.e("OpenAliveXiaomi","Preview",e);if(!isDestroyed())new AlertDialog.Builder(this).setTitle(I18n.t("预览暂时无法打开")).setMessage(reason).setPositiveButton(I18n.t("确定"),(d,w)->finish()).show();}
}
