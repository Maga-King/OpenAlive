package org.aliveclean;

/** Grants the disposable probe the hidden API access the system clock requires. */
public final class NativeClockEffectsInstrumentation extends android.app.Instrumentation {
    @Override public void onCreate(android.os.Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        android.os.Bundle result=new android.os.Bundle();
        try{
            java.io.File output=new java.io.File(getTargetContext().getFilesDir(),"effects.txt");
            if(output.exists()&&!output.delete())throw new IllegalStateException("Old probe result");
            android.content.Intent intent=new android.content.Intent(getTargetContext(),NativeClockEffectsProbe.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySync(intent);
            long end=android.os.SystemClock.uptimeMillis()+160000;
            while(!output.exists()&&android.os.SystemClock.uptimeMillis()<end)Thread.sleep(100);
            if(!output.exists())throw new AssertionError("Effects probe timed out");
            result.putString("stream",new String(java.nio.file.Files.readAllBytes(output.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            finish(android.app.Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("stream",android.util.Log.getStackTraceString(failure));finish(android.app.Activity.RESULT_CANCELED,result);}
    }
}
