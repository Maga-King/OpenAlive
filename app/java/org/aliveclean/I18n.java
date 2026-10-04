package org.aliveclean;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.Locale;

/**
 * UI language. Chinese source strings stay in the code; English and Russian
 * come from I18nTable, generated from app/i18n/strings.json by tools/i18n.py
 * (see BUILD.md). "System" uses the system language when it is one of
 * the bundled ones and English otherwise.
 */
final class I18n {
    static final String SYSTEM="",CHINESE="zh",ENGLISH="en",RUSSIAN="ru";
    static final String[] CHOICES={SYSTEM,CHINESE,ENGLISH,RUSSIAN};
    private static final String PREFS="language",KEY="choice";
    private static final Uri PROVIDER=Uri.parse("content://org.aliveclean.clock.runtime");
    private static volatile String choice;
    private static volatile long retryAt,fetchedAt;
    private static volatile boolean remote;
    private static volatile Locale locale,localeSystem;
    private static volatile String localeLanguage;
    private I18n(){}

    /** Translate a Chinese source string; missing entries fall back to English, then to the source. */
    static String t(String source){
        String lang=language();
        if(CHINESE.equals(lang)||source==null)return source;
        String[] row=I18nTable.get(source);
        if(row==null)return source;
        String value=RUSSIAN.equals(lang)&&row[1]!=null?row[1]:row[0];
        return value==null?source:value;
    }
    /** Marks a source string that is translated later with t(), where it is shown. */
    static String mark(String source){return source;}
    static String[] t(String[] sources){
        String[] values=new String[sources.length];
        for(int i=0;i<sources.length;i++)values[i]=t(sources[i]);
        return values;
    }

    /**
     * The UI language code. The stored choice is cached; "system" is re-resolved so
     * system changes apply. The clock editor, a separate app, re-reads it every minute.
     */
    static String language(){
        String cached=choice;long now=SystemClock.uptimeMillis();
        if((cached==null||(remote&&now-fetchedAt>60000))&&now>=retryAt){
            String fresh=readChoice();
            if(fresh!=null){choice=cached=fresh;fetchedAt=now;}
            else retryAt=now+30000;
        }
        return resolve(cached==null?SYSTEM:cached);
    }

    static Locale locale(){
        String lang=language();
        Locale system=systemLocale(),cached=locale;
        if(cached!=null&&lang.equals(localeLanguage)&&system.equals(localeSystem))return cached;
        cached=lang.equals(system.getLanguage())?system:CHINESE.equals(lang)?Locale.SIMPLIFIED_CHINESE:new Locale(lang);
        localeLanguage=lang;localeSystem=system;locale=cached;
        return cached;
    }

    static Configuration config(Configuration base){
        Configuration config=new Configuration(base);config.setLocale(locale());return config;
    }
    /** For Activity.attachBaseContext. */
    static Context wrap(Context base){return base.createConfigurationContext(config(base.getResources().getConfiguration()));}
    /** Re-apply the language when the system has reset a bundle's configuration. */
    static void ensure(Resources resources){
        if(!locale().equals(resources.getConfiguration().getLocales().get(0)))localize(resources);
    }
    /** Apply the language to a private Resources instance of an original UI bundle. */
    @SuppressWarnings("deprecation")
    static void localize(Resources resources){resources.updateConfiguration(config(resources.getConfiguration()),resources.getDisplayMetrics());}

    static String stored(Context context){String value=storedOrNull(context);return value==null?SYSTEM:value;}
    /** Null before the first unlock, when credential-protected storage is unavailable. */
    static String storedOrNull(Context context){
        try{return context.getSharedPreferences(PREFS,0).getString(KEY,SYSTEM);}catch(RuntimeException locked){return null;}
    }

    /** The language picker on the home page. */
    static void choose(Activity activity){
        String[] names={I18n.t("跟随系统语言"),"简体中文","English","Русский"}; // i18n:ignore language names
        String current=stored(activity);int checked=0;
        for(int i=0;i<CHOICES.length;i++)if(CHOICES[i].equals(current))checked=i;
        boolean night=(activity.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        new AlertDialog.Builder(activity,night?android.R.style.Theme_DeviceDefault_Dialog_Alert:android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(I18n.t("语言"))
                .setSingleChoiceItems(names,checked,(dialog,which)->{
                    dialog.dismiss();
                    if(!CHOICES[which].equals(current))apply(activity,CHOICES[which]);
                })
                .setNegativeButton(I18n.t("取消"),null).show();
    }
    private static void apply(Activity activity,String value){
        activity.getSharedPreferences(PREFS,0).edit().putString(KEY,value).commit();
        choice=value;
        // Xiaomi previews run in their own processes and keep the old language; they hold
        // only activities, so they are closed. This process also hosts the wallpaper service
        // and the providers SystemUI uses, so it keeps running and only recreates its screens.
        android.app.ActivityManager manager=(android.app.ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> running=manager==null?null:manager.getRunningAppProcesses();
        if(running!=null)for(android.app.ActivityManager.RunningAppProcessInfo process:running)
            if(process.uid==android.os.Process.myUid()&&process.processName.contains(":xiaomi_preview_"))
                android.os.Process.killProcess(process.pid);
        Intent home=activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if(home!=null)activity.startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        else activity.recreate();
    }

    /** The stored choice, or null while it cannot be read. */
    private static String readChoice(){
        Context app=application();
        if(app==null)return null;
        String pkg=app.getPackageName();
        if("org.aliveclean".equals(pkg))return storedOrNull(app);
        // The ColorOS clock editor asks the module app, which owns the setting.
        // SystemUI and system_server never make this call and follow the system.
        if(!"com.oplus.wallpapers".equals(pkg))return SYSTEM;
        remote=true;
        try{
            Bundle reply=app.getContentResolver().call(PROVIDER,"language",null,null);
            if(reply!=null&&reply.containsKey("choice"))return reply.getString("choice");
        }catch(RuntimeException unavailable){android.util.Log.w("OpenAliveI18n","Language setting unavailable",unavailable);}
        return null;
    }
    private static String resolve(String stored){
        for(String value:CHOICES)if(!value.isEmpty()&&value.equals(stored))return value;
        String system=systemLocale().getLanguage();
        return CHINESE.equals(system)||RUSSIAN.equals(system)||ENGLISH.equals(system)?system:ENGLISH;
    }
    private static Locale systemLocale(){return Resources.getSystem().getConfiguration().getLocales().get(0);}
    private static Context application(){
        try{return (Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);}
        catch(ReflectiveOperationException|RuntimeException unavailable){return null;}
    }
}
