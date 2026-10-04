package org.aliveclean;

import android.content.*;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;

/** Original Vivo panel XML/drawables with a portable replacement for its hidden progress view. */
final class VivoUi extends ContextWrapper {
    final VivoEngineRuntime runtime;
    private final Resources.Theme theme;
    private final Resources panelResources;
    private final LayoutInflater inflater;
    VivoUi(Context host,VivoEngineRuntime runtime){
        super(host);this.runtime=runtime;
        android.content.res.Configuration configuration=new android.content.res.Configuration(runtime.getResources().getConfiguration());
        configuration.uiMode=(configuration.uiMode&~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|android.content.res.Configuration.UI_MODE_NIGHT_NO;
        configuration.setLocale(I18n.locale());
        panelResources=new Resources(runtime.getAssets(),runtime.getResources().getDisplayMetrics(),configuration);
        theme=getResources().newTheme();theme.applyStyle(android.R.style.Theme_Material_Light_NoActionBar,false);
        inflater=LayoutInflater.from(host).cloneInContext(this);
        inflater.setFactory2(new LayoutInflater.Factory2(){
            public View onCreateView(String name,Context context,AttributeSet attrs){return onCreateView(null,name,context,attrs);}
            public View onCreateView(View parent,String name,Context context,AttributeSet attrs){
                if(name.equals("com.originui.widget.components.progress.VProgressBar"))return new ProgressBar(context,attrs);
                return null;
            }
        });
    }
    @Override public ClassLoader getClassLoader(){return runtime.getClassLoader();}
    @Override public Resources getResources(){return panelResources;}
    @Override public android.content.res.AssetManager getAssets(){return runtime.getAssets();}
    @Override public Resources.Theme getTheme(){return theme;}
    @Override public Object getSystemService(String name){return LAYOUT_INFLATER_SERVICE.equals(name)?inflater:super.getSystemService(name);}
    int id(String type,String name){int id=getResources().getIdentifier(name,type,"com.vivo.livewallpaper.box");if(id==0)throw new IllegalArgumentException("Missing Vivo resource: "+name);return id;}
    View inflate(String name,ViewGroup parent){return inflater.inflate(id("layout",name),parent,false);}
    static void fitSystemBars(View root){
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });root.requestApplyInsets();
    }
    void tintSlider(SeekBar seek){seek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xff0f77ff));seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff0f77ff));}
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    TextView text(String title,int sp){TextView t=new TextView(this);t.setText(title);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);return t;}
    TextView panelText(String title,int sp){TextView t=text(title,sp);t.setTextColor(0xff111111);return t;}
    android.graphics.drawable.Drawable official(String name){return getBaseContext().getDrawable(getBaseContext().getResources().getIdentifier("vivo_"+name,"drawable",getBaseContext().getPackageName()));}
    GradientDrawable capsule(){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff171717,0xff292929});d.setCornerRadius(dp(50));d.setStroke(dp(1),0xff424242);return d;}
}
