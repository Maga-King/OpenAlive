package org.aliveclean;

import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import java.lang.reflect.Modifier;
import org.json.JSONObject;

/** Native ColorOS editor UI, editing only the outer host's current draft. */
final class NativeClockEditor {
    static View findRoot(View child){
        for(View view=child;view!=null;){
            if(view.getClass().getName().equals("com.oplus.keyguard.ui.KeyguardPluginViewRoot"))return view;
            ViewParent parent=view.getParent();view=parent instanceof View?(View)parent:null;
        }
        return null;
    }
    static void extendStockPanel(Object editor,android.widget.LinearLayout nativePanel)throws Exception{
        extendStockPanel(editor,nativePanel,null);
    }
    static void extendStockPanel(Object editor,android.widget.LinearLayout nativePanel,AssetManager testAssets)throws Exception{
        View clockRoot=(View)editor.getClass().getMethod("getViewRoot").invoke(editor);
        View outer=findRoot(clockRoot);
        if(outer==null)return;
        // The outer view uses clock.base's resource context. Its cache belongs
        // to a different UID; use the native clock's actual host for IO/services.
        Context host=(Context)clockRoot.getClass().getMethod("getHostContext").invoke(clockRoot);
        AssetManager assets=testAssets==null?host.createPackageContext("org.aliveclean",0).getAssets():testAssets;
        Context originalContext=NativeClockProvider.resourceContext(host,assets);
        NativeClockStylePanel extra=cards(nativePanel.getContext(),originalContext,findHost(outer),()->{
            try{dismissForSwitch((Dialog)editor.getClass().getMethod("getBottomSheetDialog").invoke(editor));}
            catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        },()->notifyEdited(outer));
        nativePanel.setOrientation(android.widget.LinearLayout.VERTICAL);
        nativePanel.addView(extra,new android.widget.LinearLayout.LayoutParams(-1,-2));
    }
    static void dismissForSwitch(Dialog dialog){
        if(dialog==null)return;
        try{dialog.getClass().getMethod("dismiss",boolean.class).invoke(dialog,false);}
        catch(ReflectiveOperationException unavailable){dialog.dismiss();}
    }
    static void notifyEdited(View child){
        notifyHost(child,"renderFinish",null);
        notifyHost(child,"onStyleDataEdited",null);
    }
    private static void notifyHost(View child,String event,android.os.Bundle data){
        View root=findRoot(child);
        if(root==null)return;
        try{
            // Use the outer host's router: the previous provider is released by
            // setStyleData, and a newly selected stock provider has no custom commands.
            java.lang.reflect.Field router=root.getClass().getDeclaredField("i");
            router.setAccessible(true);
            Object callback=router.get(root);
            android.os.Bundle message=data==null?new android.os.Bundle():new android.os.Bundle(data);
            message.putInt("commandDestination",1);
            callback.getClass().getMethod("onCall",String.class,android.os.Bundle.class).invoke(callback,event,message);
        }catch(ReflectiveOperationException unsupported){throw new IllegalStateException("Native clock editor event: "+event,unsupported);}
    }
    static NativeClockEditSession.Host findHost(View child){
        for(View view=child;view!=null;){
            if(view.getClass().getName().equals("com.oplus.keyguard.ui.KeyguardPluginViewRoot")){
                final View root=view;
                return new NativeClockEditSession.Host(){
                    @Override public String read()throws Exception{return (String)root.getClass().getMethod("getStyleData").invoke(root);}
                    @Override public void write(String json)throws Exception{
                        JSONObject before=new JSONObject(read()),after=new JSONObject(json);
                        boolean switching=!before.getString("pkg").equals(after.getString("pkg"));
                        android.os.Bundle wallpaper=switching?captureWallpaper(root,before.getString("pkg")):null;
                        root.getClass().getMethod("setStyleData",String.class).invoke(root,json);
                        if(wallpaper!=null){
                            Object container=root.getClass().getMethod("getClockPluginContainer").invoke(root);
                            command(container,"setWallpaperBitmap",wallpaper);
                        }
                    }
                };
            }
            ViewParent parent=view.getParent();view=parent instanceof View?(View)parent:null;
        }
        return null;
    }

