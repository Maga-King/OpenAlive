package org.aliveclean;

import android.app.Instrumentation;
import android.os.Bundle;

/** GPU checks without depending on a foreground layout fixture. */
public final class CosmicInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            CosmicRenderTest.run(getTargetContext());
            String quality=CosmicQualityTest.run(getTargetContext());
            result.putString("stream","COSMIC_GL_OK checks="+CosmicRenderTest.checks+" samples="+CosmicRenderTest.actualSamples+quality);
            finish(-1,result);
        }catch(Throwable error){result.putString("stream",error.toString());finish(1,result);}
    }
}
