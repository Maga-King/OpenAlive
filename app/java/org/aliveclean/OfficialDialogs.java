package org.aliveclean;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.LinearLayout;

/** Calls the pinned ROM Flyme dialog builder, including its list/controller code. */
final class OfficialDialogs {
    private final OfficialUi ui;
    private final java.util.ArrayList<Dialog> opened=new java.util.ArrayList<>();
    OfficialDialogs(OfficialUi ui){this.ui=ui;}

    Dialog choices(String title,CharSequence[] items,DialogInterface.OnClickListener selection){
        return create(title,items,selection,null);
    }
    Dialog content(String title,LinearLayout content){return create(title,null,null,content);}
    private Dialog create(String title,CharSequence[] items,DialogInterface.OnClickListener selection,LinearLayout content){
        try{
            Class<?> builderClass=Class.forName("G3.c",true,ui.getClassLoader());
            Object builder=builderClass.getConstructor(Context.class,int.class).newInstance(ui,
                ui.id("style","Theme.Flyme.AppCompat.Light.Dialog.Alert.Color.Blue.Dark"));
            Object params=builderClass.getField("c").get(builder);
            Class<?> type=params.getClass();
            type.getField("d").set(params,title);
            type.getField("h").set(params,ui.getText(ui.id("string","cancel")));
            if(items!=null){type.getField("m").set(params,items);type.getField("o").set(params,selection);}
            if(content!=null)type.getField("p").set(params,content);
            Dialog dialog=(Dialog)builderClass.getMethod("f").invoke(builder);
            opened.removeIf(previous->!previous.isShowing());opened.add(dialog);return dialog;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Official dialog binding",e);}
    }
    void close(){for(Dialog dialog:opened)if(dialog.isShowing())dialog.dismiss();opened.clear();}
}
