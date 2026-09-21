package org.aliveclean;

import android.app.KeyguardManager;
import android.content.*;
import android.os.Bundle;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public final class CleanWallpaper extends WallpaperService {
    @Override public Engine onCreateEngine(){return new SceneEngine();}
    final class SceneEngine extends Engine implements SceneChannel.Listener {
        RenderLoop renderer;
        boolean ambient;
        final SceneState scenesState=new SceneState();
        boolean regionObserverRegistered,receivedLayout;
        final android.database.ContentObserver area=new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())){
            @Override public void onChange(boolean selfChange){readOfficialRegion();}
        };
        final BroadcastReceiver state=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){update();}};
        final BroadcastReceiver scenes=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
            if(isPreview())return;
            if(ColorOsBridge.ACTION_LAYOUT.equals(intent.getAction())){
                if(intent.getIntExtra("version",0)!=1)return;
                AodRegion region=AodRegion.create(intent.getIntExtra("display",-1),intent.getIntExtra("width",0),intent.getIntExtra("height",0),intent.getIntExtra("left",0),intent.getIntExtra("top",0),intent.getIntExtra("right",0),intent.getIntExtra("bottom",0));
                region(region);
                return;
            }
            if(!ColorOsBridge.ACTION.equals(intent.getAction()))return;
            int next=intent.getIntExtra("mode",-1);
            if(next>=0&&next<=2)scene(next,intent.getBooleanExtra("animate",true),intent.getLongExtra("time",android.os.SystemClock.uptimeMillis()),intent.getIntExtra("phase",0),intent.getLongExtra("clock_wake",0));
        }};
        @Override public void onCreate(SurfaceHolder holder){
            super.onCreate(holder);renderer=new RenderLoop(CleanWallpaper.this,isPreview()?SceneOptions.DRAFT:SceneOptions.APPLIED);
            if(!isPreview())SceneChannel.add(this);
            IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);
            IntentFilter bridge=new IntentFilter(ColorOsBridge.ACTION);
            bridge.addAction(ColorOsBridge.ACTION_LAYOUT);
            // ColorOS SystemUI has its own UID. Allow that signed system sender explicitly,
            // without exposing wallpaper state changes to ordinary third-party applications.
            if(android.os.Build.VERSION.SDK_INT>=33){
                registerReceiver(state,f,Context.RECEIVER_NOT_EXPORTED);
                registerReceiver(scenes,bridge,ColorOsBridge.SENDER_PERMISSION,null,Context.RECEIVER_EXPORTED);
            }else{
                registerReceiver(state,f);
                registerReceiver(scenes,bridge,ColorOsBridge.SENDER_PERMISSION,null);
            }
            if(!isPreview())try{
                getContentResolver().registerContentObserver(android.provider.Settings.System.getUriFor("alive_wallpaper_position"),false,area);
                regionObserverRegistered=true;
            }catch(RuntimeException error){android.util.Log.w("AliveClean","AOD region observer unavailable",error);}
            setTouchEventsEnabled(false);{if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Wallpaper engine created preview="+isPreview());}
        }
        @Override public void scene(int next,boolean animate,long time,int phase,long clockToken){
            boolean changed=scenesState.accept(next,time,phase);
            if(changed){ambient=next==0;renderer.mode(next,animate);}
            if(scenesState.mode()==next&&engineDisplayId()==android.view.Display.DEFAULT_DISPLAY)renderer.clockTransition(clockToken);
            {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Scene mode="+next+" changed="+changed+" animate="+animate+" phase="+phase+" delayMs="+(android.os.SystemClock.uptimeMillis()-time));}
        }
        @Override public void region(AodRegion region){
            int display=engineDisplayId();
            if(region!=null&&region.displayId==display){receivedLayout=true;renderer.aodRegion(region,display);}
        }
        @Override public void disconnected(){scenesState.disconnect();update();}
        private android.view.Display engineDisplay(){
            if(android.os.Build.VERSION.SDK_INT>=30)return getDisplayContext().getDisplay();
            return getSystemService(android.hardware.display.DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY);
        }
        private int engineDisplayId(){android.view.Display display=engineDisplay();return display==null?-1:display.getDisplayId();}
        private void readOfficialRegion(){
            if(isPreview()||renderer==null||receivedLayout)return;
            try{
                android.view.Display display=engineDisplay();
                // Flyme's legacy setting contains no display identity; use it only on the main display.
                if(display==null||display.getDisplayId()!=android.view.Display.DEFAULT_DISPLAY)return;
                android.graphics.Point size=new android.graphics.Point();display.getRealSize(size);
                AodRegion region=AodRegion.parse(display.getDisplayId(),size.x,size.y,android.provider.Settings.System.getString(getContentResolver(),"alive_wallpaper_position"));
                if(region!=null)renderer.aodRegion(region,display.getDisplayId());
            }catch(RuntimeException error){android.util.Log.w("AliveClean","AOD region unavailable",error);}
        }
        private void update(){KeyguardManager k=getSystemService(KeyguardManager.class);renderer.mode(isPreview()?1:scenesState.fallback(ambient,k.isKeyguardLocked()));}
        @Override public void onSurfaceChanged(SurfaceHolder h,int format,int w,int height){super.onSurfaceChanged(h,format,w,height);{if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Surface changed "+w+"x"+height);}update();renderer.attach(h.getSurface(),w,height);readOfficialRegion();}
        @Override public void onVisibilityChanged(boolean visible){{if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Wallpaper visible="+visible);}renderer.visible(visible);if(visible)update();}
        // Framework @SystemApi callback: absent from the public SDK's stubs.
        public void onAmbientModeChanged(boolean inAmbientMode,long duration){ambient=inAmbientMode;{if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Ambient="+ambient);}update();}
        @Override public Bundle onCommand(String action,int x,int y,int z,Bundle extras,boolean resultRequested){
            {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Wallpaper command="+action+" authority="+scenesState.authoritative());}
            if(scenesState.authoritative())return super.onCommand(action,x,y,z,extras,resultRequested);
            boolean animate=extras==null||!extras.getBoolean("action_alive_animator_end",false);
            if("action_alive_aod".equals(action)){ambient=true;renderer.mode(0,animate);}
            else if("action_alive_lock".equals(action)||"android.wallpaper.keyguard_locked".equals(action)){ambient=false;renderer.mode(1,animate);}
            else if("action_alive_launcher".equals(action)||"android.wallpaper.keyguard_going_away".equals(action)){ambient=false;renderer.mode(2,animate);}
            return super.onCommand(action,x,y,z,extras,resultRequested);
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder h){renderer.detach();super.onSurfaceDestroyed(h);}
        @Override public void onDestroy(){SceneChannel.remove(this);unregisterReceiver(state);unregisterReceiver(scenes);if(regionObserverRegistered)getContentResolver().unregisterContentObserver(area);renderer.close();super.onDestroy();}
    }
}
