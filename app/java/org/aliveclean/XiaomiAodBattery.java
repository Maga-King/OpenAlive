package org.aliveclean;

import android.content.*;
import android.os.BatteryManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Method;

/** Original Xiaomi battery artwork with host-owned battery events and no wake lock. */
final class XiaomiAodBattery extends LinearLayout {
    private final Context host;
    private final View container,icon;
    private final TextView text;
    private final Method iconUpdate;
    private boolean active,failed,charging;
    private int level=-1;
    private Runnable onChanged;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){if(active)read(intent);}
    };
    static XiaomiAodBattery install(Context host,View root,android.graphics.Typeface font){
        View container=find(root,"battery_container");
        try{
        View old=find(root,"aod_battery_layout");
        if(!(old instanceof LinearLayout)||!(old.getParent() instanceof ViewGroup)||container==null)
            throw new IllegalStateException("Original Xiaomi battery layout missing");
        LinearLayout source=(LinearLayout)old;ViewGroup parent=(ViewGroup)old.getParent();
        XiaomiAodBattery battery=new XiaomiAodBattery(host,container,source,font);
        int index=parent.indexOfChild(old);ViewGroup.LayoutParams params=old.getLayoutParams();
        parent.removeView(old);parent.addView(battery,index,params);
        battery.read(host.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
        return battery;
        }catch(Exception|LinkageError error){
            // An optional battery must never take down the clock or leave the
            // original vendor controller in an attached hierarchy.
            if(container!=null&&container.getParent() instanceof ViewGroup)((ViewGroup)container.getParent()).removeView(container);
            android.util.Log.w("OpenAliveXiaomi","Original battery layout unavailable",error);return null;
        }
    }
    private XiaomiAodBattery(Context host,View container,LinearLayout source,android.graphics.Typeface font)throws Exception{
        super(source.getContext());this.host=host;this.container=container;
        icon=find(source,"aod_battery_icon");View number=find(source,"aod_battery_digital");
        if(icon==null||!(number instanceof TextView))throw new IllegalStateException("Original Xiaomi battery contents missing");
        text=(TextView)number;
        iconUpdate=icon.getClass().getMethod("update",int.class,boolean.class,boolean.class,boolean.class,boolean.class,boolean.class);
        restorePaths(icon);
        setId(source.getId());setOrientation(source.getOrientation());setGravity(source.getGravity());
        setLayoutDirection(source.getLayoutDirection());setAlpha(source.getAlpha());
        setPadding(source.getPaddingLeft(),source.getPaddingTop(),source.getPaddingRight(),source.getPaddingBottom());
        // Keep both original leaves, their drawable resources, spacing and sizes.
        // Only AODBatteryMeterView owns the vendor BatteryController/wake lock.
        while(source.getChildCount()>0){View child=source.getChildAt(0);source.removeViewAt(0);addView(child);}
        text.setTypeface(font);container.setVisibility(GONE);
    }
    private static void restorePaths(View icon)throws Exception{
        // The original icon reflects android.util.PathParser, blocked for normal
        // apps on newer Android. Parse the SAME bundled mask data with the APK's
        // public AndroidX parser, retaining its density-dependent transform.
        String[][] paths={{"mChargingMaskPath","battery_meter_charging_mask_path","mMaskCharging","battery_meter_path_to_dp"},
                {"mQuickChargingMaskPath","battery_meter_quick_charging_mask_path","mMaskCharging","battery_meter_path_to_dp"},
                {"mProgressMaskPath","battery_meter_progress_mask_path","mMaskProgress","battery_meter_progress_mask_scale_path_to_dp"}};
        for(String[] item:paths){
            java.lang.reflect.Field flag=icon.getClass().getDeclaredField(item[2]);flag.setAccessible(true);
            if(!flag.getBoolean(icon))continue;
            java.lang.reflect.Field field=icon.getClass().getDeclaredField(item[0]);field.setAccessible(true);
            if(field.get(icon)!=null)continue;
            android.content.res.Resources res=icon.getResources();
            String data=res.getString(res.getIdentifier(item[1],"string","com.miui.aod"));
            Class<?> parser=icon.getClass().getClassLoader().loadClass("androidx.core.graphics.PathParser");
            android.graphics.Path path=(android.graphics.Path)parser.getMethod("createPathFromPathData",String.class).invoke(null,data);
            if(path==null)throw new IllegalStateException("Original battery mask missing");
            // The rounded progress mask has its own coordinate scale. Reusing
            // the charging scale here cuts into the original battery outline.
            float scale=res.getDimension(res.getIdentifier(item[3],"dimen","com.miui.aod"));
            android.graphics.Matrix matrix=new android.graphics.Matrix();matrix.setScale(scale,scale);path.transform(matrix);field.set(icon,path);
        }
    }
    private static View find(View root,String id){return root.findViewById(root.getResources().getIdentifier(id,"id","com.miui.aod"));}
    void whenChanged(Runnable callback){onChanged=callback;}
    void active(boolean value){
        if(value==active||failed)return;
        if(!value){active=false;host.unregisterReceiver(receiver);return;}
        try{
            IntentFilter filter=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent sticky=Build.VERSION.SDK_INT>=33?host.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED):host.registerReceiver(receiver,filter);
            active=true;read(sticky);
        }catch(RuntimeException error){unavailable(error);}
    }
    private void read(Intent intent){
        if(intent==null)return;
        int next=percentage(intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),intent.getIntExtra(BatteryManager.EXTRA_SCALE,0));
        int status=intent.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean plugged=intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
        boolean nextCharging=plugged&&(status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL);
        if(next==level&&nextCharging==charging)return;
        level=next;charging=nextCharging;
        try{
            // Match AODBatteryMeterView: external percentage, no power-save tint
            // or digit inside the icon. Android does not expose MIUI fast-charge tiers.
            if(level>=0)iconUpdate.invoke(icon,level,false,false,false,charging,false);
            refreshLabels();if(onChanged!=null)onChanged.run();
        }catch(Exception|LinkageError error){unavailable(error);}
    }
    void refreshLabels(){
        container.setVisibility(level>=0&&!failed?VISIBLE:GONE);
        if(level<0||failed)return;
        String name=getResources().getConfiguration().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?
                "battery_level_rtl":"battery_level";
        int format=getResources().getIdentifier(name,"string","com.miui.aod");
        text.setText(getResources().getString(format,level));
        int description=getResources().getIdentifier(charging?"accessibility_battery_level_charging":
                "accessibility_battery_level","string","com.miui.aod");
        icon.setContentDescription(getResources().getString(description,level));
    }
    private void unavailable(Throwable error){
        if(active){active=false;host.unregisterReceiver(receiver);}
        failed=true;container.setVisibility(GONE);
        android.util.Log.w("OpenAliveXiaomi","Battery display unavailable",error);
    }
    static int percentage(int level,int scale){
        return level<0||scale<=0||level>scale?-1:(int)Math.round(100.0*level/scale);
    }
}
