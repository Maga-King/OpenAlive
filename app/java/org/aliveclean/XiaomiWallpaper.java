package org.aliveclean;

import android.app.KeyguardManager;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.service.wallpaper.WallpaperService;
import android.view.*;
import java.io.File;
import java.util.*;

/** One original native player per service process, with explicit surface ownership. */
public class XiaomiWallpaper extends WallpaperService implements DisplayManager.DisplayListener {
    public static final class Earth extends XiaomiWallpaper {} public static final class Moon extends XiaomiWallpaper {}
    public static final class Mars extends XiaomiWallpaper {} public static final class Saturn extends XiaomiWallpaper {}
    public static final class Geometry extends XiaomiWallpaper {} public static final class Snowmountain extends XiaomiWallpaper {}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<SceneEngine> engines=new ArrayList<>();
    private final SceneState scenes=new SceneState();
    private XiaomiPacks.Pack pack; private XiaomiPlayer.Runtime runtime; private XiaomiPlayer player;
    private SceneEngine owner; private DisplayManager displays; private boolean destroyed,ambient,ready; private int land,mode=-1;
    private int desktopAmplitude,desktopResponsiveness,offsetGeneration;
    private final Messenger client=new Messenger(new Handler(Looper.getMainLooper(),m->{
        if(m.sendingUid!=android.os.Process.myUid())return true;Bundle b=m.getData();
        if(m.what==1){int next=b.getInt("mode",-1);boolean changed=scenes.accept(next,b.getLong("time"),b.getInt("phase"));if(changed){ambient=next==0;setMode(next,!b.getBoolean("animate",true));}refresh();}
        else if(m.what==2&&pack!=null&&pack.id.equals(b.getString("family"))){land=b.getInt("land");send("Land_"+land);}
        else if(m.what==3){scenes.disconnect();updateMode(false);refresh();}
        else if(m.what==4){desktopAmplitude=b.getInt("amplitude");desktopResponsiveness=b.getInt("responsiveness");offsetGeneration++;sendOffset();}return true;
    }));
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(Intent.ACTION_TIME_TICK.equals(i.getAction())){daylight();return;}
        if(Intent.ACTION_TIME_CHANGED.equals(i.getAction())||Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction()))daylight();
        if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))ambient=true;
        else if(Intent.ACTION_SCREEN_ON.equals(i.getAction())||Intent.ACTION_USER_PRESENT.equals(i.getAction()))ambient=false;
        if(player!=null)try{player.calendarChanged();}catch(Exception e){failure(e);}
        updateMode(false);refresh();
    }};
    @Override public void onCreate(){super.onCreate();displays=getSystemService(DisplayManager.class);displays.registerDisplayListener(this,main);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_DATE_CHANGED);filter.addAction(Intent.ACTION_TIME_TICK);filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);
        new Thread(()->{try{
            XiaomiPacks.Pack p=XiaomiPacks.get(this,getClass().getSimpleName().toLowerCase(Locale.ROOT));File file=XiaomiPacks.find(this,p);
            int saved=XiaomiPacks.land(this,p);XiaomiPlayer.Runtime loaded=XiaomiPlayer.prepare(this,p.id,file,p.digest);
            main.post(()->{if(destroyed)return;pack=p;land=saved;runtime=loaded;
                Bundle b=new Bundle();b.putBinder("client",client.getBinder());try{XiaomiState.call(this,"subscribe",b);}catch(RuntimeException e){failure(e);}select();});
        }catch(Exception e){failure(e);}},"XiaomiWallpaperLoad").start();
    }
    @Override public Engine onCreateEngine(){return new SceneEngine();}
    private void select(){
        SceneEngine next=null;for(SceneEngine e:engines)if(e.surface&&(next==null||e.visible&&!next.visible||e.visible&&e.isPreview()))next=e;
        if(next==null){owner=null;offsetGeneration++;refresh();return;}boolean changed=next!=owner;if(changed)offsetGeneration++;owner=next;
        if(runtime==null)return;
        try{
            if(player==null){player=new XiaomiPlayer(runtime,next.getSurfaceHolder(),next.getDisplayContext());player.whenFailed(this::failure);changed=true;}
            if(changed)attach();else refresh();
        }catch(Exception e){failure(e);}
    }
    private void attach(){if(player==null||owner==null||!owner.surface)return;try{
        player.surface(owner.getSurfaceHolder());android.graphics.Rect r=owner.getSurfaceHolder().getSurfaceFrame();Display display=owner.display();
        player.surfaceChanged(r.width(),r.height(),display==null?60:display.getRefreshRate());
        boolean show=canRun();player.running(show);
        player.send((getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?"Night":"Day");
        player.initialize(land,owner.isPreview());
        player.whenReady(()->{if(destroyed||owner==null||!owner.surface)return;ready=true;updateMode(true);sendOffset();refresh();});
    }catch(Exception e){failure(e);}}
    private void send(String event){if(player!=null&&ready)try{player.send(event);}catch(Exception e){failure(e);}}
    private void updateMode(boolean force){if(owner==null)return;int next=owner.isPreview()?1:scenes.fallback(ambient,getSystemService(KeyguardManager.class).isKeyguardLocked());setMode(next,force);}
    private void setMode(int next,boolean force){
        if(next<0||next>2)return;
        if(next!=1&&pendingLock!=null){main.removeCallbacks(pendingLock);pendingLock=null;}
        if(next!=mode||force){
            main.removeCallbacks(settleAod);aodSettled=false;
            // 自适应 AOD: panoramic AOD belongs to ColorOS, which shows this
            // surface under its own clock. Keep the lock composition there:
            // Desk->AOD plays the original Desk->Lock move, Lock<->AOD sends nothing.
            boolean wasHeld=mode==0&&aodHeld;
            if(next==0)aodHeld=owner!=null&&!owner.isPreview()&&AdaptiveAod.active(this);
            if(next==0?aodHeld:wasHeld){
                int shown=mode<0?-1:mode==0?1:mode,target=next==0?1:next;mode=next;
                if(mode==0){long window=AdaptiveAod.renderMillis(this);if(window>=0)main.postDelayed(settleAod,window);}
                if(force||shown!=target)send((force?new String[]{null,"ForceLock","ForceLand"}:new String[]{null,"Lock","Desk"})[target]);
                refresh();return;
            }
        }
        if(!force&&mode==next)return;
        boolean waking=next==1&&(mode==0||pendingLock!=null)&&owner!=null&&!owner.isPreview();mode=next;
        if(waking){
            if(pendingLock!=null)return;
            // Xiaomi screenOn also waits 100 ms and discards the candidate
            // after authentication. Touch-to-wake can precede fingerprint
            // success; sending Lock immediately bends a direct AOD->Desk path.
            pendingLock=()->{
                pendingLock=null;if(destroyed||mode!=1||owner==null||!owner.surface)return;
                if(!getSystemService(KeyguardManager.class).isKeyguardLocked())return;
                send(force?"ForceLock":"Lock");
            };
            main.postDelayed(pendingLock,100);return;
        }
        send((force?new String[]{"ForceAOD","ForceLock","ForceLand"}:new String[]{"AOD","Lock","Desk"})[mode]);
    }
    private Runnable pendingLock;
    private boolean aodHeld,aodSettled;
    private final Runnable settleAod=()->{if(mode==0&&aodHeld){aodSettled=true;refresh();}};
    // Snow Mountain picks its light from os.date() on each rendered frame
    // (CameraCtrl.lua GetTimeSlice, default sunrise 06:00, sunset 18:00) and
    // fades the new slice in over 5 s of frame time. A settled panoramic AOD
    // renders nothing, so wake it briefly when the official slice changes.
    private int shownSlice;
    private static int daySlice(){
        Calendar c=Calendar.getInstance();int t=c.get(Calendar.HOUR_OF_DAY)*3600+c.get(Calendar.MINUTE)*60+c.get(Calendar.SECOND),rise=21600,set=64800;
        if(t>=rise-3600&&t<rise)return 1;if(t>=rise&&t<rise+7200)return 2;if(t>=rise+7200&&t<(rise+set)/2)return 3;
        if(t>=(rise+set)/2&&t<set-3600)return 4;if(t>=set-3600&&t<set)return 5;if(t>=set&&t<set+2400)return 6;
        if(t>=set+2400&&t<set+4800)return 7;if(t>=set+4800&&t<set+7200)return 8;return 9;
    }
    private void daylight(){
        if(pack==null||!"snowmountain".equals(pack.id))return;int now=daySlice();if(now==shownSlice)return;
        if(mode==0&&aodHeld&&aodSettled){aodSettled=false;refresh();main.removeCallbacks(settleAod);main.postDelayed(settleAod,6000);}
    }
    private boolean canRun(){if(owner==null||!owner.surface||!owner.visible)return false;if(aodSettled&&!owner.isPreview())return false;
        Display d=owner.display();if(d==null)return false;return d.getState()!=Display.STATE_OFF&&d.getState()!=Display.STATE_UNKNOWN;}
    private void refresh(){if(player==null)return;try{boolean run=canRun();player.running(run);if(run&&ready)shownSlice=daySlice();}catch(Exception e){failure(e);}}
    private void sendOffset(){if(owner!=null&&owner.offset>=0)send("Offset_"+XiaomiDesktopMotion.offset(owner.offset,desktopAmplitude));}
    private void queueOffset(SceneEngine engine,float value){
        // Keep intermediate samples; debouncing to the final page loses motion.
        // Invalidate queued samples when controls or the surface owner change.
        refresh();
        int generation=offsetGeneration;float adjusted=XiaomiDesktopMotion.offset(value,desktopAmplitude);
        main.postDelayed(()->{if(generation==offsetGeneration&&!destroyed&&owner==engine&&engine.surface&&engine.visible&&canRun())send("Offset_"+adjusted);},XiaomiDesktopMotion.delay(desktopResponsiveness));
    }
    private void failure(Throwable e){android.util.Log.e("OpenAliveXiaomi","Wallpaper runtime",e);}
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        super.dump(fd,out,args);
        out.println("Xiaomi family="+(pack==null?"loading":pack.id)+" ready="+ready+" running="+(player!=null&&player.isRunning())+" mode="+mode+" land="+land+" aodHeld="+aodHeld+" aodSettled="+aodSettled+" pendingLock="+(pendingLock!=null));
        if(player!=null)out.println("  player "+player.status());
        for(SceneEngine e:engines){Display d=e.display();out.println("  owner="+(e==owner)+" preview="+e.isPreview()+" surface="+e.surface+" visible="+e.visible+" display="+(d==null?-1:d.getState())+" offset="+e.offset);}
    }
    public void onDisplayAdded(int id){refresh();}public void onDisplayRemoved(int id){refresh();}public void onDisplayChanged(int id){updateMode(false);refresh();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);send((c.uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?"Night":"Day");}
    final class SceneEngine extends Engine {
        boolean surface,visible;float offset=-1;
        Display display(){return Build.VERSION.SDK_INT>=30?getDisplayContext().getDisplay():displays.getDisplay(Display.DEFAULT_DISPLAY);}
        @Override public void onCreate(SurfaceHolder h){super.onCreate(h);engines.add(this);setOffsetNotificationsEnabled(true);}
        @Override public void onSurfaceChanged(SurfaceHolder h,int f,int w,int height){super.onSurfaceChanged(h,f,w,height);surface=true;boolean same=owner==this;select();if(same)attach();}
        @Override public void onSurfaceDestroyed(SurfaceHolder h){surface=false;if(owner==this&&player!=null)try{player.surfaceDestroyed();}catch(Exception e){failure(e);}select();super.onSurfaceDestroyed(h);}
        @Override public void onVisibilityChanged(boolean value){visible=value;select();updateMode(false);refresh();}
        @Override public void onOffsetsChanged(float x,float y,float xs,float ys,int xp,int yp){float value=((int)(Math.max(0,Math.min(1,x))*1000f))/10f;if(offset==value)return;offset=value;if(owner==this)queueOffset(this,value);}
        public void onAmbientModeChanged(boolean value,long duration){ambient=value;updateMode(false);refresh();}
        @Override public Bundle onCommand(String action,int x,int y,int z,Bundle extras,boolean result){
            if(!scenes.authoritative()){
                if("android.wallpaper.goingtosleep".equals(action)){ambient=true;setMode(0,false);}
                else if("android.wallpaper.wakingup".equals(action)){ambient=false;setMode(1,false);}
                else if("android.wallpaper.keyguardgoingaway".equals(action)){ambient=false;setMode(2,false);}
            }
            return super.onCommand(action,x,y,z,extras,result);
        }
        @Override public void onDestroy(){engines.remove(this);surface=false;visible=false;select();super.onDestroy();}
    }
    @Override public void onDestroy(){destroyed=true;displays.unregisterDisplayListener(this);unregisterReceiver(screen);
        main.removeCallbacksAndMessages(null);
        Bundle b=new Bundle();b.putBinder("client",client.getBinder());try{XiaomiState.call(this,"unsubscribe",b);}catch(RuntimeException ignored){}
        try{if(player!=null)player.close();}catch(Exception e){failure(e);}super.onDestroy();
    }
}