    static Object command(Object container,String name,android.os.Bundle data)throws ReflectiveOperationException{
        android.os.Bundle message=data==null?new android.os.Bundle():new android.os.Bundle(data);
        message.putBoolean("isCacheCommand",false);
        return container.getClass().getMethod("k",String.class,android.os.Bundle.class).invoke(container,name,message);
    }
    private static android.os.Bundle captureWallpaper(View root,String id)throws Exception{
        Object container=root.getClass().getMethod("getClockPluginContainer").invoke(root);
        if(NativeClockProvider.contains(id)){
            android.os.Bundle value=(android.os.Bundle)command(container,"openAliveGetWallpaper",null);
            return value!=null&&value.getParcelable("wallpaperBitmap")!=null?value:null;
        }
        View view=(View)container.getClass().getMethod("getView",int.class).invoke(container,1);
        if(view==null)return null;
        try{
            Object clock=view.getClass().getMethod("getClockContainer").invoke(view);
            Object controller=clock.getClass().getMethod("getColorController").invoke(clock);
            Bitmap bitmap=(Bitmap)controller.getClass().getMethod("getScreenShotBitmap").invoke(controller);
            if(bitmap==null||bitmap.isRecycled())return null;
            android.os.Bundle result=new android.os.Bundle(),options=new android.os.Bundle();
            Object state=view.getClass().getMethod("getRenderedViewState").invoke(view);
            Object info=state==null?null:state.getClass().getMethod("getWallpaperColorInfo").invoke(state);
            if(info!=null){
                for(String key:new String[]{"wallpaperColor","darkWallpaperColor","lightWallpaperColor","wallpaperScene"})
                    options.putInt(key,(Integer)info.getClass().getMethod("get"+Character.toUpperCase(key.charAt(0))+key.substring(1)).invoke(info));
                options.putBoolean("isWallpaperDark",(Boolean)info.getClass().getMethod("isWallpaperDark").invoke(info));
            }
            // The native sampler may recycle its input when its provider is
            // released. This one-time transfer owns a separate immutable copy.
            result.putParcelable("wallpaperBitmap",bitmap.copy(Bitmap.Config.ARGB_8888,false));result.putBundle("wallpaperBitmapOptionData",options);return result;
        }catch(NoSuchMethodException otherNativeStyle){return null;}
    }

