package org.aliveclean;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import java.util.HashMap;
import java.util.Map;

/** Actual native rows, including media custom cards; no shelf or empty placeholder. */
final class AodNotificationBounds implements AodWidgetSpace.Bounds {
    private final ViewGroup stack;
    private final Rect union=new Rect();
    private final RectF rect=new RectF();
    private final Matrix matrix=new Matrix();
    private final Map<Class<?>,Boolean> rowTypes=new HashMap<>();
    AodNotificationBounds(ViewGroup stack){this.stack=stack;}
    public Rect read(){
        union.setEmpty();
        for(int i=0;i<stack.getChildCount();i++){
            View row=stack.getChildAt(i);
            if(row.getVisibility()!=View.VISIBLE||row.getAlpha()<=.01f
                    ||row.getWidth()<=0||row.getHeight()<=0||!isRow(row.getClass()))continue;
            // Unclipped bounds are essential: clipping at the window bottom
            // must not become the next frame's apparent content geometry.
            rect.set(0,0,row.getWidth(),row.getHeight());
            matrix.reset();row.transformMatrixToGlobal(matrix);matrix.mapRect(rect);
            if(!rect.isEmpty())union.union((int)Math.floor(rect.left),(int)Math.floor(rect.top),
                    (int)Math.ceil(rect.right),(int)Math.ceil(rect.bottom));
        }
        return union;
    }
    private boolean isRow(Class<?> type){
        Boolean known=rowTypes.get(type);if(known!=null)return known;
        boolean result=false;
        for(Class<?> current=type;current!=null;current=current.getSuperclass()){
            String name=current.getName();
            if(name.equals("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow")
                    ||name.equals("com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow")){result=true;break;}
        }
        rowTypes.put(type,result);return result;
    }
}
