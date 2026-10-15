package org.aliveclean;

import android.app.Activity;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** Design tokens of the home and settings pages. Accent follows the applied wallpaper. */
final class Look {
    final Context context;
    final boolean dark;
    final int page,surface,raised,text,muted,line,accent,onAccent;

    Look(Context context){
        this.context=context;
        dark=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        // True black in dark mode: the live previews read as real screens.
        page=dark?0xff000000:0xfff2f3f5;
        surface=dark?0xff141519:0xffffffff;
        raised=dark?0xff202127:0xffe8e9ee;
        text=dark?0xffeeedf0:0xff17181c;
        muted=dark?0xff8d8c95:0xff6b6c74;
        line=dark?0xff26272d:0xffe4e5ea;
        accent=accent(context,dark);
        onAccent=Color.luminance(accent)>.5f?0xff101114:0xffffffff;
    }
    private static int accent(Context context,boolean dark){
        int seed=0xff9fb4ff;
        try{
            WallpaperColors colors=WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_LOCK);
            if(colors==null)colors=WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if(colors!=null)seed=colors.getPrimaryColor().toArgb();
        }catch(RuntimeException ignored){}
        float[] hsl=new float[3];toHsl(seed,hsl);
        // Keep the hue, but make it a calm, legible UI accent on either page.
        hsl[1]=Math.max(.38f,Math.min(.72f,hsl[1]<.08f?.45f:hsl[1]));
        hsl[2]=dark?.74f:.44f;
        return hslToColor(hsl);
    }
    private static void toHsl(int color,float[] out){
        float r=Color.red(color)/255f,g=Color.green(color)/255f,b=Color.blue(color)/255f;
        float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min,l=(max+min)/2;
        float h=0,s=d==0?0:d/(1-Math.abs(2*l-1));
        if(d!=0){if(max==r)h=((g-b)/d)%6;else if(max==g)h=(b-r)/d+2;else h=(r-g)/d+4;h*=60;if(h<0)h+=360;}
        out[0]=h;out[1]=s;out[2]=l;
    }
    private static int hslToColor(float[] hsl){
        float h=hsl[0],s=hsl[1],l=hsl[2],c=(1-Math.abs(2*l-1))*s,x=c*(1-Math.abs((h/60f)%2-1)),m=l-c/2;
        float r,g,b;int i=(int)(h/60)%6;
        if(i==0){r=c;g=x;b=0;}else if(i==1){r=x;g=c;b=0;}else if(i==2){r=0;g=c;b=x;}
        else if(i==3){r=0;g=x;b=c;}else if(i==4){r=x;g=0;b=c;}else{r=c;g=0;b=x;}
        return Color.rgb(Math.round((r+m)*255),Math.round((g+m)*255),Math.round((b+m)*255));
    }

    int dp(float v){return Math.round(v*context.getResources().getDisplayMetrics().density);}
    static int alpha(int color,float a){return (color&0xffffff)|(Math.round(a*255)<<24);}

    void window(Activity activity,View root){
        Window w=activity.getWindow();w.setStatusBarColor(page);w.setNavigationBarColor(page);
        root.setBackgroundColor(page);
        root.setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setFitsSystemWindows(true);
    }
    GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    Drawable pressable(int color,float radius){
        return new RippleDrawable(ColorStateList.valueOf(alpha(text,dark?.12f:.08f)),round(color,radius),round(0xffffffff,radius));
    }
    TextView text(CharSequence value,float sp,int color,boolean bold){
        TextView t=new TextView(context);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setIncludeFontPadding(false);t.setLineSpacing(0,1.2f);return t;
    }
    ImageView icon(int res,int tint,int size){
        ImageView i=new ImageView(context);i.setImageResource(res);i.setImageTintList(ColorStateList.valueOf(tint));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return i;
    }
    /** Section heading inside a page; plain sentence case, no eyebrow labels. */
    TextView heading(String value){
        TextView t=text(value,14,muted,true);t.setPadding(dp(28),dp(26),dp(28),dp(10));return t;
    }
    /** A grouped list; rows are separated by inset hairlines. */
    LinearLayout group(){
        LinearLayout g=new LinearLayout(context);g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(round(surface,22));g.setClipToOutline(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.leftMargin=lp.rightMargin=dp(16);g.setLayoutParams(lp);
        g.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        g.setDividerDrawable(new InsetDrawable(new ColorDrawable(line){@Override public int getIntrinsicHeight(){return Math.max(1,dp(.6f));}},dp(72),0,0,0));
        return g;
    }
    /** Icon tile, title, optional summary and trailing view; returns the row. */
    LinearLayout row(int iconRes,String title,CharSequence summary,View trailing){
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setMinimumHeight(dp(68));
        row.setBackground(pressable(surface,0));
        if(iconRes!=0){
            FrameLayout tile=new FrameLayout(context);tile.setBackground(round(alpha(accent,dark?.16f:.14f),12));
            ImageView glyph=icon(iconRes,accent,22);tile.addView(glyph,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(40),dp(40));tp.setMarginEnd(dp(16));row.addView(tile,tp);
        }
        LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);
        TextView t=text(title,16,text,false);labels.addView(t);
        if(summary!=null){TextView s=text(summary,13,muted,false);s.setTag("summary");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(5);labels.addView(s,sp);}
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(trailing!=null){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginStart(dp(12));row.addView(trailing,lp);}
        return row;
    }
    ImageView chevron(){return icon(R.drawable.ic_oa_chevron,alpha(muted,.8f),16);}
    static TextView summary(View row){return (TextView)row.findViewWithTag("summary");}
    Switch toggle(){
        Switch s=new Switch(context);
        int[][] states={{android.R.attr.state_checked},{}};
        s.setThumbTintList(new ColorStateList(states,new int[]{onAccent==0xffffffff?0xffffffff:0xff101114,dark?0xffc9c8cf:0xffffffff}));
        s.setTrackTintList(new ColorStateList(states,new int[]{accent,dark?0xff3a3b42:0xffc9cad1}));
        s.setTrackTintMode(PorterDuff.Mode.SRC);s.setShowText(false);s.setBackground(null);return s;
    }
    /** Top bar: back button or none, a title, optional trailing action. */
    LinearLayout bar(Activity activity,String title,boolean back,float titleSp){
        LinearLayout bar=new LinearLayout(context);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(back?8:28),dp(10),dp(12),dp(6));bar.setMinimumHeight(dp(64));
        if(back){
            ImageView b=icon(R.drawable.ic_oa_back,text,24);b.setScaleType(ImageView.ScaleType.CENTER);b.setContentDescription("返回");
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(text,.12f)),null,null));
            b.setOnClickListener(v->activity.finish());bar.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        TextView t=text(title,titleSp,text,true);if(back)t.setPadding(dp(4),0,0,0);
        bar.addView(t,new LinearLayout.LayoutParams(0,-2,1));return bar;
    }
    ImageView barAction(int res,String description,View.OnClickListener click){
        ImageView a=icon(res,text,22);a.setScaleType(ImageView.ScaleType.CENTER);a.setContentDescription(description);
        a.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(text,.12f)),round(raised,24),null));
        a.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));a.setOnClickListener(click);return a;
    }
}
