package org.aliveclean;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** One bounded process-wide request; permission dialogs can pause any activity. */
final class RootAuthorization {
    enum Status {
        GRANTED("Root 已授权"),DENIED("Root 未授权"),UNAVAILABLE("Root 不可用"),TIMEOUT("Root 授权超时");
        final String text;Status(String text){this.text=text;}
    }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    // Access on the main thread only. The probe itself owns no UI/context.
    private static final ArrayList<Registration> listeners=new ArrayList<>();
    private static boolean checking;
    private static Status latest;
    private static long completedAt;
    private static final long REUSE_MILLIS=15_000;

    static final class Registration {
        private Consumer<Status> callback;
        Registration(Consumer<Status> callback){this.callback=callback;}
        void close(){callback=null;listeners.remove(this);}
        private void deliver(Status result){
            Consumer<Status> current=callback;close();
            if(current!=null)current.accept(result);
        }
    }

    static Registration watch(boolean force,Consumer<Status> callback){
        Registration registration=new Registration(callback);listeners.add(registration);
        if(checking)return registration;
        // Reuse a just-finished request across permission-dialog dismissal or
        // recreation, so denial cannot immediately trigger another prompt.
        if(!force&&latest!=null&&SystemClock.elapsedRealtime()-completedAt<REUSE_MILLIS){
            Status result=latest;MAIN.post(()->registration.deliver(result));return registration;
        }
        checking=true;
        Thread worker=new Thread(()->{
            Status result=check();
            MAIN.post(()->{
                checking=false;latest=result;completedAt=SystemClock.elapsedRealtime();
                ArrayList<Registration> pending=new ArrayList<>(listeners);
                for(Registration listener:pending)listener.deliver(result);
            });
        },"OpenAliveRootStatus");
        worker.setDaemon(true);worker.start();return registration;
    }

    private static Status check(){
        java.lang.Process process=null;
        try{
            process=new ProcessBuilder("su","-c","id -u").redirectErrorStream(true).start();
            java.lang.Process child=process;
            StringBuilder output=new StringBuilder();
            Thread reader=new Thread(()->{
                try(BufferedReader in=new BufferedReader(new InputStreamReader(child.getInputStream(),"UTF-8"))){
                    for(String line;(line=in.readLine())!=null;){synchronized(output){if(output.length()<1024)output.append(line).append('\n');}}
                }catch(IOException ignored){}
            },"OpenAliveRootStatusOutput");reader.setDaemon(true);reader.start();
            if(!process.waitFor(15,TimeUnit.SECONDS))return Status.TIMEOUT;
            reader.join(500);
            synchronized(output){return process.exitValue()==0&&output.toString().trim().equals("0")?Status.GRANTED:Status.DENIED;}
        }catch(IOException missing){return Status.UNAVAILABLE;}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return Status.TIMEOUT;}
        finally{if(process!=null){process.destroy();if(process.isAlive())process.destroyForcibly();}}
    }
    private RootAuthorization(){}
}
