package org.aliveclean;

import android.os.Bundle;
import java.util.function.BiFunction;
import java.util.LinkedHashMap;
import java.util.Map;

/** Clock-to-widget protocol. Widget views and their content remain owned by ColorOS. */
final class NativeClockWidgetBridge {
    private BiFunction<String,Bundle,Bundle> callback;
    private int left,top,right,rows=1,columns=4;
    private int occupiedRows,originalRows;
    private boolean ready, measured, blocked;
    private Bundle last;
    private final Map<String,Bundle> desired=new LinkedHashMap<>();
    private final Map<String,Bundle> delivered=new LinkedHashMap<>();
    private boolean recruited;
    private boolean editing;
    private int nativeStyle;
    private int uiState=5,clockSize=1;
    private String scene="widgetSceneUninitialized";

    NativeClockWidgetBridge(){viewState(false,false);}

    void callback(BiFunction<String,Bundle,Bundle> value){callback=value;last=null;delivered.clear();recruited=false;flush();}
    void recruit(){
        if(callback==null||recruited||blocked)return;
        recruited=true;send("recruitWidgetPlugin",new Bundle());
    }
    void ready(){ready=true;last=null;delivered.clear();send("getWidgetsContainerState",new Bundle());flush();}
    void released(){ready=false;last=null;delivered.clear();recruited=false;}
    void close(){released();callback=null;desired.clear();}
    void blocked(boolean value){blocked=value;if(!blocked)flush();}
    void layout(int left,int top,int right){
        // Left/right are margins in ColorOS WidgetPosition, not rectangle edges.
        if(left<0||right<0||top<0)return;
        this.left=left;this.top=top;this.right=right;measured=true;publish();
    }
    void rows(Bundle args){
        if(args==null)return;
        if(args.containsKey("cardOccupiedRows")&&args.getInt("cardOccupiedRows")>=0){
            occupiedRows=args.getInt("cardOccupiedRows");originalRows=occupiedRows;rows=occupiedRows;
        }
        if(args.containsKey("originCardOccupiedRows")&&args.getInt("originCardOccupiedRows")>=0)
            originalRows=args.getInt("originCardOccupiedRows");
        if(args.containsKey("displayWidgetRows")&&args.getInt("displayWidgetRows")>=0)rows=args.getInt("displayWidgetRows");
        if(args.containsKey("displayWidgetCols"))columns=Math.max(1,args.getInt("displayWidgetCols"));
        publish();
    }
    int occupiedRows(boolean large){return ready?(large?originalRows:occupiedRows):0;}
    void translation(float x,float y){
        if(!Float.isFinite(x)||!Float.isFinite(y))return;
        Bundle args=new Bundle();args.putFloat("widgetsContainerTranslationX",x);args.putFloat("widgetsContainerTranslationY",y);
        state("setWidgetsContainerTranslation",args);
    }
    void color(int mode,int color,boolean dark){
        Bundle args=new Bundle();args.putInt("clockColoringMode",mode);args.putInt("clockPrimaryColor",color);
        args.putBoolean("clockShowOnDarkBackground",dark);args.putBoolean("quickWakeUpScenes",false);
        state("onClockColorConfigChanged",args);
    }
    void style(int nativeStyle){
        this.nativeStyle=nativeStyle;
        Bundle args=new Bundle();args.putInt("clockStyle",nativeStyle);
        // Native phase 3 is the fixed-size classic phase, not the stretchable large clock.
        args.putInt("clockPhaseState",3);state("updateWidgetClockStyleAndPhaseState",args);
    }
    void viewState(boolean value,boolean animate){
        if(editing!=value){editing=value;last=null;}
        Bundle args=new Bundle();args.putInt("viewState",value?1:0);args.putBoolean("isAnim",animate);
        state("onViewStateChanged",args);visibility();flush();
    }
    void beginScene(int uiState,int clockSize,boolean animate){
        this.uiState=uiState;this.clockSize=clockSize;
        String next=uiState==1?"widgetSceneUnlock":uiState==3?"widgetSceneAod":
                clockSize==2?"widgetSceneImmersed":clockSize==0?"widgetSceneSmallClock":"widgetSceneBigClock";
        Bundle args=new Bundle();args.putString("widgetAnimSourceScene",scene);args.putString("widgetAnimTargetScene",next);
        args.putInt("widgetAnimClockStyle",nativeStyle);args.putBoolean("widgetAnimIsAnimEnable",animate);
        args.putInt("widgetAnimTransitionType",0);
        scene=next;desired.remove("endWidgetAnim");delivered.remove("endWidgetAnim");
        visibility();state("startWidgetAnim",args);
    }
    private void visibility(){
        Bundle args=new Bundle();args.putBoolean("isVisible",editing||((uiState==2||uiState==5)&&clockSize!=2));
        args.putString("reason","uiState="+uiState+",clockSize="+clockSize);
        state("setWidgetsVisibility",args);
    }
    void settledScene(){
        Bundle args=new Bundle();args.putString("widgetAnimTargetScene",scene);
        state("endWidgetAnim",args);
    }
    private void state(String command,Bundle args){desired.put(command,args);flush();}
    private void flush(){
        if(!ready||blocked||callback==null)return;
        // Geometry comes before the final scene, so native visibility resolves against
        // the current clock/date extent. Snapshot protects synchronous widget callbacks.
        publish();
        for(Map.Entry<String,Bundle> item:new LinkedHashMap<>(desired).entrySet()){
            if(!ready||blocked||callback==null)return;
            Bundle prior=delivered.get(item.getKey());
            if(equal(prior,item.getValue()))continue;
            delivered.put(item.getKey(),item.getValue().deepCopy());
            send(item.getKey(),item.getValue().deepCopy());
        }
    }
    private static boolean equal(Bundle a,Bundle b){
        if(a==null||!a.keySet().equals(b.keySet()))return false;
        for(String key:b.keySet())if(!java.util.Objects.equals(a.get(key),b.get(key)))return false;
        return true;
    }
    private void publish(){
        if(!ready||!measured||blocked||callback==null)return;
        if(last!=null&&last.getInt("widgetsContainerPositionTop")==top&&last.getInt("widgetsContainerPositionLeft")==left
                &&last.getInt("widgetsContainerPositionRight")==right&&last.getInt("widgetsContainerRows")==rows
                &&last.getInt("widgetsContainerColumns")==columns)return;
        Bundle args=new Bundle();args.putInt("widgetsContainerRows",rows);args.putInt("widgetsContainerColumns",columns);
        args.putBoolean("widgetsContainerAutoCenter",false);
        args.putInt("widgetsContainerPositionLeft",left);args.putInt("widgetsContainerPositionRight",right);args.putInt("widgetsContainerPositionTop",top);
        args.putInt("addWidgetBtnTranslationY",0);
        // A clock replacement keeps the existing widget plugin. In the editor,
        // a size-only reason leaves its old padding until a card is edited.
        args.putBoolean("widgetsContainerForceMove",editing);
        args.putString("morphologyChangeReason",editing?"changeForClockStyleSwitch":
                last==null?"changeForInitialize":"changeForClockSizeSwitch");
        // Save before invoking the host: the native widget may synchronously report rows.
        last=args.deepCopy();send("onStyleWidgetsMorphologyChanged",args);
    }
    private void send(String command,Bundle args){
        if(callback==null)return;
        Bundle message=args.deepCopy();message.putInt("commandDestination",4);callback.apply(command,message);
    }
}
