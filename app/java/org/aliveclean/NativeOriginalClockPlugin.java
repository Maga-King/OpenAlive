package org.aliveclean;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.TimeZone;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import org.json.JSONObject;

/** Shared native transport for independent original clock renderers. */
class NativeOriginalClockPlugin implements IntFunction<View>, BiFunction<String, Bundle, Bundle>,
        BiPredicate<BiFunction<String, Bundle, Bundle>, Boolean> {
    final String id;
    final LinearLayout root;
    private final NativeClockFaces faces;
    private final boolean vertical;
    final FrameLayout clockContainer;
    private final NativeClockWidgetBridge widgets=new NativeClockWidgetBridge();
    private final NativeClockGeometry geometry;
    private BiFunction<String, Bundle, Bundle> callback;
    private int color = 0xffffffff;
    private boolean released, blocked;
    private Bundle pendingLayout;
    private boolean aod = true;
    private boolean compact;
    private int uiState=5;
    private int nativeClockSize=1;
    private String nativeReturnStyle="";
    private android.app.Dialog editor;
    private NativeClockEditBox editBox;
    private boolean editing;
    private int burnX=-1,burnY=-1;
    private final NativeClockSceneTransition transition;
    private final NativeClockMaterial material;
    private boolean glass;
    private int coloringType;
    private org.json.JSONArray colorDepth;
    private Bundle wallpaperInfo=new Bundle();

    NativeOriginalClockPlugin(Context context,String id,NativeClockFaces faces,boolean vertical) throws Exception {
        this.id=id;this.faces=faces;this.vertical=vertical;
        geometry=new NativeClockGeometry(context);
        widgets.style(vertical?7:0);
        widgets.color(2,color,true);
        widgets.beginScene(uiState,nativeClockSize,false);widgets.settledScene();
        root=new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        updateInset();
        clockContainer=new FrameLayout(context);
        // Stable view contract shared with the retention AOD host across class loaders.
        // Only clock faces live here; the system widget container is a sibling.
        clockContainer.setTag("org.aliveclean.native_clock_content");
        faces.attach(clockContainer);
        faces.scene(uiState,compact);
        root.addView(clockContainer, new LinearLayout.LayoutParams(-1, -2));
        transition=new NativeClockSceneTransition(root,clockContainer,faces);
        material=new NativeClockMaterial(root);
        clockContainer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> notifyLayout());
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View v){widgets.recruit();}
            @Override public void onViewDetachedFromWindow(View v){transition.cancel();root.removeCallbacks(recoverVisibleWidgets);}
        });
    }

    @Override public View apply(int type) {
        if (released) return null;
        if (type == 1) return root;
        if (type == 7) return clockContainer;
        if (type == 8) return activeFace();
        // Widget IDs must pass through to the host's independent widget plugin.
        return null;
    }

    @Override public boolean test(BiFunction<String, Bundle, Bundle> listener, Boolean register) {
        if (released || listener == null || register == null) return false;
        if (register) { callback = listener; widgets.callback(listener);notifyLayout();if(root.isAttachedToWindow())widgets.recruit();requestWallpaper();return true; }
        if (callback != listener) return false;
        callback = null;widgets.callback(null); return true;
    }

    @Override public Bundle apply(String command, Bundle args) {
        if (released) return null;
        Bundle out = new Bundle();
        switch (command) {
            case "setStyleData":
                if (args == null || !args.containsKey("styleData")) return null;
                try {
                    JSONObject json = new JSONObject(args.getString("styleData"));
                    if (json.getInt("version") != 1 || !id.equals(json.getString("style"))) return null;
                    int value = json.getInt("color");
                    String previous=json.optString("nativeReturnStyle","");
                    if(!previous.isEmpty()){
                        JSONObject previousStyle=new JSONObject(previous);
                        if(NativeClockProvider.contains(previousStyle.getString("pkg")))return null;
                        new JSONObject(previousStyle.getString("clockStyleConfig"));
                    }
                    coloringType=colorMode(json);
                    colorDepth=json.optJSONArray("primaryColorDepthHSL");
                    glass=coloringType==1||coloringType>=5;
                    color=value;
                    updateColor();requestWallpaper();
                    nativeReturnStyle=previous;
                } catch (Exception invalid) { return null; }
                return null;
            case "getStyleData":
                try {
                    JSONObject config=new JSONObject().put("version",1).put("style",id).put("color",color);
                    if(coloringType!=0)writeColorMode(config,coloringType);
                    if(colorDepth!=null)config.put("primaryColorDepthHSL",colorDepth);
                    if(!nativeReturnStyle.isEmpty())config.put("nativeReturnStyle",nativeReturnStyle);
                    out.putString("styleData",config.toString());
                    return out;
                } catch (Exception impossible) { throw new AssertionError(impossible); }
            case "setTime":
                if (args != null) {
                    long time=args.getLong("time",System.currentTimeMillis());
                    boolean format=android.text.format.DateFormat.is24HourFormat(root.getContext());
                    faces.update(time,TimeZone.getDefault(),format);
                }
                return null;
            case "onClockStateChanged":
                View previous=activeFace();
                int previousState=uiState;
                boolean animate=args!=null&&args.getBoolean("isAnim",true)&&!blocked&&!editing&&root.isAttachedToWindow();
                // Sentinels from the outer host carry no new size/scene.
                if(args==null)return null;
                int nextState=args.getInt("uiState",uiState),nextSize=args.getInt("clockSize",-1);
                if(nextSize!=0&&nextSize!=1&&nextSize!=2&&nextSize!=10)nextSize=nativeClockSize;
                boolean nextCompact=nextSize==0;
                if(nextState!=1&&nextState!=2&&nextState!=3&&nextState!=5)nextState=uiState;
                if(nextState==uiState&&nextSize==nativeClockSize)return null;
                RectF start=transition.capture();
                if(args != null && args.containsKey("clockSize")) {
                    // ColorOS SceneKt: 0=small, 1=big, 2=immersed, 10=unlocked.
                    compact=nextCompact;
                    nativeClockSize=nextSize;
                }
                if(args != null && args.containsKey("uiState")) {
                    int state=args.getInt("uiState");
                    if(state==1 || state==2 || state==3 || state==5) {
                        uiState=state;
                        aod=state==3 || state==5;
                        if(!aod){root.animate().cancel();root.setTranslationX(0);root.setTranslationY(0);widgets.translation(0,0);burnX=-1;burnY=-1;}
                    }
                }
                updateInset();
                faces.scene(uiState,compact);
                boolean animateScene=animate&&previousState!=1&&uiState!=1;
                widgets.beginScene(uiState,nativeClockSize,animateScene);
                material.scene(activeFace(),coloringType);
                updateEditBox(false);
                if(animateScene){
                    transition.start(previous,start,()->{material.settled();widgets.settledScene();recoverWidgets();});
                }else {widgets.settledScene();recoverWidgets();}
                requestWallpaper();
                return null;
            case "setWallpaperBitmap":
                Bundle options=args==null?null:args.getBundle("wallpaperBitmapOptionData");
                wallpaperInfo=options==null?new Bundle():options.deepCopy();
                material.wallpaper(args==null?null:(android.graphics.Bitmap)args.getParcelable("wallpaperBitmap"));
                updateColor();return null;
            case "openAliveGetColorInfo":return wallpaperInfo.deepCopy();
            case "openAliveGetWallpaper":
                out.putParcelable("wallpaperBitmap",material.wallpaper());out.putBundle("wallpaperBitmapOptionData",wallpaperInfo.deepCopy());return out;
            case "setAodUiDeBurnin":
                // AOD coordinates are absolute translations, not cumulative deltas.
                // -1 is the native unknown sentinel. A lockscreen must not inherit them.
                if(args!=null&&aod){
                    burnX=args.getInt("aodTranslationX",-1);burnY=args.getInt("aodTranslationY",-1);
                    if(burnX!=-1&&burnY!=-1){
                        root.animate().cancel();
                        int duration=Math.max(0,args.getInt("duration",50));
                        float targetY=burnY-root.getPaddingTop();
                        if(duration==0){root.setTranslationX(burnX);root.setTranslationY(targetY);widgets.translation(burnX,targetY);}
                        else root.animate().translationX(burnX).translationY(targetY).setDuration(duration)
                                .setUpdateListener(animation->widgets.translation(root.getTranslationX(),root.getTranslationY())).start();
                    }
                }
                return null;
            case "getClockVisibleRect":
                RectF ink = new RectF();
                faces.numberBounds(ink);
                android.graphics.Matrix transform = new android.graphics.Matrix();
                activeFace().transformMatrixToGlobal(transform); transform.mapRect(ink);
                Rect rect = new Rect(); ink.roundOut(rect); out.putParcelable("visibleRect", rect);
                return out;
            case "onWidgetsPluginReady":widgets.ready();notifyLayout();recoverWidgets();return null;
            case "onViewStateChanged":
                editing=args!=null&&args.getInt("viewState",0)==1;
                widgets.viewState(editing,args!=null&&args.getBoolean("isAnim"));
                updateEditBox(args!=null&&args.getBoolean("isAnim"));
                if(!editing&&editor!=null){editor.dismiss();editor=null;}
                return null;
            case "requestShowEditPanel":
                if(editor!=null&&editor.isShowing())return null;
                try{
                    editor=NativeClockEditor.show(root.getContext(),root.getContext(),root,()->{});
                }catch(Exception unavailable){
                    android.widget.Toast.makeText(root.getContext(),"时钟编辑页暂时无法打开",android.widget.Toast.LENGTH_SHORT).show();
                }
                return null;
            case "requestHideEditPanel":if(editor!=null)editor.dismiss();editor=null;return null;
            case "openAliveNotifyStyleEdited":
                if(callback!=null){Bundle edited=new Bundle();edited.putInt("commandDestination",1);callback.apply("onStyleDataEdited",edited);}
                return null;
            case "onWidgetsPluginReleased":widgets.released();notifyLayout();return null;
            case "onWidgetDataRowChanged":
            case "updateDisplayWidgetContainerSize":widgets.rows(args);notifyLayout();return null;
            case "blockRender": blocked = true;widgets.blocked(true); return null;
            case "unblockRender":
                blocked = false;
                widgets.blocked(false);
                if (pendingLayout != null && callback != null) callback.apply("onClockLayoutCalculated", pendingLayout.deepCopy());
                pendingLayout = null; return null;
            case "release":
                released = true; callback = null; pendingLayout = null; transition.cancel();material.close();faces.close();
                root.removeCallbacks(recoverVisibleWidgets);
                root.animate().cancel();
                NativeClockEditor.dismissForSwitch(editor);editor=null;
                if(editBox!=null){editBox.close();editBox=null;}
                widgets.close();clockContainer.removeAllViews();root.removeAllViews(); return null;
            default: return null;
        }
    }

    private View activeFace(){return faces.active();}
    private final NativeClockWidgetRecovery widgetRecovery=new NativeClockWidgetRecovery();
    private final Runnable recoverVisibleWidgets=this::retryWidgetsAtRest;
    private void retryWidgetsAtRest(){
        if(!released&&!blocked&&!editing&&root.isAttachedToWindow()&&(uiState==2||uiState==5))widgetRecovery.visible(root);
    }
    private void recoverWidgets(){root.removeCallbacks(recoverVisibleWidgets);root.post(recoverVisibleWidgets);}
    private void updateColor(){
        int mode=coloringType==0?2:coloringType;
        boolean dark=wallpaperInfo.getBoolean("isWallpaperDark",true);
        int value=glass?0xffffffff:mode==2?(dark?0xffffffff:0xff000000):
                mode==4?wallpaperInfo.getInt("wallpaperColor",color):color;
        try{faces.color(value);}catch(Exception failure){throw new IllegalStateException("Clock color update",failure);}
        material.scene(activeFace(),mode);
        widgets.color(mode>=5?1:mode,value,dark);
    }
    private void requestWallpaper(){
        if(!material.hasWallpaper()&&callback!=null){
            Bundle request=new Bundle();request.putInt("commandDestination",1);callback.apply("requestWallpaperBitmap",request);
        }
    }
    static int colorMode(JSONObject config){
        int nativeMode=config.optInt("coloringType",0);
        int effect=config.optInt("openAliveColorEffect",0);
        int mode=nativeMode==1&&effect>=5&&effect<=6?effect:nativeMode;
        return mode>=0&&mode<=6?mode:0;
    }
    static void writeColorMode(JSONObject config,int mode)throws org.json.JSONException{
        // SystemUI also parses coloringType for widgets and affordances. Never
        // leak our extra material IDs into that native four-mode contract.
        config.put("coloringType",mode>=5?1:mode);
        if(mode>=5)config.put("openAliveColorEffect",mode);else config.remove("openAliveColorEffect");
    }
    boolean sceneAnimating(){return transition.running();}
    boolean materialApplied(){return material.applied();}

    private void updateEditBox(boolean animation){
        if(!editing&&editBox==null)return;
        try{
            if(editBox==null)editBox=new NativeClockEditBox(root.getContext(),clockContainer,()->apply("requestShowEditPanel",null));
            editBox.update(activeFace(),editing,animation);
        }catch(Exception error){android.util.Log.w("OpenAliveClock","Native clock edit outline unavailable",error);}
    }

    private void updateInset(){
        int top=geometry.top(aod,compact,vertical);
        root.setPadding(0,top,0,0);
        if(aod&&burnX!=-1&&burnY!=-1){
            root.animate().cancel();root.setTranslationX(burnX);root.setTranslationY(burnY-top);
            widgets.translation(burnX,burnY-top);
        }
    }

    private void notifyLayout() {
        if (released || clockContainer.getHeight() == 0) return;
        Bundle args = new Bundle(); args.putInt("commandDestination", 1);
        int width=clockContainer.getWidth();
        int smallHeight=contentHeight(faces.lock(true),width),bigHeight=contentHeight(faces.lock(false),width);
        // ColorOS always expects small-clock metrics plus a separate big-clock
        // bundle, regardless of which scene is currently visible.
        int smallWidgets=geometry.widgetExtent(widgets.occupiedRows(false));
        int bigWidgets=geometry.widgetExtent(widgets.occupiedRows(true));
        args.putInt("clockHeight",smallHeight+smallWidgets);args.putInt("clockTop",geometry.top(false,true,false));
        Bundle big=new Bundle();big.putInt("bigClockCurHeight",bigHeight+bigWidgets);big.putInt("bigClockMinHeight",bigHeight+bigWidgets);
        big.putInt("bigClockTop",geometry.top(false,false,vertical));
        big.putInt("bigClockContentHeightAtCurFont",bigHeight);big.putInt("bigClockContentHeightAtMinFont",bigHeight);
        big.putInt("bigClockWidgetsHeight",bigWidgets);
        args.putBundle("bigClockPositionParams",big);
        // Native total heights include occupied widgets for notification avoidance;
        // content-only fields continue to describe the clock/date itself.
        if (blocked) pendingLayout = args;
        else if (callback != null) callback.apply("onClockLayoutCalculated", args.deepCopy());
        widgets.layout(geometry.widgetInset(),clockContainer.getBottom()+geometry.widgetGap(),geometry.widgetInset());
    }
    private static int contentHeight(View view,int width){
        if(view.getVisibility()!=View.VISIBLE){
            view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        }
        return view.getMeasuredHeight();
    }
}
