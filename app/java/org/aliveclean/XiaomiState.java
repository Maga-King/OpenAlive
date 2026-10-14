package org.aliveclean;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.util.*;

/** Same-UID state and scene relay for isolated native player processes. */
public final class XiaomiState extends ContentProvider {
    static final Uri URI=Uri.parse("content://org.aliveclean.xiaomi/state");
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<IBinder,Messenger> clients=new HashMap<>();
    private Bundle scene;
    static Bundle call(Context c,String method,Bundle b){return c.getContentResolver().call(URI,method,null,b);}
    private static boolean own(Context c,WallpaperInfo i){return i!=null&&i.getPackageName().equals(c.getPackageName())&&i.getServiceName().startsWith("org.aliveclean.XiaomiWallpaper$");}
    static boolean active(Context c){try{WallpaperManager m=WallpaperManager.getInstance(c);return own(c,m.getWallpaperInfo())||(Build.VERSION.SDK_INT>=34&&own(c,m.getWallpaperInfo(WallpaperManager.FLAG_LOCK)));}catch(RuntimeException e){return false;}}
    static Bundle lockClock(Context c)throws Exception {
        XiaomiPacks.Pack pack=selected(c,1);
        return pack==null?null:new Bundle(pack.clock);
    }
    static XiaomiPacks.Pack selected(Context c,int mode)throws Exception {
        WallpaperManager manager=WallpaperManager.getInstance(c);
        WallpaperInfo info=mode==2||Build.VERSION.SDK_INT<34?manager.getWallpaperInfo():manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
        // A separate static lock wallpaper has no WallpaperInfo. Do not take
        // ownership merely because the home wallpaper happens to be Xiaomi.
        if(info==null&&manager.getWallpaperId(WallpaperManager.FLAG_LOCK)<0)info=manager.getWallpaperInfo();
        if(!own(c,info))return null;
        for(XiaomiPacks.Pack p:XiaomiPacks.all(c))if(p.component().equals(info.getServiceName()))return p;
        return null;
    }
    @Override public boolean onCreate(){main.post(()->SceneChannel.add(new SceneChannel.Listener(){
        public void scene(int mode,boolean animate,long time,int phase,long token,float night,float mask){
            Bundle b=new Bundle();b.putInt("mode",mode);b.putBoolean("animate",animate);b.putLong("time",time);b.putInt("phase",phase);b.putFloat("night",night);b.putFloat("mask",mask);scene=b;dispatch(1,b);
        }
        public void region(AodRegion r){}
        public void disconnected(){scene=null;dispatch(3,new Bundle());}
    }));return true;}
    private void dispatch(int what,Bundle b){for(Messenger client:new ArrayList<>(clients.values()))send(client,what,b);}
    private void send(Messenger client,int what,Bundle b){Message m=Message.obtain();m.what=what;m.setData(new Bundle(b));try{client.send(m);}catch(RemoteException e){clients.remove(client.getBinder());}}
    @Override public Bundle call(String method,String arg,Bundle data){
        if(Binder.getCallingUid()!=android.os.Process.myUid())throw new SecurityException("Same UID required");
        try{
            Bundle result=new Bundle();Context c=getContext();
            if("subscribe".equals(method)){
                IBinder binder=data.getBinder("client");if(binder==null)throw new IllegalArgumentException("Missing client");Messenger client=new Messenger(binder);
                binder.linkToDeath(()->main.post(()->clients.remove(binder)),0);
                main.post(()->{clients.put(binder,client);send(client,4,motion(c));if(scene!=null)send(client,1,scene);});return result;
            }
            if("unsubscribe".equals(method)){IBinder binder=data.getBinder("client");main.post(()->clients.remove(binder));return result;}
            if("motionRead".equals(method))return motion(c);
            if("motionSave".equals(method)){
                if(data==null)throw new IllegalArgumentException("Missing desktop settings");
                if(!c.getSharedPreferences("xiaomi",0).edit()
                        .putInt("desktop.amplitude",XiaomiDesktopMotion.clamp(data.getInt("amplitude")))
                        .putInt("desktop.responsiveness",XiaomiDesktopMotion.clamp(data.getInt("responsiveness"))).commit())
                    throw new IllegalStateException("Cannot save desktop settings");
                Bundle saved=motion(c);main.post(()->dispatch(4,saved));return saved;
            }
            XiaomiPacks.Pack p=XiaomiPacks.get(c,data.getString("family"));
            if("save".equals(method)){
                int land=data.getInt("land",-1);if(land<0||land>=p.lands)throw new IllegalArgumentException("Invalid landing point");
                if(!c.getSharedPreferences("xiaomi",0).edit().putInt(p.id+".land",land).commit())throw new IllegalStateException("Cannot save Xiaomi selection");
                Bundle b=new Bundle(data);main.post(()->dispatch(2,b));
            }else if(!"read".equals(method))throw new IllegalArgumentException("Unknown operation");
            result.putInt("land",c.getSharedPreferences("xiaomi",0).getInt(p.id+".land",0));return result;
        }catch(Exception e){throw new IllegalStateException("Xiaomi state unavailable",e);}
    }
    private static Bundle motion(Context c){
        android.content.SharedPreferences p=c.getSharedPreferences("xiaomi",0);Bundle b=new Bundle();
        b.putInt("amplitude",XiaomiDesktopMotion.clamp(p.getInt("desktop.amplitude",0)));
        b.putInt("responsiveness",XiaomiDesktopMotion.clamp(p.getInt("desktop.responsiveness",0)));return b;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
