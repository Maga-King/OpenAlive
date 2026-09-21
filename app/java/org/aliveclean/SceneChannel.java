package org.aliveclean;

import android.content.Context;
import android.os.*;
import java.util.LinkedHashSet;

/** Authenticated scene delivery and frame completion feedback; no per-frame IPC. */
final class SceneChannel {
    interface Listener {void scene(int mode,boolean animate,long time,int phase,long clockToken);void region(AodRegion area);void disconnected();}
    private static final LinkedHashSet<Listener> listeners=new LinkedHashSet<>();
    private static Messenger messenger;
    private static volatile Messenger clockFeedback;
    private static Handler main;
    private static IBinder owner;
    private static Bundle lastScene;
    private static AodRegion lastArea;
    private static int senderUid=-1;
    private static int clockApi;
    private static String clockError="";
    static synchronized boolean hasClockHost(){return owner!=null&&owner.isBinderAlive()&&clockApi==4;}
    static synchronized String clockStatus(){
        if(owner==null||!owner.isBinderAlive())return "正在等待系统时钟模块连接";
        if(hasClockHost())return "息屏使用独立时钟，锁屏保留系统时钟";
        if(!clockError.isEmpty()||clockApi==0)return "息屏时钟模块初始化失败，请查看模块诊断日志";
        return "系统仍在运行旧版时钟模块，需要重新加载";
    }
    static synchronized IBinder connect(Context context,IBinder source,int api,String error,IBinder feedback)throws Exception {
        if(messenger==null){
            senderUid=context.getPackageManager().getApplicationInfo("com.android.systemui",0).uid;
            main=new Handler(Looper.getMainLooper(),message->{
                if(message.sendingUid!=senderUid&&message.sendingUid!=1000)return true;
                Bundle b=message.getData();
                if(message.what==1){
                    int mode=b.getInt("mode",-1);if(mode<0||mode>2)return true;
                    long time=b.getLong("time",0);
                    if(lastScene!=null&&time<lastScene.getLong("time",0))return true;
                    lastScene=new Bundle(b);
                    {if(Diagnostics.TRACE)android.util.Log.i("AliveClean","Scene transport=binder mode="+mode+" delayMs="+(SystemClock.uptimeMillis()-time));}
                    for(Listener l:listeners)l.scene(mode,b.getBoolean("animate",true),time,b.getInt("phase",0),b.getLong("clock_wake",0));
                }else if(message.what==2){
                    AodRegion area=decode(b);if(area==null)return true;lastArea=area;
                    for(Listener l:listeners)l.region(area);
                }
                return true;
            });
            messenger=new Messenger(main);
        }
        if(source!=null&&source!=owner){
            owner=source;
            source.linkToDeath(()->main.post(()->{
                if(owner!=source)return;owner=null;clockFeedback=null;lastScene=null;lastArea=null;
                for(Listener l:listeners)l.disconnected();
            }),0);
        }
        clockApi=api;
        clockError=error==null?"":error;
        clockFeedback=api==4&&feedback!=null?new Messenger(feedback):null;
        return messenger.getBinder();
    }
    static void frameReady(long token,boolean submitted){
        Messenger target=clockFeedback;if(target==null||token==0)return;
        Message message=Message.obtain();message.what=1;Bundle b=new Bundle();b.putLong("token",token);b.putBoolean("submitted",submitted);message.setData(b);
        try{target.send(message);}catch(RemoteException error){android.util.Log.w("AliveClean","Frame completion channel unavailable",error);}
    }
    static void add(Listener listener){
        listeners.add(listener);
        if(lastArea!=null)listener.region(lastArea);
        if(lastScene!=null)listener.scene(lastScene.getInt("mode"),false,lastScene.getLong("time"),lastScene.getInt("phase",0),lastScene.getLong("clock_wake",0));
    }
    static void remove(Listener listener){listeners.remove(listener);}
    static AodRegion decode(Bundle b){
        if(b==null||b.getInt("version",0)!=1)return null;
        return AodRegion.create(b.getInt("display",-1),b.getInt("width",0),b.getInt("height",0),b.getInt("left",0),b.getInt("top",0),b.getInt("right",0),b.getInt("bottom",0));
    }
}