    static NativeClockStylePanel cards(Context nativeContext,Context originalContext,
            NativeClockEditSession.Host host,Runnable beforeSelect,Runnable afterSelect)throws Exception{
        JSONObject outer=new JSONObject(host.read());
        String selected=outer.getString("pkg");
        JSONObject inner=new JSONObject(outer.getString("clockStyleConfig"));
        String fallback=NativeClockProvider.contains(selected)?inner.optString("nativeReturnStyle",""):
                new JSONObject().put("pkg",selected).put("clockStyleConfig",outer.getString("clockStyleConfig")).toString();
        NativeClockEditSession session=new NativeClockEditSession(host);
        NativeClockStylePanel panel=new NativeClockStylePanel(nativeContext,selected,(id,config)->{
            try{
                if(NativeClockProvider.contains(id)&&!NativeClockAvailability.ready(originalContext)){
                    android.widget.Toast.makeText(nativeContext,NativeClockAvailability.unavailableMessage(),android.widget.Toast.LENGTH_LONG).show();
                    return false;
                }
                beforeSelect.run();session.select(id,config);afterSelect.run();return true;
            }catch(Exception failure){
                android.widget.Toast.makeText(nativeContext,"时钟切换失败，已保留原样式",android.widget.Toast.LENGTH_SHORT).show();
                return false;
            }
        });
        for(int style:new int[]{2,0}){
            String id=style==2?NativeFlymeClockPlugin.ID:NativeFlymeClockPlugin.HORIZONTAL_ID;
            JSONObject config=styleConfig(id,inner);
            if(!fallback.isEmpty())config.put("nativeReturnStyle",fallback);
            Bitmap thumb;
            try(OfficialClockFace face=new OfficialClockFace(originalContext,false)){
                face.layoutStyle(style);
                java.util.Calendar time=java.util.Calendar.getInstance();time.set(2026,8,24,18,30);
                face.update(time.getTimeInMillis(),time.getTimeZone(),true);
                // Measure the original controls at device width before scaling the
                // whole scene. Measuring at thumbnail width clips horizontal digits.
                int width=originalContext.getResources().getDisplayMetrics().widthPixels;
                int height=Math.round(width*(900f/540f));
                face.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.AT_MOST));
                face.layout(0,0,width,face.getMeasuredHeight());
                thumb=Bitmap.createBitmap(540,900,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(thumb);canvas.scale(540f/width,540f/width);face.draw(canvas);
            }
            panel.add(id,style==2?"魅族 · 纵向时钟":"魅族 · 横向时钟",config.toString(),thumb);
        }
        {
            String id=NativeFlymeArtworkPlugin.PERSPECTIVE;
            JSONObject config=styleConfig(id,inner);
            if(!fallback.isEmpty())config.put("nativeReturnStyle",fallback);
            Bitmap thumb;
            try(java.io.InputStream in=originalContext.getAssets().open("native-clock/previews/flyme-perspective.png")){
                thumb=android.graphics.BitmapFactory.decodeStream(in);
            }
            if(thumb==null)throw new IllegalStateException("Original Flyme artwork preview unavailable");
            panel.add(id,"魅族 · 透视数字",config.toString(),thumb);
        }
        for(NativeHyperOsStyles.Style style:NativeHyperOsStyles.ALL){
            String id=style.id;
            JSONObject config=styleConfig(id,inner);
            if(!fallback.isEmpty())config.put("nativeReturnStyle",fallback);
            Bitmap thumb;
            try(java.io.InputStream in=originalContext.getAssets().open("native-clock/previews/"+style.preview)){
                thumb=android.graphics.BitmapFactory.decodeStream(in);
            }
            if(thumb==null)throw new IllegalStateException("Original HyperOS preview unavailable");
            panel.add(id,style.title,config.toString(),thumb);
        }
        if(NativeClockProvider.contains(selected)&&!fallback.isEmpty()){
            JSONObject previous=new JSONObject(fallback);
            if(!NativeClockProvider.contains(previous.getString("pkg")))
                panel.addText(previous.getString("pkg"),"原系统时钟",previous.getString("clockStyleConfig"));
        }
        return panel;
    }

    private static JSONObject styleConfig(String id,JSONObject current)throws Exception{
        int mode=NativeOriginalClockPlugin.colorMode(current);if(mode<1)mode=2;
        JSONObject config=new JSONObject().put("version",1).put("style",id)
                .put("color",current.optInt("color",current.optInt("primaryColor",0xffffffff)));
        NativeOriginalClockPlugin.writeColorMode(config,mode);
        org.json.JSONArray depth=current.optJSONArray("primaryColorDepthHSL");
        if(depth!=null&&depth.length()==3)config.put("primaryColorDepthHSL",new org.json.JSONArray(depth.toString()));
        return config;
    }

    static Dialog show(Context hostContext,Context moduleContext,View root,Runnable changed)throws Exception{
        NativeClockEditSession.Host host=findHost(root);
        View outer=findRoot(root);
        // A style switch releases the old provider and its callback. Resolve the
        // current provider through the surviving outer host after every edit.
        Runnable committed=()->{notifyEdited(outer);changed.run();};
        if(host==null)throw new IllegalStateException("Native clock draft is not attached");
        Context plugin=hostContext.createPackageContext("com.oplus.keyguard.personality.clocks",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        Context nativeContext=new PanelContext(hostContext,plugin);
        ClassLoader loader=nativeContext.getClassLoader();
        int theme=loader.loadClass("com.support.dialog.R$style").getField("Theme_COUI_Dialog").getInt(null);
        Context themed=new ContextThemeWrapper(nativeContext,theme);
        Class<?> overlay=loader.loadClass("s3.b");
        for(java.lang.reflect.Field field:loader.loadClass("s3.a").getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers())&&field.getType()==overlay){field.setAccessible(true);overlay.getMethod("a",Context.class).invoke(field.get(null),themed);}
        }
        int sheet=loader.loadClass("com.support.panel.R$style").getField("COUIHandleBottomSheetDialog").getInt(null);
        Class<?> type=loader.loadClass("com.oplus.keyguard.clock.common.dialog.EditPanelBuilder");
        Object builder=type.getConstructor(Context.class,int.class,boolean.class).newInstance(hostContext,sheet,false);
        type.getMethod("setAgentContext",Context.class).invoke(builder,themed);
        type.getMethod("initPanelRoot").invoke(builder);
        type.getMethod("setPanelTitleContent",String.class).invoke(builder,"时钟与颜色");
        type.getMethod("setPanelDefaultHeight",int.class).invoke(builder,(int)(hostContext.getResources().getDisplayMetrics().heightPixels*.55f));
        Dialog[] dialog={null};
        NativeClockStylePanel panel=cards(themed,moduleContext,host,()->dismissForSwitch(dialog[0]),committed);
        type.getMethod("addCustomContent",View.class).invoke(builder,panel);
        addMaterialPanel(builder,type,loader,host,outer,committed,themed);
        dialog[0]=(Dialog)type.getMethod("safeShow").invoke(builder);
        if(dialog[0]!=null&&dialog[0].isShowing()){
            android.os.Bundle state=new android.os.Bundle();
            state.putInt("clockStyleDialogPanelType",0);
            state.putFloat("editPanelHeight",0f);
            dialog[0].setOnDismissListener(ignored->notifyHost(outer,"onStyleDialogHidden",state));
            // ColorOS marks the draft editable here, just as it does for widgets.
            notifyHost(outer,"onStyleDialogShown",state);
        }
        return dialog[0];
    }

    private static void addMaterialPanel(Object builder,Class<?> type,ClassLoader loader,
            NativeClockEditSession.Host host,View root,Runnable changed,Context themed)throws Exception{
        JSONObject current=new JSONObject(host.read());
        if(!NativeClockProvider.contains(current.getString("pkg")))return;
        JSONObject config=new JSONObject(current.getString("clockStyleConfig"));
        android.widget.TextView soft=new android.widget.TextView(themed);
        soft.setText("特殊效果1");soft.setContentDescription("特殊效果1");soft.setTag("openalive_special_clock_color");
        soft.setGravity(android.view.Gravity.CENTER);soft.setTextSize(16);soft.setTextColor(0xfff4e4eb);
        float density=root.getResources().getDisplayMetrics().density;
        int softPad=Math.round(16*density);soft.setPadding(softPad,softPad,softPad,softPad);
        android.graphics.drawable.StateListDrawable softBackground=new android.graphics.drawable.StateListDrawable();
        for(boolean selected:new boolean[]{true,false}){
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xff345473,0xffa7857c,0xff577b6a});
            background.setCornerRadius(16*density);
            background.setStroke(Math.round((selected?2:1)*density),selected?0xff168cff:0x336f7b89);
            softBackground.addState(selected?new int[]{android.R.attr.state_selected}:new int[0],background);
        }
        soft.setBackground(softBackground);soft.setSelected(NativeOriginalClockPlugin.colorMode(config)==5);
        android.widget.TextView wallpaperSoft=new android.widget.TextView(themed);
        wallpaperSoft.setText("柔和渐变");wallpaperSoft.setContentDescription("柔和渐变，颜色跟随壁纸");
        wallpaperSoft.setTag("openalive_soft_clock_color");wallpaperSoft.setGravity(android.view.Gravity.CENTER);
        wallpaperSoft.setTextSize(16);wallpaperSoft.setTextColor(0xffeeeeee);wallpaperSoft.setPadding(softPad,softPad,softPad,softPad);
        android.graphics.drawable.StateListDrawable followBackground=new android.graphics.drawable.StateListDrawable();
        for(boolean selected:new boolean[]{true,false}){
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
            background.setColor(0xff33353b);background.setCornerRadius(16*density);
            background.setStroke(Math.round((selected?2:1)*density),selected?0xff168cff:0x336f7b89);
            followBackground.addState(selected?new int[]{android.R.attr.state_selected}:new int[0],background);
        }
        wallpaperSoft.setBackground(followBackground);wallpaperSoft.setSelected(NativeOriginalClockPlugin.colorMode(config)==6);
        Class<?> listener=loader.loadClass("com.oplus.keyguard.clock.common.callback.OnColorSelectedListener");
        Object selection=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{listener},(proxy,method,args)->{
            if(!method.getName().equals("onColorSelected"))return null;
            JSONObject outer=new JSONObject(host.read());
            if(!NativeClockProvider.contains(outer.getString("pkg")))return null;
            JSONObject data=new JSONObject(outer.getString("clockStyleConfig"));
            int mode=(Integer)args[0];
            soft.setSelected(false);
            wallpaperSoft.setSelected(false);
            NativeOriginalClockPlugin.writeColorMode(data,mode==0?1:mode==1?2:mode==2?4:3);
            data.put("color",(Integer)args[1]);
            org.json.JSONArray hsl=new org.json.JSONArray();
            for(float component:(float[])args[2])hsl.put(component);
            data.put("primaryColorDepthHSL",hsl);
            outer.put("clockStyleConfig",data.toString());host.write(outer.toString());changed.run();return null;
        });
        android.os.Bundle info=colorInfo(root);
        Class<?> infoType=loader.loadClass("com.oplus.keyguard.clock.common.color.wallpaper.WallpaperColorInfo");
        Object wallpaper=infoType.getConstructor(int.class,int.class,int.class,int.class,int.class,int.class,int.class,boolean.class)
                .newInstance(info.getInt("wallpaperColor",-1),info.getInt("darkWallpaperColor",-1),info.getInt("lightWallpaperColor",-1),
                        info.getInt("wallpaperScene",0),0,0,0,info.getBoolean("isWallpaperDark",true));
        float[] depth={0,0,1};
        org.json.JSONArray saved=config.optJSONArray("primaryColorDepthHSL");
        if(saved!=null&&saved.length()==3)for(int i=0;i<3;i++)depth[i]=(float)saved.getDouble(i);
        int mode=NativeOriginalClockPlugin.colorMode(config);
        type.getMethod("setupColor",int[].class,int.class,int.class,float[].class,String.class,
                loader.loadClass("com.oplus.keyguard.clock.common.color.wallpaper.WallpaperColorInfo"),
                loader.loadClass("com.oplus.keyguard.clock.common.dialog.EditPanelBuilder$ReusablePanelViews"),listener,int[].class)
                .invoke(builder,new int[]{0,2,1,3},mode>=5?-1:mode==1?0:mode==4?2:mode==3?3:1,config.optInt("color",0xffffffff),
                        depth,android.text.format.DateFormat.format("HH:mm",System.currentTimeMillis()).toString(),wallpaper,null,selection,new int[0]);
        soft.setOnClickListener(v->{
            try{
                rebindColor(builder,type,loader,infoType,wallpaper,-1);
                JSONObject outer=new JSONObject(host.read()),data=new JSONObject(outer.getString("clockStyleConfig"));
                NativeOriginalClockPlugin.writeColorMode(data,5);data.remove("primaryColorDepthHSL");
                outer.put("clockStyleConfig",data.toString());host.write(outer.toString());soft.setSelected(true);wallpaperSoft.setSelected(false);changed.run();
            }catch(Exception failure){throw new IllegalStateException("Select soft clock color",failure);}
        });
        wallpaperSoft.setOnClickListener(v->{
            try{
                rebindColor(builder,type,loader,infoType,wallpaper,-1);
                JSONObject outer=new JSONObject(host.read()),data=new JSONObject(outer.getString("clockStyleConfig"));
                NativeOriginalClockPlugin.writeColorMode(data,6);data.remove("primaryColorDepthHSL");
                outer.put("clockStyleConfig",data.toString());host.write(outer.toString());wallpaperSoft.setSelected(true);soft.setSelected(false);changed.run();
            }catch(Exception failure){throw new IllegalStateException("Select wallpaper soft material",failure);}
        });
        type.getMethod("addCustomContent",View.class).invoke(builder,wallpaperSoft);
        type.getMethod("addCustomContent",View.class).invoke(builder,soft);
        android.widget.TextView reset=new android.widget.TextView(themed);
        reset.setText("恢复默认颜色");reset.setContentDescription("恢复默认颜色");reset.setTag("openalive_reset_clock_color");
        reset.setTextSize(14);reset.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad=Math.round(16*density);reset.setPadding(pad,pad,pad,pad);
        reset.setTextColor(0xffb6bbc5);
        android.graphics.drawable.Drawable icon=new android.graphics.drawable.Drawable(){
            private final android.graphics.Paint paint=new android.graphics.Paint(3);
            public void draw(Canvas canvas){
                android.graphics.Rect b=getBounds();float r=b.width()*.36f,x=b.exactCenterX(),y=b.exactCenterY();
                paint.setColor(reset.getCurrentTextColor());paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.5f*density);
                canvas.drawCircle(x,y,r,paint);float d=r*.7071f;canvas.drawLine(x-d,y+d,x+d,y-d,paint);
            }
            public void setAlpha(int alpha){paint.setAlpha(alpha);}
            public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);}
            public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
        icon.setBounds(0,0,Math.round(28*density),Math.round(28*density));
        reset.setCompoundDrawablesRelative(icon,null,null,null);reset.setCompoundDrawablePadding(Math.round(10*density));
        reset.setOnClickListener(v->{
            try{
                // Return to automatic contrast, without retaining a manual hue
                // or depth adjustment. Rebind the native picker as well as the draft.
                rebindColor(builder,type,loader,infoType,wallpaper,1);soft.setSelected(false);wallpaperSoft.setSelected(false);
                JSONObject outer=new JSONObject(host.read());
                JSONObject data=new JSONObject(outer.getString("clockStyleConfig"));
                NativeOriginalClockPlugin.writeColorMode(data,2);data.put("color",0xffffffff);data.remove("primaryColorDepthHSL");
                outer.put("clockStyleConfig",data.toString());host.write(outer.toString());changed.run();
            }catch(Exception failure){throw new IllegalStateException("Reset clock color",failure);}
        });
        type.getMethod("addCustomContent",View.class).invoke(builder,reset);
    }

    private static void rebindColor(Object builder,Class<?> type,ClassLoader loader,Class<?> infoType,Object wallpaper,int mode)throws Exception{
        java.lang.reflect.Field colorPanel=type.getDeclaredField("colorSettingsPanel");colorPanel.setAccessible(true);
        Object nativePanel=colorPanel.get(builder);
        java.lang.reflect.Field controllerField=nativePanel.getClass().getDeclaredField("colorSettingsController");controllerField.setAccessible(true);
        Object controller=controllerField.get(nativePanel);
        java.lang.reflect.Field action=controller.getClass().getSuperclass().getDeclaredField("onNotifyColorSelectedAction");action.setAccessible(true);
        ((View)nativePanel).removeCallbacks((Runnable)action.get(controller));
        Object scroll=type.getMethod("getPanelCOUIScrollView").invoke(builder);
        nativePanel.getClass().getMethod("setupColor",int[].class,int.class,int.class,float[].class,
                loader.loadClass("com.coui.appcompat.scrollview.COUIScrollView"),infoType,int[].class)
                .invoke(nativePanel,new int[]{0,2,1,3},mode,0xffffffff,new float[]{0,0,1},scroll,wallpaper,new int[0]);
    }

    private static android.os.Bundle colorInfo(View root)throws Exception{
        Object container=root.getClass().getMethod("getClockPluginContainer").invoke(root);
        Object value=command(container,"openAliveGetColorInfo",null);
        return value instanceof android.os.Bundle?(android.os.Bundle)value:new android.os.Bundle();
    }

    static final class PanelContext extends ContextWrapper{
        private final Context plugin;
        private final Resources.Theme theme;
        private LayoutInflater inflater;
        PanelContext(Context host,Context plugin){super(host);this.plugin=plugin;theme=plugin.getResources().newTheme();}
        @Override public Resources getResources(){return plugin.getResources();}
        @Override public AssetManager getAssets(){return plugin.getAssets();}
        @Override public Resources.Theme getTheme(){return theme;}
        @Override public ClassLoader getClassLoader(){return plugin.getClassLoader();}
        @Override public Object getSystemService(String name){
            if(LAYOUT_INFLATER_SERVICE.equals(name)){
                if(inflater==null)inflater=LayoutInflater.from(getBaseContext()).cloneInContext(this);
                return inflater;
            }
            return super.getSystemService(name);
        }
    }
    private NativeClockEditor(){}
}
