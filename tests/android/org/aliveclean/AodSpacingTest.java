package org.aliveclean;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

/** Replay the one-row/empty-second-row geometry captured from the device. */
final class AodSpacingTest {
    static int checks;
    static final class EmptyCardView extends View {EmptyCardView(Context c){super(c);}}
    static void run(Context context){
        FrameLayout window=new FrameLayout(context);window.layout(0,0,1440,3168);
        FrameLayout root=new FrameLayout(context);window.addView(root);root.layout(0,0,1440,3168);
        root.setScaleX(.9f);root.setScaleY(.9f);root.setTranslationY(695);
        FrameLayout list=new FrameLayout(context);list.setId(-0x3f6fdc3);root.addView(list);list.layout(72,830,1368,1486);
        View[] cards=new View[8];
        for(int i=0;i<8;i++){
            FrameLayout slot=new FrameLayout(context);list.addView(slot);
            int x=8+(i%4)*320,y=8+(i/4)*320;slot.layout(x,y,x+320,y+320);
            View card=i<4?new View(context):new EmptyCardView(context);slot.addView(card);card.layout(40,40,280,280);cards[i]=card;
            if(i>=4)card.setVisibility(View.INVISIBLE);
        }
        ColorOsWidgetBounds bounds=new ColorOsWidgetBounds(root);Rect expected=new Rect(),scratch=new Rect();
        for(int i=0;i<4;i++){cards[i].getGlobalVisibleRect(scratch);expected.union(scratch);}
        require(bounds.read().equals(expected),"Empty second row reserved space");
        // Even a visible edit placeholder is not occupied widget content.
        cards[4].setVisibility(View.VISIBLE);
        require(bounds.read().equals(expected),"Placeholder became an occupied card");
        FrameLayout second=(FrameLayout)cards[4].getParent();second.removeView(cards[4]);
        View real=new View(context);second.addView(real);real.layout(40,40,280,280);real.getGlobalVisibleRect(scratch);expected.union(scratch);
        require(bounds.read().equals(expected),"Real second-row widget omitted");
        real.setVisibility(View.GONE);cards[0].setAlpha(0);expected.setEmpty();
        for(int i=1;i<4;i++){cards[i].getGlobalVisibleRect(scratch);expected.union(scratch);}
        require(bounds.read().equals(expected),"Hidden widget retained occupied bounds");
        FrameLayout stack=new FrameLayout(context);window.addView(stack);stack.layout(0,0,1440,3168);
        AodStackSpace space=new AodStackSpace();
        for(float scale:new float[]{1,.9f,.85f})for(float translation:new float[]{0,31,-19}){
            stack.setScaleY(scale);stack.setTranslationY(translation);
            int floor=expected.bottom+50,padding=space.padding(stack,floor);
            Matrix matrix=new Matrix();stack.transformMatrixToGlobal(matrix);float[] point={720,padding};matrix.mapPoints(point);
            require(point[1]>=floor-.01f&&point[1]-floor<=1.01f,"Notification floor mixed screen and stack coordinates");
        }
        stack.setScaleY(0);require(space.padding(stack,1900)==0,"Noninvertible stack accepted");
    }
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
}
