package org.aliveclean;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Extra clock cards inside the existing ColorOS editor; selection stays editor-local. */
final class NativeClockStylePanel extends HorizontalScrollView {
    interface Selection { boolean select(String id, String config); }
    private final LinearLayout cards;
    private final List<View> items = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();
    private final Selection selection;
    private String selected;

    NativeClockStylePanel(Context nativeContext, String selected, Selection selection) {
        super(nativeContext);
        this.selected=selected; this.selection=selection;
        setHorizontalScrollBarEnabled(false);
        cards=new LinearLayout(nativeContext);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        try {
            int margin=nativeContext.getClassLoader().loadClass("com.oplus.keyguard.clock.digital.R$dimen")
                    .getField("digital_clock_layout_clock_selection_panel_margin_start").getInt(null);
            cards.setPaddingRelative(nativeContext.getResources().getDimensionPixelSize(margin),0,0,0);
        } catch(ReflectiveOperationException unsupported){throw new IllegalStateException(unsupported);}
        addView(cards,new LayoutParams(-2,-2));
    }

    void add(String id, String title, String config, Bitmap thumbnail) throws Exception {
        addCard(id,title,config,thumbnail);
    }
    void addText(String id,String title,String config)throws Exception{addCard(id,title,config,null);}
    private void addCard(String id,String title,String config,Bitmap thumbnail)throws Exception{
        Context context=getContext();
        ClassLoader loader=context.getClassLoader();
        // Use the installed native card dimensions, background and selected-state drawable.
        int layout=loader.loadClass("com.oplus.keyguard.clock.digital.R$layout")
                .getField("digital_clock_layout_selection_clock_time_view_center_vertical").getInt(null);
        View inflated=LayoutInflater.from(context).inflate(layout,cards,false);
        if(!(inflated instanceof ViewGroup))throw new IllegalStateException("Unsupported clock card root");
        ViewGroup card=(ViewGroup)inflated;
        card.removeAllViews();
        if(thumbnail!=null){
            ImageView image=new ImageView(context);
            image.setImageBitmap(thumbnail);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(image,new ViewGroup.LayoutParams(-1,-1));
        }else{
            android.widget.TextView text=new android.widget.TextView(context);
            text.setText(title);text.setGravity(android.view.Gravity.CENTER);
            card.addView(text,new ViewGroup.LayoutParams(-1,-1));
        }
        loader.loadClass("com.oplus.keyguard.clock.common.util.EditPanelItemDrawableUtil")
                .getMethod("applyLowEndBackgroundIfNeeded",View.class).invoke(null,card);
        card.setContentDescription(title);
        card.setFocusable(true);
        card.setOnClickListener(v->{
            if(id.equals(this.selected))return;
            if(selection.select(id,config))setSelectedStyle(id);
        });
        items.add(card);ids.add(id);cards.addView(card);
        card.setSelected(id.equals(selected));
    }

    void setSelectedStyle(String id) {
        selected=id;
        for(int i=0;i<items.size();i++)items.get(i).setSelected(ids.get(i).equals(id));
    }

    View card(int index){return items.get(index);}
    View card(String id){int index=ids.indexOf(id);if(index<0)throw new IllegalArgumentException("Clock card missing: "+id);return items.get(index);}
}
