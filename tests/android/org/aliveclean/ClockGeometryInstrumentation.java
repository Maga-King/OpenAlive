package org.aliveclean;

import android.app.Instrumentation;
import android.os.Bundle;

public final class ClockGeometryInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();final Throwable[] error={null};
        runOnMainSync(()->{try{ClockInkBoundsTest.run(getTargetContext());ClockScopeTest.run(getTargetContext());ClockTargetBoundsTest.run(getTargetContext());}catch(Throwable t){error[0]=t;}});
        if(error[0]!=null){result.putString("stream",error[0].toString());finish(1,result);return;}
        result.putString("stream","CLOCK_GEOMETRY_OK checks="+(ClockInkBoundsTest.checks+ClockScopeTest.checks+ClockTargetBoundsTest.checks));finish(-1,result);
    }
}
