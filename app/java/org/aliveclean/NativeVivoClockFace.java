package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Original Vivo layout geometry/font axes adapted to the native ColorOS clock host. */
final class NativeVivoClockFace extends FrameLayout {
    private final NativeVivoClockStyles.Style style;
    private final OfficialVivoClockUi original;
    private final View content;
    private final ArrayList<TextView> labels=new ArrayList<>(),numbers=new ArrayList<>();
    private final IdentityHashMap<View,HashMap<String,Integer>> dimensions=new IdentityHashMap<>();
    private final boolean aod,compact;
    private float fit=1,offsetX,offsetY;
    private int nativeHeight;
    private final RectF body=new RectF();
    NativeVivoClockFace(Context context,OfficialVivoClockUi original,NativeVivoClockStyles.Style style,boolean aod,boolean compact)throws Exception {
        super(context);this.original=original;this.style=style;this.aod=aod;this.compact=compact;
        setClipChildren(false);setClipToPadding(false);
        content=original.inflate(style.group,new LayoutInflater.Factory2(){
            public View onCreateView(View parent,String name,Context c,AttributeSet attrs){return onCreateView(name,c,attrs);}
            public View onCreateView(String name,Context c,AttributeSet attrs){
                try{
                    View view=original.create(name,attrs);
                    if(view!=null){
                        HashMap<String,Integer> refs=new HashMap<>();
                        for(String key:new String[]{"textSize","layout_width","layout_height","layout_marginTop","layout_marginBottom","layout_marginStart","layout_marginEnd"}){
                            int id=attrs.getAttributeResourceValue("http://schemas.android.com/apk/res/android",key,0);
                            if(id!=0)refs.put(key,id);
                        }
                        dimensions.put(view,refs);
                    }
                    return view;
                }catch(Exception failure){throw new IllegalStateException("Original Vivo clock control "+name,failure);}
            }
        });
        addView(content,new LayoutParams(-1,-1));
        collect(content);
        Typeface font=new Typeface.Builder(context.getAssets(),"native-clock/vivo/"+style.font)
                .setFontVariationSettings("'wght' "+style.weight+ytde()).build();
        for(TextView text:labels){
            String name=name(text);
            boolean digit=name.equals("hour")||name.equals("minute")||name.equals("colon")||name.startsWith("hour_")||name.startsWith("minute_");
            if(digit){numbers.add(text);text.setTypeface(font);text.setMaxLines(1);text.setHorizontallyScrolling(false);}
            text.setTextColor(Color.WHITE);
            if(name.equals("info"))text.setVisibility(GONE);
            else if(!digit)text.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        }
        // These sizes are assigned by the original TimeComponent at runtime,
        // not by the default XML. Preserve that second layout step.
        if(style.group==8||style.group==9||style.group==11){
            for(TextView text:numbers){
                ViewGroup.LayoutParams p=text.getLayoutParams();
                p.height=dimension("minute_hour_height",p.height);
                if(p instanceof MarginLayoutParams){
                    MarginLayoutParams m=(MarginLayoutParams)p;
                    m.topMargin=dimension(name(text)+"_margin_top",m.topMargin);
                }
                text.setLayoutParams(p);
            }
        }
        if(style.group==11){
            View date=content.findViewById(original.getResources().getIdentifier("ll_date","id",OfficialVivoClockUi.PACKAGE));
            MarginLayoutParams p=(MarginLayoutParams)date.getLayoutParams();
            p.topMargin=dimension("date_margin_top",p.topMargin);date.setLayoutParams(p);
        }
        if(style.group==5){
            // S5TimeComponent.updateTopMargin switches off the XML's centered
            // interactive constraint when attached to the keyguard scene.
            for(String name:new String[]{"hour","ll_info_week"}){
                View view=content.findViewById(original.getResources().getIdentifier(name,"id",OfficialVivoClockUi.PACKAGE));
                ViewGroup.LayoutParams p=view.getLayoutParams();p.getClass().getField("bottomToBottom").setInt(p,-1);p.getClass().getField("topToTop").setInt(p,0);
                String key=name.equals("hour")?"vivo_keyguard_s5_hour_margin_top":"vivo_keyguard_s5_ll_week_info_margin_top";
                ((MarginLayoutParams)p).topMargin=original.getResources().getDimensionPixelSize(original.getResources().getIdentifier(key,"dimen",OfficialVivoClockUi.PACKAGE));view.setLayoutParams(p);
            }
        }
        if(numbers.isEmpty())throw new IllegalStateException("Empty Vivo clock "+style.key);
        update(System.currentTimeMillis(),TimeZone.getDefault(),true);
    }
    private String ytde(){
        if(style.group!=7&&style.group!=8&&style.group!=9&&style.group!=12)return "";
        String key=(style.group==7||style.group==12)?"vivo_keyguard_s"+style.group+"_time_"+style.grid+"_ytde":"vivo_keyguard_s"+style.group+"_"+style.grid+"_time_text_ytde";
        int id=original.getResources().getIdentifier(key,"dimen",OfficialVivoClockUi.PACKAGE);
        if(id==0)throw new IllegalStateException("Missing Vivo font axis "+key);
        return ", 'ytde' "+original.getResources().getDimension(id);
    }
    private int dimension(String suffix,int fallback){
        int id=original.getResources().getIdentifier("vivo_keyguard_s"+style.group+"_"+style.grid+"_"+suffix,"dimen",OfficialVivoClockUi.PACKAGE);
        return id==0?fallback:original.getResources().getDimensionPixelSize(id);
    }
    private String name(View view){return view.getId()==NO_ID?"":original.getResources().getResourceEntryName(view.getId());}
    private void collect(View view){
        HashMap<String,Integer> refs=dimensions.get(view);
        if(refs!=null){
            for(Map.Entry<String,Integer> entry:refs.entrySet()){
                String resource=original.getResources().getResourceEntryName(entry.getValue());
                String chosen=resource.replaceAll("_[234]x[1-6]_","_"+style.grid+"_");
                if(chosen.equals(resource)&&!resource.matches(".*_[234]x[1-6]_.*")){
                    chosen=resource.replace("vivo_keyguard_s"+style.group+"_","vivo_keyguard_s"+style.group+"_"+style.grid+"_");
                }
                int id=original.getResources().getIdentifier(chosen,"dimen",OfficialVivoClockUi.PACKAGE);
                if(id==0)id=entry.getValue();int value=original.getResources().getDimensionPixelSize(id);
                ViewGroup.LayoutParams p=view.getLayoutParams();
                switch(entry.getKey()){
                    case "textSize":if(view instanceof TextView)((TextView)view).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,value);break;
                    case "layout_width":if(p!=null)p.width=value;break;
                    case "layout_height":if(p!=null)p.height=value;break;
                    default:if(p instanceof MarginLayoutParams){MarginLayoutParams m=(MarginLayoutParams)p;
                        switch(entry.getKey()){
                            case "layout_marginTop":m.topMargin=value;break;
                            case "layout_marginBottom":m.bottomMargin=value;break;
                            case "layout_marginStart":m.setMarginStart(value);break;
                            case "layout_marginEnd":m.setMarginEnd(value);break;
                        }
                    }
                }
            }
        }
        if(view instanceof TextView)labels.add((TextView)view);
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;g.setClipChildren(false);g.setClipToPadding(false);for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i));}
    }
    void update(long time,TimeZone zone,boolean format24){
        Calendar cal=Calendar.getInstance(zone);cal.setTimeInMillis(time);
        int hour=cal.get(format24?Calendar.HOUR_OF_DAY:Calendar.HOUR);if(!format24&&hour==0)hour=12;
        String hh=String.format(Locale.ROOT,"%02d",hour),mm=String.format(Locale.ROOT,"%02d",cal.get(Calendar.MINUTE));
        Locale locale=Locale.getDefault();java.text.SimpleDateFormat dateFormat="zh".equals(locale.getLanguage())?new java.text.SimpleDateFormat("M月d日 E",Locale.CHINA):new java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale,"MMMdEEE"),locale);dateFormat.setTimeZone(zone);
        for(TextView text:labels){
            String name=name(text),value=null;
            switch(name){
                case "hour":value=style.group==1||style.group==2||style.group==4||style.group==5||style.group==6||style.group==7||style.group==12?hh+":"+mm:hh;break;
                case "minute":value=mm;break;
                case "hour_first":value=hh.substring(0,1);break;
                case "hour_second":value=hh.substring(1);break;
                case "minute_first":value=mm.substring(0,1);break;
                case "minute_second":value=mm.substring(1);break;
                case "colon":value=":";text.setVisibility(style.group==3||style.group==8||style.group>=10?GONE:VISIBLE);break;
                case "date":value=style.group==5||style.group==6?(cal.get(Calendar.MONTH)+1)+"/"+cal.get(Calendar.DAY_OF_MONTH):dateFormat.format(cal.getTime());break;
                case "week":value=new java.text.SimpleDateFormat("EEEE",Locale.CHINA).format(cal.getTime());break;
            }
            if(value!=null)text.setText(value);
        }
        requestLayout();invalidate();
    }
    void color(int color){for(TextView text:labels)text.setTextColor(color);}
    java.util.List<TextView> materialViews(){return java.util.Collections.unmodifiableList(labels);}
    boolean glassView(View view){return numbers.contains(view);}
    String geometryReport(){StringBuilder text=new StringBuilder("body="+body+" fit="+fit);for(TextView v:labels)text.append('\n').append(name(v)).append(" visible=").append(v.getVisibility()).append(" text=").append(v.getText()).append(" size=").append(v.getTextSize()).append(" measured=").append(v.getWidth()).append('x').append(v.getHeight()).append(" fallback=").append(v.isFallbackLineSpacing()).append(" xy=").append(v.getX()).append(',').append(v.getY()).append(" bounds=").append(bounds(v));return text.toString();}
    private RectF bounds(View view){
        RectF r=new RectF(0,0,view.getWidth(),view.getHeight());
        if(view instanceof TextView){TextView t=(TextView)view;
            if(t.getText().length()==0)return new RectF();
            Rect ink=new Rect();t.getPaint().getTextBounds(t.getText().toString(),0,t.getText().length(),ink);
            r.set(ink);r.offset(t.getCompoundPaddingLeft(),t.getBaseline());
            if(t.getLayout()!=null)r.offset(t.getLayout().getLineLeft(0),0);
        }
        View node=view;while(node!=content&&node.getParent() instanceof View){r.offset(node.getLeft()+node.getTranslationX(),node.getTop()+node.getTranslationY());node=(View)node.getParent();}
        return r;
    }
    @Override protected void onMeasure(int ws,int hs){
        int width=MeasureSpec.getSize(ws);nativeHeight=getResources().getDisplayMetrics().heightPixels;
        // ColorOS's editor theme can disable fallback line spacing. Vivo's
        // variable ytde axis extends far below the base font's descent; with
        // that theme default TextView measures 479px for a ~1760px glyph and
        // clips it before any material shader sees the lower half. Request the
        // shaped font's line metrics explicitly, independently of host theme.
        for(TextView label:labels)if(!label.isFallbackLineSpacing())label.setFallbackLineSpacing(true);
        content.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(nativeHeight,MeasureSpec.EXACTLY));
        content.layout(0,0,width,nativeHeight);body.setEmpty();
        for(TextView text:labels)if(text.getVisibility()==VISIBLE)body.union(bounds(text));
        float margin=getResources().getDisplayMetrics().density*2;
        fit=Math.min(1f,(width-margin*2)/Math.max(1,body.width()));
        if(aod||compact){
            int id=original.getResources().getIdentifier("vivo_keyguard_s"+style.group+"_integration_aod_time_text_size","dimen",OfficialVivoClockUi.PACKAGE);
            float size=id==0?48*getResources().getDisplayMetrics().density:original.getResources().getDimension(id);
            float base=numbers.get(0).getTextSize();fit=Math.min(fit,size/base);
        }
        offsetX=(aod||compact)?(width-body.width()*fit)/2-body.left*fit:Math.max(margin-body.left*fit,Math.min(0,width-margin-body.right*fit));
        offsetY=-body.top*fit+margin;
        setMeasuredDimension(width,Math.max(1,(int)Math.ceil(body.height()*fit+margin*2)));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        content.layout(0,0,r-l,nativeHeight);content.setPivotX(0);content.setPivotY(0);content.setScaleX(fit);content.setScaleY(fit);content.setTranslationX(offsetX);content.setTranslationY(offsetY);
    }
    void numberBounds(RectF out){out.setEmpty();for(TextView number:numbers)if(number.getVisibility()==VISIBLE)out.union(bounds(number));out.set(out.left*fit+offsetX,out.top*fit+offsetY,out.right*fit+offsetX,out.bottom*fit+offsetY);}
}
