package org.aliveclean;

import android.graphics.Rect;
import android.view.View;

/** The four regions unioned by WidgetsContainerController.getVisibleRect. */
final class ColorOsWidgetBounds implements AodWidgetSpace.Bounds {
    private static final int[] IDS={-0x3f6fdc3,-0x3f6fd05,-0x3f6fefc,-0x3f6ffb7};
    private final View root;
    private final Rect union=new Rect(),scratch=new Rect();
    ColorOsWidgetBounds(View root){this.root=root;}
    public Rect read(){
        union.setEmpty();
        // Read the same child rectangles locally. The plugin command wrapper
        // allocates Bundles and logs every query, which is unsuitable per draw.
        for(int id:IDS){
            View view=root.findViewById(id);
            if(view!=null&&view.getGlobalVisibleRect(scratch)&&!scratch.isEmpty())union.union(scratch);
        }
        return union;
    }
}
