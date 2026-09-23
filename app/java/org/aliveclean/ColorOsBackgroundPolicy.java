package org.aliveclean;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Xml;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;

/** One-shot request through Battery's own policy writer, only for this wallpaper. */
final class ColorOsBackgroundPolicy {
    private static final String PACKAGE="org.aliveclean";
    static void allow()throws Exception {
        try(PlatformProvider battery=new PlatformProvider("com.oplus.powermanager",0)){
            ArrayList<String> packages=new ArrayList<>();packages.add(PACKAGE);
            Bundle extras=new Bundle();extras.putStringArrayList("pc_white_list",packages);
            if(battery.call("power_control_add_white_list",null,extras)==null)
                throw new IllegalStateException("电池管理未接受后台运行设置");
            // Battery handles the request asynchronously and persists the customization list.
            // Read that list, which the official settings page uses, rather than the unrelated
            // user allow_list or just the generic device-idle whitelist.
            long until=SystemClock.uptimeMillis()+3000;
            do{
                if(persisted())return;
                Thread.sleep(100);
            }while(SystemClock.uptimeMillis()<until);
            throw new IllegalStateException("后台运行设置尚未写入，需在系统电池设置中确认");
        }
    }
    private static boolean persisted()throws Exception {
        String path=(String)Class.forName("com.oplus.settings.OplusSettingsConfig")
            .getMethod("getFilePath",int.class,int.class,String.class)
            .invoke(null,0,0,"battery/power_control_white_list");
        try(FileInputStream input=new FileInputStream(path)){
            XmlPullParser parser=Xml.newPullParser();parser.setInput(input,"UTF-8");
            for(int event=parser.next();event!=XmlPullParser.END_DOCUMENT;event=parser.next()){
                if(event==XmlPullParser.START_TAG&&"p".equals(parser.getName())
                    &&PACKAGE.equals(parser.getAttributeValue(null,"att")))return true;
            }
            return false;
        }catch(FileNotFoundException missing){return false;}
    }
}
