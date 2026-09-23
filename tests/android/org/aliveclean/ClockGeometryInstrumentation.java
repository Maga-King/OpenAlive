package org.aliveclean;

import android.app.Instrumentation;
import android.os.Bundle;

public final class ClockGeometryInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();final Throwable[] error={null};
        runOnMainSync(()->{try{ClockInkBoundsTest.run(getTargetContext());ClockScopeTest.run(getTargetContext());ClockTargetBoundsTest.run(getTargetContext());AodSpacingTest.run(getTargetContext());}catch(Throwable t){error[0]=t;}});
        ActivityMonitor monitor=addMonitor(SpacingTestActivity.class.getName(),null,false);
        try{getUiAutomation().executeShellCommand("am start -n org.aliveclean.geometrytest/org.aliveclean.SpacingTestActivity").close();}catch(java.io.IOException e){throw new RuntimeException(e);}
        android.app.Activity activity=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
        if(activity==null){result.putString("stream","Layout test activity did not open");finish(1,result);return;}
        runOnMainSync(()->{try{AodSpacingTest.attached(activity);}catch(Throwable t){error[0]=t;}finally{activity.finish();}});
        if(error[0]==null)try{CosmicRenderTest.run(getTargetContext());}catch(Throwable t){error[0]=t;}
        if(error[0]!=null){result.putString("stream",error[0].toString());finish(1,result);return;}
        result.putString("stream","CLOCK_GEOMETRY_OK checks="+(ClockInkBoundsTest.checks+ClockScopeTest.checks+ClockTargetBoundsTest.checks+AodSpacingTest.checks)+" COSMIC_GL_CHECKS="+CosmicRenderTest.checks);finish(-1,result);
    }
}
