package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.json.JSONObject;

/** Exercise the installed ColorOS transport and outer serializer without applying a style. */
public final class NativeClockTransportInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context app = getTargetContext();
            Context editor = app.createPackageContext("com.oplus.wallpapers",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Throwable[] failure = new Throwable[1];
            runOnMainSync(() -> { try { providerBoundary(app);check(app, editor.getClassLoader()); nativeContainer(app); } catch (Throwable error) { failure[0] = error; } });
            if (failure[0] != null) throw failure[0];
            result.putString("stream", "NATIVE_TRANSPORT_OK installed_proxy=true installed_container=true outer_codec=true independent_id=true callback=true scenes=true widget_protocol=true compact=true burnin=true release=true\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error)); finish(0, result);
        }
    }
    @SuppressWarnings("unchecked")
    private void providerBoundary(Context app)throws Exception{
        android.view.Display display=app.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY);
        Context visual=app.createDisplayContext(display);
        Context vendor=app.createPackageContext("com.oplus.keyguard.personality.clocks",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY).createDisplayContext(display);
        Class<?> providerType=vendor.getClassLoader().loadClass("com.oplus.keyguard.OplusStyleClockPluginProvider");
        // Also resolves exactly the inherited method used by the production hook.
        providerType.getMethod("apply",String.class);
        Method bridge=providerType.getMethod("apply",Object.class);
        require(bridge.isBridge()&&bridge.isSynthetic(),"installed Function transport bridge not resolved");
        java.util.function.Function<String,Object> stock=(java.util.function.Function<String,Object>)providerType.getConstructor(Context.class,Context.class).newInstance(visual,vendor);
        Object sentinel=new Object();int[] calls={0};
        android.view.LayoutInflater hostile=android.view.LayoutInflater.from(app).cloneInContext(app);
        hostile.setFactory2(new android.view.LayoutInflater.Factory2(){
            public View onCreateView(View parent,String name,Context context,android.util.AttributeSet attrs){throw new AssertionError("host AppCompat factory entered original clock XML");}
            public View onCreateView(String name,Context context,android.util.AttributeSet attrs){throw new AssertionError("host AppCompat factory entered original clock XML");}
        });
        Context foreignHost=new android.content.ContextWrapper(app){
            @Override public ClassLoader getClassLoader(){return vendor.getClassLoader();}
            @Override public Object getSystemService(String name){return LAYOUT_INFLATER_SERVICE.equals(name)?hostile:super.getSystemService(name);}
        };
        NativeClockProvider factory=new NativeClockProvider(foreignHost,app.getAssets(),id->{calls[0]++;return sentinel;});
        require(factory.apply("unrelated.vendor.clock")==sentinel&&calls[0]==1,"native provider fallback intercepted");
        NativeFlymeClockPlugin independent=(NativeFlymeClockPlugin)factory.apply(NativeFlymeClockPlugin.ID);
        require(independent.lockFace!=null&&calls[0]==1,"vendor context could not inflate independent original face");
        independent.apply("release",null);
        NativeOriginalClockPlugin hyperos=(NativeOriginalClockPlugin)factory.apply(NativeHyperOsClockPlugin.VERTICAL_ID);
        require(hyperos.apply(1)!=null,"HyperOS original clock did not survive host factory isolation");
        hyperos.apply("release",null);
        NativeClockProvider delegated=new NativeClockProvider(app,app.getAssets(),stock);
        Object nativeClock=delegated.apply("com.oplus.keyguard.clock.digital");
        require(nativeClock!=null&&!(nativeClock instanceof NativeFlymeClockPlugin),"stock digital clock no longer delegated");
        if(nativeClock instanceof java.util.function.BiFunction)((java.util.function.BiFunction<String,Bundle,Bundle>)nativeClock).apply("release",null);
    }
    private void nativeContainer(Context app)throws Exception {
        Context base=app.createPackageContext("com.oplus.keyguard.clock.base",Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
        ClassLoader loader=base.getClassLoader();
        Class<?> containerClass=loader.loadClass("com.oplus.keyguard.ui.ClockPluginContainer");
        Object container=containerClass.getConstructor(Context.class,Context.class).newInstance(app,base);
        NativeFlymeClockPlugin plugin=new NativeFlymeClockPlugin(app);
        Class<?> providerClass=loader.loadClass("v3.c");
        Object provider=providerClass.getConstructor(int.class).newInstance(7);
        uniqueField(providerClass,Object.class).set(provider,(java.util.function.Function<String,Object>)id->NativeFlymeClockPlugin.ID.equals(id)?plugin:null);
        uniqueField(containerClass,providerClass).set(container,provider);
        try {
            require(Boolean.TRUE.equals(containerClass.getMethod("c",String.class).invoke(container,NativeFlymeClockPlugin.ID)),"installed container rejected plugin");
            require(NativeFlymeClockPlugin.ID.equals(containerClass.getMethod("getPluginPkgName").invoke(container)),"container lost independent ID");
            View host=(View)container;
            host.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.AT_MOST));
            host.layout(0,0,1440,host.getMeasuredHeight());
            require(plugin.root.getParent()==container,"original container did not attach root");
            // Commands go through the installed host's forwarding method, not a test proxy.
            Method forward=containerClass.getMethod("k",String.class,Bundle.class);
            Bundle value=new Bundle();value.putLong("time",1790086320000L);
            forward.invoke(container,"setTime",value);
            require(plugin.face.time()==value.getLong("time"),"container did not forward minute tick");
            Bundle data=(Bundle)forward.invoke(container,"getStyleData",null);
            require(data!=null&&data.getString("styleData").contains(NativeFlymeClockPlugin.ID),"container cannot save style");
        } finally {
            plugin.apply("release",null);
            ((android.view.ViewGroup)container).removeAllViews();
        }
    }
    private static java.lang.reflect.Field uniqueField(Class<?> owner,Class<?> type)throws Exception {
        java.lang.reflect.Field found=null;
        for(java.lang.reflect.Field field:owner.getDeclaredFields())if(field.getType()==type&&!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            if(found!=null)throw new IllegalStateException("ambiguous field in "+owner.getName());
            found=field;
        }
        if(found==null)throw new NoSuchFieldException(owner.getName()+" field type "+type.getName());
        found.setAccessible(true);return found;
    }
    private void check(Context app, ClassLoader loader) throws Exception {
        NativeFlymeClockPlugin plugin = new NativeFlymeClockPlugin(app);
        Class<?> comm = loader.loadClass("com.oplus.keyguard.comm.b");
        Object proxy = comm.getMethod("b", Object.class).invoke(comm.getField("INSTANCE").get(null), plugin);
        Class<?> contract = loader.loadClass("com.oplus.keyguard.d");
        Class<?> callbackType = loader.loadClass("com.oplus.keyguard.e");
        Method call = contract.getMethod("a", String.class, Bundle.class);
        Method view = contract.getMethod("d", int.class);
        require(view.invoke(proxy, 1) == plugin.root, "native root view mismatch");
        require(view.invoke(proxy, 28) == null, "clock intercepted the native widget view ID");
        int[] callbacks = {0};
        Bundle[] hostLayout={null};
        Bundle[] widgetGeometry={null};int[] widgetCalls={0};
        java.util.Map<String,Bundle> widgetState=new java.util.HashMap<>();
        Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackType}, (p,m,a) -> {
            if (m.getName().equals("a")) {
                Bundle data=(Bundle)a[1];
                if(data.getInt("commandDestination")==4){
                    require(java.util.Arrays.asList("onStyleWidgetsMorphologyChanged","getWidgetsContainerState","setWidgetsContainerTranslation","onClockColorConfigChanged","updateWidgetClockStyleAndPhaseState","startWidgetAnim","endWidgetAnim","setWidgetsVisibility","recruitWidgetPlugin").contains(a[0]),"unknown widget command: "+a[0]);
                    widgetState.put((String)a[0],data.deepCopy());
                    if("onStyleWidgetsMorphologyChanged".equals(a[0])){widgetGeometry[0]=data.deepCopy();widgetCalls[0]++;}
                    return null;
                }
                require("onClockLayoutCalculated".equals(a[0]), "unexpected callback");
                Bundle layout = (Bundle)a[1];
                hostLayout[0]=layout.deepCopy();
                require(layout.getInt("commandDestination") == 1, "wrong callback destination");
                require(layout.getInt("clockHeight") > 0, "empty clock height");
                callbacks[0]++; return null;
            }
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            return "Clock transport test callback";
        });
        contract.getMethod("e", callbackType).invoke(proxy, callback);
        plugin.root.measure(View.MeasureSpec.makeMeasureSpec(1440, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.AT_MOST));
        plugin.root.layout(0, 0, 1440, plugin.root.getMeasuredHeight());
        require(callbacks[0] > 0, "native host did not receive layout");
        Bundle clock = (Bundle)call.invoke(proxy, "getClockVisibleRect", null);
        Rect rect = clock.getParcelable("visibleRect");
        require(rect != null && !rect.isEmpty() && rect.bottom < plugin.clockContainer.getBottom(), "numeric bounds include date/widgets");
        Bundle style = (Bundle)call.invoke(proxy, "getStyleData", null);
        String saved = style.getString("styleData");
        outerRoundTrip(loader, saved);
        NativeFlymeClockPlugin restored = new NativeFlymeClockPlugin(app);
        restored.apply("setStyleData", style);
        require(saved.equals(restored.apply("getStyleData", null).getString("styleData")), "style lost on restore");
        Bundle bad = new Bundle(); bad.putString("styleData", "{\"version\":99,\"style\":\"unknown\",\"color\":0}");
        restored.apply("setStyleData", bad);
        require(saved.equals(restored.apply("getStyleData", null).getString("styleData")), "bad style changed clock");
        Bundle time = new Bundle(); time.putLong("time", 1790086200000L);
        call.invoke(proxy, "setTime", time);
        require(plugin.face.time() == time.getLong("time"), "host time ignored");
        call.invoke(proxy,"onWidgetsPluginReady",null);
        require(widgetGeometry[0]!=null,"clock did not publish geometry to widget plugin");
        int nativeInset=new NativeClockGeometry(app).widgetInset();
        require(widgetGeometry[0].getInt("widgetsContainerPositionLeft")==nativeInset
                &&widgetGeometry[0].getInt("widgetsContainerPositionRight")==nativeInset,"widget margins encoded as absolute right edge");
        require(widgetState.get("updateWidgetClockStyleAndPhaseState").getInt("clockStyle")==7,"Flyme vertical must retain centered native widget layout");
        require(widgetState.get("onClockColorConfigChanged").getInt("clockPrimaryColor")==0xffffffff,"widget color not initialized");
        for(int occupied:new int[]{1,2,1,0}){
            Bundle rows=new Bundle();rows.putInt("cardOccupiedRows",occupied);
            call.invoke(proxy,"onWidgetDataRowChanged",rows);
            require(widgetGeometry[0].getInt("widgetsContainerRows")==occupied,"reserved an absent widget row");
            Bundle big=hostLayout[0].getBundle("bigClockPositionParams");
            int extent=new NativeClockGeometry(app).widgetExtent(occupied);
            require(big.getInt("bigClockCurHeight")==big.getInt("bigClockContentHeightAtCurFont")+extent,
                    "notification geometry did not follow occupied widget rows");
            require(big.getInt("bigClockWidgetsHeight")==extent,"widget height metadata stale");
        }
        for(int state : new int[]{2,3,5,2}) {
            Bundle scene=new Bundle();scene.putInt("uiState",state);scene.putBoolean("isAnim",false);
            call.invoke(proxy,"onClockStateChanged",scene);
            plugin.root.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.AT_MOST));
            plugin.root.layout(0,0,1440,plugin.root.getMeasuredHeight());
            require(widgetGeometry[0].getInt("widgetsContainerPositionTop")==plugin.clockContainer.getBottom()+new NativeClockGeometry(app).widgetGap(),"widget geometry overlaps clock/date");
            require((state==2?plugin.lockFace:plugin.face).getVisibility()==View.VISIBLE,"wrong scene face");
            require((state==3?"widgetSceneAod":"widgetSceneBigClock").equals(widgetState.get("endWidgetAnim").getString("widgetAnimTargetScene")),"native widget scene stale");
            require(widgetState.get("setWidgetsVisibility").getBoolean("isVisible")== (state==2||state==5),"full-screen AOD hid native widgets");
            Rect visible=((Bundle)call.invoke(proxy,"getClockVisibleRect",null)).getParcelable("visibleRect");
            require(!visible.isEmpty()&&visible.bottom<=plugin.clockContainer.getBottom(),"scene numeric bounds invalid");
        }
        Bundle small=new Bundle();small.putInt("uiState",5);small.putInt("clockSize",0);
        call.invoke(proxy,"onClockStateChanged",small);
        layout(plugin.root);
        int compactHeight=plugin.clockContainer.getHeight();
        Rect compactRect=((Bundle)call.invoke(proxy,"getClockVisibleRect",null)).getParcelable("visibleRect");
        require(view.invoke(proxy,8)==plugin.compactFace,"notification clock did not use compact original template");
        require(!compactRect.isEmpty()&&compactRect.bottom<plugin.clockContainer.getBottom(),"compact date entered numeric bounds");
        require(widgetGeometry[0].getInt("widgetsContainerPositionTop")==plugin.clockContainer.getBottom()+new NativeClockGeometry(app).widgetGap(),"compact widget geometry incorrect");
        Bundle burn=new Bundle();burn.putInt("aodTranslationX",7);burn.putInt("aodTranslationY",11);burn.putInt("duration",0);
        call.invoke(proxy,"setAodUiDeBurnin",burn);call.invoke(proxy,"setAodUiDeBurnin",burn);
        Rect moved=((Bundle)call.invoke(proxy,"getClockVisibleRect",null)).getParcelable("visibleRect");
        Rect expected=new Rect(compactRect);expected.offset(7,11-plugin.root.getPaddingTop());
        require(expected.equals(moved),"burn-in offset missing from tracking rect or accumulated twice");
        require(widgetState.get("setWidgetsContainerTranslation").getFloat("widgetsContainerTranslationX")==7&&widgetState.get("setWidgetsContainerTranslation").getFloat("widgetsContainerTranslationY")==11-plugin.root.getPaddingTop(),"separate widget plugin did not follow burn-in offset");
        Bundle large=new Bundle();large.putInt("uiState",5);large.putInt("clockSize",1);
        call.invoke(proxy,"onClockStateChanged",large);layout(plugin.root);
        require(plugin.clockContainer.getHeight()>compactHeight,"notification removal did not restore full size");
        large.putInt("uiState",2);call.invoke(proxy,"onClockStateChanged",large);layout(plugin.root);
        require(plugin.root.getTranslationX()==0&&plugin.root.getTranslationY()==0,"AOD displacement leaked onto lockscreen");
        call.invoke(proxy,"setAodUiDeBurnin",burn);
        require(plugin.root.getTranslationX()==0&&plugin.root.getTranslationY()==0,"late AOD command moved lockscreen");
        Bundle unlocked=new Bundle();unlocked.putInt("uiState",1);
        call.invoke(proxy,"onClockStateChanged",unlocked);
        require("widgetSceneUnlock".equals(widgetState.get("endWidgetAnim").getString("widgetAnimTargetScene")),"unlock left widgets in AOD");
        call.invoke(proxy,"onClockStateChanged",large);
        int beforeBlock=callbacks[0],widgetBefore=widgetCalls[0];call.invoke(proxy,"blockRender",null);
        plugin.clockContainer.layout(0,0,1440,plugin.clockContainer.getHeight()+8);
        require(callbacks[0]==beforeBlock,"layout dispatched while blocked");
        require(widgetCalls[0]==widgetBefore,"widget geometry dispatched while blocked");
        call.invoke(proxy,"unblockRender",null);
        require(callbacks[0]==beforeBlock+1,"final layout not delivered after unblock");
        require(widgetCalls[0]==widgetBefore+1,"widget geometry was lost after unblock");
        contract.getMethod("b", callbackType).invoke(proxy, callback);
        int previous = callbacks[0];
        plugin.root.layout(0, 0, 1440, plugin.root.getHeight()+1);
        require(previous == callbacks[0], "callback kept after unregister");
        call.invoke(proxy, "release", null);
        require(view.invoke(proxy, 1) == null && plugin.root.getChildCount() == 0, "release leaked views");
        restored.apply("release", null);
    }
    private static void layout(View view){
        view.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.AT_MOST));
        view.layout(0,0,1440,view.getMeasuredHeight());
    }
    private void outerRoundTrip(ClassLoader loader, String inner) throws Exception {
        Class<?> adapter = loader.loadClass("com.oplus.keyguard.config.KeyguardStyleConfigTypeAdapter");
        Class<?> config = loader.loadClass("com.oplus.keyguard.config.KeyguardStyleConfig");
        Class<?> reader = loader.loadClass("com.google.gson.stream.JsonReader");
        Class<?> writer = loader.loadClass("com.google.gson.stream.JsonWriter");
        String widgets = "{\"keepSystemWidget\":true}";
        String base = "{\"keepSystemBaseUi\":true}";
        String json = new JSONObject().put("pkg", NativeFlymeClockPlugin.ID).put("clockStyleConfig", inner)
                .put("widgetStyleConfig", widgets).put("baseUiStyleConfig", base).toString();
        Object codec = adapter.getConstructor().newInstance();
        Object decoded = adapter.getMethod("read", reader).invoke(codec, reader.getConstructor(Reader.class).newInstance(new StringReader(json)));
        StringWriter out = new StringWriter();
        Object sink = writer.getConstructor(Writer.class).newInstance(out);
        adapter.getMethod("write", writer, config).invoke(codec, sink, decoded);
        writer.getMethod("flush").invoke(sink);
        Object again = adapter.getMethod("read", reader).invoke(codec, reader.getConstructor(Reader.class).newInstance(new StringReader(out.toString())));
        require(NativeFlymeClockPlugin.ID.equals(config.getMethod("getPkg").invoke(again)), "host reset independent plugin ID");
        require(inner.equals(config.getMethod("getClockStyleConfig").invoke(again)), "host reset clock data");
        require(widgets.equals(config.getMethod("getWidgetStyleConfig").invoke(again)), "host changed widgets");
        require(base.equals(config.getMethod("getBaseUiStyleConfig").invoke(again)), "host changed base UI");
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
