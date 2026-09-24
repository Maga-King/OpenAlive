package org.aliveclean;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

/** Original clock XML must not inherit the host Activity's AppCompat factory. */
final class NativeClockInflater extends LayoutInflater {
    NativeClockInflater(Context context){super(context);}
    @Override public LayoutInflater cloneInContext(Context context){return new NativeClockInflater(context);}
    @Override protected View onCreateView(String name,AttributeSet attrs)throws ClassNotFoundException{
        for(String prefix:new String[]{"android.widget.","android.webkit.","android.app."}){
            try{return createView(name,prefix,attrs);}catch(ClassNotFoundException next){}
        }
        return super.onCreateView(name,attrs);
    }
}
