package org.aliveclean;

import android.content.Context;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Invoke only after a user applies this wallpaper. Never execute shell text from a preference. */
final class RootBridge {
    static String prepareAod(Context context)throws Exception {
        if(android.os.Process.myUid()/100000!=0)throw new IOException("目前仅支持主用户的 ColorOS 息屏配置");
        if(context.getPackageManager().resolveContentProvider("com.oplus.aod.AodMachineHelperProvider",0)==null)throw new IOException("此系统未提供已适配的 ColorOS 息屏接口，请在系统设置中配置");
        String apk=context.getApplicationInfo().sourceDir;
        String command="CLASSPATH="+quote(apk)+" /system/bin/app_process /system/bin org.aliveclean.PlatformApply panoramic-aod";
        Process process;
        try{process=new ProcessBuilder("su","1000","-c",command).redirectErrorStream(true).start();}
        catch(IOException error){throw new IOException("无法启动 su，请检查 Root 是否可用",error);}
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(()->{
            try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){for(String line;(line=in.readLine())!=null;){synchronized(output){if(output.length()<65536)output.append(line).append('\n');}}}catch(IOException ignored){}
        },"AliveApplyOutput");reader.start();
        if(!process.waitFor(30,TimeUnit.SECONDS)){
            process.destroyForcibly();
            boolean started;synchronized(output){started=output.indexOf("ALIVE_ADAPTER_STARTED")>=0;}
            throw new IOException(started?"已获得 Root，但系统息屏接口响应超时":"su 等待超时，请查看超级用户授权提示");
        }
        reader.join(1000);String result; synchronized(output){result=output.toString();}
        for(String line:result.split("\n"))if(line.startsWith("ALIVE_RESULT ")){
            JSONObject json=new JSONObject(line.substring(13));
            if(process.exitValue()==0&&json.optBoolean("ok")){
                return json.optBoolean("backgroundAllowed")
                    ?"壁纸已应用，已开启全景息屏并允许后台运行"
                    :"壁纸已应用；后台运行设置未确认，请在系统电池设置中选择完全允许后台行为";
            }
            String stage=json.optString("stage","unknown");
            String detail=json.optString("error","系统拒绝切换息屏模式");
            throw new IOException("系统息屏接口失败（"+stage+"）："+detail);
        }
        android.util.Log.e("AliveClean","Adapter exit="+process.exitValue()+" output="+result);
        String detail=result.trim();if(detail.length()>600)detail=detail.substring(detail.length()-600);
        throw new IOException("息屏辅助进程未返回结果（退出码 "+process.exitValue()+"）"+(detail.isEmpty()?"，请检查超级用户授权":"："+detail));
    }
    private static String quote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}
}
