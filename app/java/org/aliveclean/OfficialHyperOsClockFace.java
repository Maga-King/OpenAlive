package org.aliveclean;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

/** Original HyperOS ClockBean -> ClockStyleInfo -> layout, including foreground layers. */
final class OfficialHyperOsClockFace extends FrameLayout implements AutoCloseable {
    private final String template;
    private final int style;
    private final int fontStyle,clockWeight;
    private final Class<?> beanType,infoType;
    private final List<View> faces=new ArrayList<>();
    private int color=Color.WHITE,displayType=-1;
    private String zone=java.util.TimeZone.getDefault().getID();
    private boolean format24;

    OfficialHyperOsClockFace(OfficialHyperOsUi source,String template,int style)throws Exception{
        this(source,template,style,21,420);
    }
    OfficialHyperOsClockFace(OfficialHyperOsUi source,String template,int style,int fontStyle,int clockWeight)throws Exception{
        super(source);this.template=template;this.style=style;
        this.fontStyle=fontStyle;this.clockWeight=clockWeight;
        if(!java.util.Arrays.asList("all_in_one","classic_plus","classic","rhombus","eastern_a","eastern_b","eastern_c","magazine_a","magazine_b","magazine_c","doodle").contains(template))
            throw new IllegalArgumentException("Unsupported original HyperOS template");
        beanType=source.getClassLoader().loadClass("com.miui.clock.module.ClockBean");
        infoType=source.getClassLoader().loadClass("com.miui.clock.module.ClockStyleInfo");
        format24=android.text.format.DateFormat.is24HourFormat(source);
        scene(false,false,false);
    }
    void scene(boolean aod,boolean compact,boolean fullAod)throws Exception{
        // Original flags: 1 AOD, 2 notification, 8 preview, 32 full AOD.
        // Preview leaves data/settings subscription with the ColorOS host.
        int type=8|(aod?(fullAod?32:1):0)|(compact?2:0);
        if(type==displayType)return;
        List<View> next=new ArrayList<>();
        next.add(inflate(compact?compactTemplate():template,type));
        // Low-power AOD uses a complete SingleClock/AodClock. Only the original
        // lock/full-AOD layouts split hours and minutes into depth layers.
        if((!aod||fullAod)&&!compact&&(template.equals("all_in_one")||template.equals("eastern_a")||template.equals("eastern_c")))
            next.add(inflate(template+"_minute",type));
        // Magazine C style 5 already renders its date/week. The original
        // editor hides the separate date layer for this style.
        if((!aod||fullAod)&&!compact&&template.equals("magazine_c")&&style!=5)next.add(inflate("magazine_c_date_only",type));
        removeAllViews();faces.clear();faces.addAll(next);displayType=type;
        for(View face:faces)addView(face);
        refresh(zone,format24);
    }
    private String compactTemplate(){
        // The original factory only switches some template IDs on flag 2.
        // Others require their explicit notification counterpart; otherwise
        // a split hour-only view is mistaken for a complete compact clock.
        switch(template){
            case "all_in_one":return "all_in_one_single";
            case "classic_plus":return "classic_plus_small";
            case "classic":return "classic_small";
            case "rhombus":return "rhombus_notification";
            case "eastern_a":return "eastern_a_notification";
            case "eastern_b":return "eastern_b_vertical_notification";
            case "eastern_c":return "eastern_c_notification";
            case "magazine_a":return "magazine_a_notification";
            case "magazine_b":return "magazine_b_notification";
            case "magazine_c":return "magazine_c_notification";
            case "doodle":return "doodle_single";
            default:throw new IllegalStateException("Missing original compact template");
        }
    }
    private View inflate(String name,int type)throws Exception{
        Object bean=beanType.getConstructor(String.class,int.class).newInstance(name,style);
        beanType.getMethod("setPrimaryColor",int.class).invoke(bean,color);
        beanType.getMethod("setSecondaryColor",int.class).invoke(bean,color);
        beanType.getMethod("setInfoAreaColor",int.class).invoke(bean,color);
        beanType.getMethod("setClassicLine1",int.class).invoke(bean,template.startsWith("classic")?101:103);
        if(template.equals("classic_plus"))beanType.getMethod("setClassicLine2",int.class).invoke(bean,72);
        if(template.equals("classic"))beanType.getMethod("setClassicLine2",int.class).invoke(bean,300);
        beanType.getMethod("setFontStyle",int.class).invoke(bean,fontStyle);
        beanType.getMethod("setClockWeight",int.class).invoke(bean,clockWeight);
        if(template.equals("all_in_one")){
            Object preset=getContext().getClassLoader().loadClass("com.miui.clock.allInOne.AllInOneUtil")
                    .getMethod("getPresetConfig",int.class).invoke(null,style);
            beanType.getMethod("setDoubleRow",boolean.class).invoke(bean,preset.getClass().getMethod("isDoubleRow").invoke(preset));
            beanType.getMethod("setColonShow",boolean.class).invoke(bean,preset.getClass().getMethod("isColonShow").invoke(preset));
        }
        beanType.getMethod("setUnablePresetData",boolean.class).invoke(bean,true);
        Object info=infoType.getMethod("convertInfoFromClockBean",Context.class,beanType,int.class).invoke(null,getContext(),bean,type);
        // Legacy compact Info constructors accept only ClockBean and discard
        // displayType. Both the base and classic subclass store this field.
        for(Class<?> owner=info.getClass();owner!=Object.class;owner=owner.getSuperclass()){
            try{java.lang.reflect.Field display=owner.getDeclaredField("displayType");display.setAccessible(true);display.setInt(info,type);}
            catch(NoSuchFieldException absent){}
        }
        if((Integer)infoType.getMethod("getDisplayType").invoke(info)!=type)throw new IllegalStateException("Original scene type not retained");
        if((Boolean)infoType.getMethod("shouldObserveSettings").invoke(info))throw new IllegalStateException("Original clock attempted own settings subscription");
        int layout=(Integer)infoType.getMethod("getLayoutId").invoke(info);
        LayoutInflater inflater=LayoutInflater.from(getContext()).cloneInContext(getContext());
        {
            // Resolve this APK's named system font from the original bundled
            // file; ColorOS does not register HyperOS font families.
            // This official APK's compact XML omits the signature child while
            // ClassicClockBaseView unconditionally dereferences it on inflation.
            // Supply only that invisible dependency; retain its original XML,
            // constraints, time renderer and date renderer.
            inflater.setFactory2(new LayoutInflater.Factory2(){
                public View onCreateView(View parent,String name,Context context,android.util.AttributeSet attrs){
                    return onCreateView(name,context,attrs);
                }
                public View onCreateView(String name,Context context,android.util.AttributeSet attrs){
                    android.graphics.Typeface originalFont=((OfficialHyperOsUi)getContext()).originalSystemFamily(
                            attrs.getAttributeValue("http://schemas.android.com/apk/res/android","fontFamily"));
                    if(originalFont!=null){
                        try{
                            String type=name.indexOf('.')<0?"android.widget."+name:name;
                            android.widget.TextView text=(android.widget.TextView)context.getClassLoader().loadClass(type)
                                    .getConstructor(Context.class,android.util.AttributeSet.class).newInstance(context,attrs);
                            text.setTypeface(originalFont);return text;
                        }catch(ReflectiveOperationException failure){throw new android.view.InflateException("Original system font binding",failure);}
                    }
                    if(!name.equals("com.miui.clock.classic.ClassicClockSmallView")
                            &&!name.equals("com.miui.clock.classic.ClassicPlusClockSmallView"))return null;
                    try {
                        android.view.ViewGroup root=(android.view.ViewGroup)context.getClassLoader().loadClass(name)
                                .getConstructor(Context.class,android.util.AttributeSet.class).newInstance(context,attrs);
                        android.widget.TextView signature=new android.widget.TextView(context);
                        int id=getResources().getIdentifier("signature_text","id","com.miui.aod");
                        if(id==0)throw new IllegalStateException("Missing original signature resource");
                        signature.setId(id);signature.setVisibility(View.GONE);
                        root.addView(signature,new android.view.ViewGroup.LayoutParams(0,0));
                        return root;
                    } catch(ReflectiveOperationException e){throw new android.view.InflateException("Original compact clock",e);}
                }
            });
        }
        View face=inflater.inflate(layout,this,false);
        if(face.getClass().getName().equals("com.miui.clock.magazine.MiuiMagazineCClock")
                ||face.getClass().getName().equals("com.miui.clock.magazine.MiuiMagazineCSingleClock")){
            // The original binder reuses this field for weekday/style changes.
            // Typeface.create(family) resolves only on HyperOS's system image.
            face.getClass().getField("mTypefaceNeueMatic").set(face,
                    ((OfficialHyperOsUi)getContext()).originalSystemFamily("miclock-neue-matic-compressed-black"));
        }
        face.getClass().getMethod("setClockStyleInfo",infoType).invoke(face,info);
        return face;
    }
    void refresh(String timeZone,boolean use24Hours)throws Exception{
        boolean zoneChanged=!timeZone.equals(zone);zone=timeZone;format24=use24Hours;
        for(View face:faces){
            face.getClass().getMethod("setIs24HourFormat",boolean.class).invoke(face,use24Hours);
            if(zoneChanged||!timeZone.equals(face.getClass().getMethod("getTimeZone").invoke(face)))
                face.getClass().getMethod("updateTimeZone",String.class).invoke(face,timeZone);
            face.getClass().getMethod("updateTime").invoke(face);
        }
    }
    int displayType(){return displayType;}
    void color(int value)throws Exception{
        for(View face:faces){
            Object info=face.getClass().getMethod("getClockStyleInfo").invoke(face);
            if(info==null)throw new IllegalStateException("Original clock has no style info");
            for(String method:new String[]{"setPrimaryColor","setSecondaryColor","setInfoAreaColor"})
                infoType.getMethod(method,int.class).invoke(info,value);
            face.getClass().getMethod("updateColor").invoke(face);
            // Graffiti glyphs are tinted Drawables rebuilt by updateTime;
            // the inherited updateColor does not rebind those images.
            if(template.equals("doodle"))face.getClass().getMethod("updateTime").invoke(face);
        }
        color=value;
    }
    @Override public void close(){removeAllViews();faces.clear();}
}
