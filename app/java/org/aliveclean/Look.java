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

/**
 * Glass over the applied wallpaper: every page sits on a blurred snapshot of
 * it, panels are frosted and edge-lit. The accent follows the wallpaper.
 */
final class Look {
    /** Last wallpaper frame seen by the home page; shared by secondary pages. */
    static volatile Bitmap backdrop;

    final Context context;
    final boolean dark;
    final int text,muted,faint,glass,glassStrong,edge,accent,onAccent,scrim;
    final int page,surface,raised,divider;

    Look(Context context){
        this.context=context;
        dark=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        text=dark?0xf2ffffff:0xff121318;
        muted=dark?0xa6ffffff:0xa0121318;
        faint=dark?0x66ffffff:0x66121318;
        glass=dark?0x1fffffff:0x8cffffff;
        glassStrong=dark?0x33ffffff:0xccffffff;
        edge=dark?0x2effffff:0xb3ffffff;
        scrim=dark?0x8c06070a:0x66f4f5f8;
        page=dark?0xff000000:0xfff3f4f7;surface=dark?0xff17181c:0xffffffff;
        raised=dark?0xff2c2d33:0xffe7e8ed;divider=dark?0xff26272c:0xffe6e7eb;
        accent=accent(context,dark);
        onAccent=Color.luminance(accent)>.5f?0xff0e0f12:0xffffffff;
    }
    private static int accent(Context context,boolean dark){
        int seed=0xff9fb4ff;
        try{
            WallpaperManager manager=WallpaperManager.getInstance(context);
            WallpaperColors colors=manager.getWallpaperColors(WallpaperManager.FLAG_LOCK);
            if(colors==null)colors=manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if(colors!=null)seed=colors.getPrimaryColor().toArgb();
        }catch(RuntimeException ignored){}
        float[] hsl=new float[3];toHsl(seed,hsl);
        hsl[1]=hsl[1]<.08f?.45f:Math.max(.42f,Math.min(.78f,hsl[1]));
        hsl[2]=dark?.76f:.46f;
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

    // ---- Page --------------------------------------------------------------

    /** Edge-to-edge page on the blurred wallpaper; returns the content column. */
    FrameLayout page(Activity activity){return page(activity,true);}
    /** wallpaper: blurred wallpaper behind the page (home only); otherwise solid. */
    FrameLayout page(Activity activity,boolean wallpaper){
        Window w=activity.getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);w.setNavigationBarColor(Color.TRANSPARENT);
        if(android.os.Build.VERSION.SDK_INT>=29){w.setNavigationBarContrastEnforced(false);w.setStatusBarContrastEnforced(false);}
        w.setBackgroundDrawable(new ColorDrawable(dark?0xff000000:0xfff4f5f8));
        FrameLayout root=new FrameLayout(activity);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            |(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        if(!wallpaper){root.setBackgroundColor(page);return root;}
        ImageView back=new ImageView(activity);back.setScaleType(ImageView.ScaleType.CENTER_CROP);back.setTag("backdrop");
        root.addView(back,new FrameLayout.LayoutParams(-1,-1));
        View tint=new View(activity);
        tint.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{alpha(scrim,Color.alpha(scrim)/255f*.75f),scrim,alpha(scrim,Math.min(1,Color.alpha(scrim)/255f*1.25f))}));
        root.addView(tint,new FrameLayout.LayoutParams(-1,-1));
        backdrop(root,backdrop);
        return root;
    }
    void backdrop(FrameLayout root,Bitmap frame){
        ImageView back=root.findViewWithTag("backdrop");if(back==null)return;
        if(frame==null){
            back.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{alpha(accent,.55f),dark?0xff0b0c10:0xffe9ebf1,alpha(accent,.3f)}));
            back.setRenderEffect(null);return;
        }
        back.setImageBitmap(frame);
        if(android.os.Build.VERSION.SDK_INT>=31)back.setRenderEffect(RenderEffect.createBlurEffect(dp(56),dp(56),Shader.TileMode.CLAMP));
    }
    /** Vertical scroller below the status bar, padded for the navigation bar. */
    LinearLayout column(FrameLayout root){
        ScrollView scroll=new ScrollView(context);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setClipToPadding(false);
        LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();
            v.setPadding(0,top,0,bottom+dp(28));return insets.consumeSystemWindowInsets();
        });
        return content;
    }

    // ---- Surfaces -----------------------------------------------------------

    /** Frosted panel: translucent fill, a soft top sheen and a lit hairline edge. */
    Drawable glass(float radius,boolean strong){
        float r=dp(radius);
        GradientDrawable fill=new GradientDrawable();fill.setCornerRadius(r);fill.setColor(strong?glassStrong:glass);
        GradientDrawable sheen=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{dark?0x14ffffff:0x40ffffff,0x00ffffff});
        sheen.setCornerRadius(r);
        GradientDrawable rim=new GradientDrawable();rim.setCornerRadius(r);rim.setColor(Color.TRANSPARENT);
        rim.setStroke(Math.max(1,dp(.8f)),edge);
        return new LayerDrawable(new Drawable[]{fill,sheen,rim});
    }
    Drawable pressable(Drawable base,float radius){
        GradientDrawable mask=new GradientDrawable();mask.setCornerRadius(dp(radius));mask.setColor(0xffffffff);
        return new RippleDrawable(ColorStateList.valueOf(dark?0x24ffffff:0x1a000000),base,mask);
    }
    GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    void lift(View view,float radius,float elevation){
        view.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(radius));}});
        view.setClipToOutline(true);view.setElevation(dp(elevation));
        if(android.os.Build.VERSION.SDK_INT>=28){view.setOutlineAmbientShadowColor(0xff000000);view.setOutlineSpotShadowColor(0xff000000);}
    }

    // ---- Type ---------------------------------------------------------------

    TextView text(CharSequence value,float sp,int color,int weight){
        TextView t=new TextView(context);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),weight,false));
        t.setIncludeFontPadding(false);t.setLineSpacing(0,1.25f);return t;
    }
    TextView text(CharSequence value,float sp,int color,boolean bold){return text(value,sp,color,bold?600:400);}
    /** Small section label over a group, aligned with the row text. */
    TextView heading(String value){
        TextView t=text(value,13,muted,500);t.setPadding(dp(32),dp(28),dp(32),dp(8));return t;
    }
    /** Explanatory note under a group. */
    TextView footer(CharSequence value){
        TextView t=text(value,13,muted,400);t.setLineSpacing(0,1.35f);t.setPadding(dp(32),dp(8),dp(32),0);return t;
    }
    ImageView icon(int res,int tint,int size){
        ImageView i=new ImageView(context);i.setImageResource(res);i.setImageTintList(ColorStateList.valueOf(tint));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return i;
    }

    // ---- Chrome -------------------------------------------------------------

    /** Round glass button for the top bar. */
    ImageView orb(int res,String description,View.OnClickListener click){
        ImageView a=new ImageView(context);a.setImageResource(res);a.setImageTintList(ColorStateList.valueOf(text));
        a.setScaleType(ImageView.ScaleType.CENTER);a.setContentDescription(description);
        a.setBackground(pressable(glass(22,false),22));a.setOnClickListener(click);
        a.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));return a;
    }
    /** Home: large title with trailing actions. Secondary pages: back and a compact title. */
    LinearLayout bar(Activity activity,String title,boolean back){
        LinearLayout box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(row);
        if(back){
            row.setPadding(dp(6),dp(4),dp(16),dp(4));row.setMinimumHeight(dp(60));
            ImageView b=icon(R.drawable.ic_oa_back,text,24);b.setScaleType(ImageView.ScaleType.CENTER);b.setContentDescription("返回");
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x24ffffff:0x1a000000),null,null));
            b.setOnClickListener(v->activity.finish());row.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView t=text(title,20,text,600);t.setTag("title");t.setPadding(dp(2),0,0,0);
            row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        }else{
            row.setPadding(dp(24),dp(14),dp(16),dp(6));
            TextView t=text(title,32,text,700);t.setTag("title");row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        }
        return box;
    }
    static LinearLayout barRow(LinearLayout bar){return (LinearLayout)bar.getChildAt(0);}

    // ---- Lists --------------------------------------------------------------

    LinearLayout group(){
        LinearLayout g=new LinearLayout(context);g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(round(surface,16));g.setClipToOutline(true);
        g.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(16));}});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.leftMargin=lp.rightMargin=dp(16);g.setLayoutParams(lp);
        g.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        g.setDividerDrawable(new InsetDrawable(new ColorDrawable(divider){@Override public int getIntrinsicHeight(){return Math.max(1,dp(.6f));}},dp(16),0,0,0));
        return g;
    }
    /** Title, optional one-line summary, trailing control. iconRes is drawn plain, never in a tile. */
    LinearLayout row(int iconRes,String title,CharSequence summary,View trailing){
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16),dp(13),dp(16),dp(13));row.setMinimumHeight(dp(56));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x1fffffff:0x14000000),null,new ColorDrawable(0xffffffff)));
        if(iconRes!=0){ImageView i=icon(iconRes,muted,22);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(22),dp(22));ip.setMarginEnd(dp(14));row.addView(i,ip);}
        LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title,16,text,400));
        if(summary!=null){TextView s=text(summary,13,muted,400);s.setTag("summary");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(4);labels.addView(s,sp);}
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(trailing!=null){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginStart(dp(12));row.addView(trailing,lp);}
        return row;
    }
    /** Equal-width segments in one track; the selected one is raised. */
    LinearLayout segmented(String[] labels,int selected,java.util.function.IntConsumer choose){
        LinearLayout track=new LinearLayout(context);track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(round(dark?0xff0e0f12:0xffeeeff3,12));track.setPadding(dp(3),dp(3),dp(3),dp(3));
        for(int i=0;i<labels.length;i++){
            final int index=i;boolean on=i==selected;
            TextView s=text(labels[i],14,on?text:muted,on?600:400);s.setGravity(Gravity.CENTER);s.setSelected(on);
            if(on){s.setBackground(round(raised,9));s.setElevation(dp(1));}
            s.setContentDescription(labels[i]+(on?"，已选择":""));
            s.setOnClickListener(v->choose.accept(index));
            track.addView(s,new LinearLayout.LayoutParams(0,dp(34),1));
        }
        return track;
    }
    ImageView chevron(){return icon(R.drawable.ic_oa_chevron,faint,16);}
    static TextView summary(View row){return (TextView)row.findViewWithTag("summary");}
    Switch toggle(){
        Switch s=new Switch(context);
        int[][] states={{android.R.attr.state_checked},{}};
        s.setThumbTintList(new ColorStateList(states,new int[]{0xffffffff,dark?0xffd9d9de:0xffffffff}));
        s.setTrackTintList(new ColorStateList(states,new int[]{accent,dark?0x47ffffff:0x33000000}));
        s.setTrackTintMode(PorterDuff.Mode.SRC);s.setShowText(false);s.setBackground(null);return s;
    }
    SeekBar slider(){
        SeekBar s=new SeekBar(context);
        s.setProgressTintList(ColorStateList.valueOf(accent));s.setThumbTintList(ColorStateList.valueOf(0xffffffff));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(dark?0x40ffffff:0x26000000));return s;
    }

    // ---- Covers -------------------------------------------------------------

    /** Image card with a darkened foot carrying its title. */
    FrameLayout cover(Bitmap image,String title,String subtitle,float radius){
        FrameLayout card=new FrameLayout(context);
        card.setBackground(round(dark?0xff1b1c22:0xffdfe1e7,radius));
        ImageView picture=new ImageView(context);picture.setScaleType(ImageView.ScaleType.CENTER_CROP);picture.setTag("image");
        if(image!=null)picture.setImageBitmap(image);
        card.addView(picture,new FrameLayout.LayoutParams(-1,-1));
        View foot=new View(context);foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0x33000000,0xb3000000}));
        card.addView(foot,new FrameLayout.LayoutParams(-1,-1));
        if(title!=null){
            LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(16),0,dp(16),dp(14));
            labels.addView(text(title,17,0xffffffff,600));
            if(subtitle!=null){TextView s=text(subtitle,12.5f,0xc8ffffff,400);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(4);labels.addView(s,sp);}
            card.addView(labels,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        }
        View rim=new View(context);
        GradientDrawable stroke=new GradientDrawable();stroke.setCornerRadius(dp(radius));stroke.setStroke(Math.max(1,dp(.8f)),0x26ffffff);
        rim.setBackground(stroke);card.addView(rim,new FrameLayout.LayoutParams(-1,-1));
        card.setForeground(pressable(null,radius));
        lift(card,radius,10);
        return card;
    }
    static void coverImage(FrameLayout card,Bitmap image){((ImageView)card.findViewWithTag("image")).setImageBitmap(image);}
}
