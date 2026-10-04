package org.aliveclean;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

final class NativeVivoClockStyles {
    static final String PREFIX="org.aliveclean.clock.vivo.";
    private static final String[] KEYS={"s1-4x2","s3-2x3","s2-3x2","s4-3x2","s5-4x1","s6-2x2",
        "s7-4x4","s7-4x3","s7-4x5","s7-4x6","s7-4x2","s7-3x2","s7-3x3","s7-3x4",
        "s8-2x5","s8-2x4","s8-2x6","s8-3x5","s8-3x6","s8-4x5","s8-4x6",
        "s9-2x4","s9-2x5","s9-2x6","s9-3x5","s9-3x6","s9-4x5","s9-4x6",
        "s10-4x4","s10-4x5","s10-4x6","s11-2x4","s11-2x5","s11-2x6",
        "s12-4x4","s12-4x3","s12-4x5","s12-4x6","s12-4x2","s12-3x2","s12-3x3","s12-3x4"};
    private static Style[] styles;
    static boolean contains(String id){if(id==null||!id.startsWith(PREFIX))return false;String key=id.substring(PREFIX.length());for(String k:KEYS)if(k.equals(key))return true;return false;}
    static synchronized Style[] all(Context context)throws Exception {
        if(styles!=null)return styles;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=context.getAssets().open("native-clock/vivo/clock_styles.json")){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
        JSONArray data=new JSONArray(bytes.toString("UTF-8"));Style[] result=new Style[data.length()];
        for(int i=0;i<result.length;i++)result[i]=new Style(data.getJSONObject(i));styles=result;return styles;
    }
    static Style find(Context host,String id)throws Exception{for(Style s:all(host))if(s.id.equals(id))return s;throw new IllegalArgumentException(id);}
    static final class Style {
        final String id,key,font,grid,title,name;final int group,weight;final boolean primary;
        Style(JSONObject json)throws Exception{
            key=json.getString("style_id");id=PREFIX+key;group=Integer.parseInt(json.getString("style_group").substring(1));grid=key.substring(key.indexOf('-')+1);
            JSONObject f=json.getJSONObject("default_font");font=f.getString("font_path").substring("/system/fonts/".length());weight=f.getInt("font_weight");
            primary=json.optBoolean("group_default_style");
            String[] names={"",I18n.mark("经典横排"),I18n.mark("左侧横排"),I18n.mark("经典纵排"),I18n.mark("镂刻横排"),I18n.mark("镂刻细横排"),I18n.mark("镂刻纵排"),I18n.mark("舒展数字"),I18n.mark("舒展纵排"),I18n.mark("分列数字"),I18n.mark("丝绸方阵"),I18n.mark("丝绸纵列"),I18n.mark("方形数字")};
            name=names[group];title="Vivo · "+name+" "+grid;
        }
    }
}
