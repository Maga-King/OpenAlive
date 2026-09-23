package org.aliveclean;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/** Occupied widget cards, excluding the RecyclerView's reserved empty row. */
final class ColorOsWidgetBounds implements AodWidgetSpace.Bounds {
    private static final int[] IDS={-0x3f6fdc3,-0x3f6fd05,-0x3f6fefc,-0x3f6ffb7};
    private final View root;
    private final Rect union=new Rect(),scratch=new Rect();
    ColorOsWidgetBounds(View root){this.root=root;}
    public Rect read(){
        union.setEmpty();
        for(int id:IDS){
            View view=root.findViewById(id);
            if(view==null)continue;
            if(id==IDS[0]&&view instanceof ViewGroup)readCards((ViewGroup)view);
            else include(view);
        }
        return union;
    }
    private void readCards(ViewGroup list){
        for(int i=0;i<list.getChildCount();i++){
            View item=list.getChildAt(i);
            if(!visible(item)||!(item instanceof ViewGroup))continue;
            ViewGroup card=(ViewGroup)item;
            for(int j=0;j<card.getChildCount();j++){
                View child=card.getChildAt(j);String name=child.getClass().getSimpleName();
                if(name.equals("EmptyCardView")||name.equals("WidgetShadowView")
                        ||name.equals("DeleteIconView")||name.equals("WidgetTransitionBlurView"))continue;
                include(child);
            }
        }
    }
    private void include(View view){
        if(visible(view)&&view.getGlobalVisibleRect(scratch)&&!scratch.isEmpty())union.union(scratch);
    }
    private boolean visible(View view){
        for(View current=view;current!=null;){
            if(current.getVisibility()!=View.VISIBLE||current.getAlpha()<=.01f)return false;
            if(current==root)return true;
            ViewParent parent=current.getParent();current=parent instanceof View?(View)parent:null;
        }
        return false;
    }
}
