package org.aliveclean;

import android.graphics.Rect;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Glyph bounds in the exact digit-layout coordinate system used by the native query. */
final class ClockTargetBounds {
    private final ClockInkBounds ink=new ClockInkBounds();
    private final Rect layout=new Rect(),glyphs=new Rect(),box=new Rect(),painted=new Rect();
    private Class<?> targetType,containerType,digitType,textType;
    private Method containers,simpleDigits,digitView,visibleText;
    private Field drawOffset;

    boolean correct(View target,Rect destination){
        if(target==null)return false;
        try{
            if(targetType!=target.getClass()){
                targetType=target.getClass();
                containers=targetType.getMethod("getDigitalTimeContainers");
                simpleDigits=targetType.getMethod("getSimpleDigitalTimeViews");
            }
            if(containers==null||simpleDigits==null)return false;
            Object all=containers.invoke(target),numbers=simpleDigits.invoke(target);
            if(!(all instanceof List)||!(numbers instanceof List))return false;
            layout.setEmpty();glyphs.setEmpty();
            for(Object item:(List<?>)all){
                // The native query filters DigitalTimeContainer, not its still-visible child.
                if(!(item instanceof View)||((View)item).getVisibility()!=View.VISIBLE)continue;
                if(containerType!=item.getClass()){
                    containerType=item.getClass();digitView=containerType.getMethod("getDigitalTimeView");
                }
                Object value=digitView.invoke(item);if(!(value instanceof View))continue;
                View digit=(View)value;
                if(digit.getWidth()<=0||digit.getHeight()<=0)return false;
                box.set(digit.getLeft(),digit.getTop(),digit.getRight(),digit.getBottom());
                layout.union(box);
                // A colon can be a drawable or have no text layout. It is not a time digit.
                if(!((List<?>)numbers).contains(digit))continue;
                if(digitType!=digit.getClass()){
                    digitType=digit.getClass();visibleText=digitType.getMethod("getVisibleTextView");
                }
                Object text=visibleText.invoke(digit);
                if(!(text instanceof TextView))return false;
                if(textType!=text.getClass()){
                    textType=text.getClass();drawOffset=null;
                    for(Class<?> type=textType;type!=null;type=type.getSuperclass()){
                        try{drawOffset=type.getDeclaredField("fontMetricsDrawOffsetY");drawOffset.setAccessible(true);break;}
                        catch(NoSuchFieldException ignored){}
                    }
                }
                float offset=drawOffset==null?0:drawOffset.getFloat(text);
                if(!ink.measureLayout((TextView)text,offset,digit,painted))return false;
                painted.offset(digit.getLeft(),digit.getTop());glyphs.union(painted);
            }
            return ClockVisualAnchor.project(destination,layout,glyphs);
        }catch(ReflectiveOperationException|RuntimeException ignored){return false;}
    }
}
