package org.aliveclean;

public final class ContinuousAodPolicyTest {
    public static void main(String[] args){
        int checks=0;
        for(int state=0;state<=6;state++)for(int request=0;request<=6;request++){
            if(ContinuousAodPolicy.vote(false,"Panoramic-Aod-Show",request,state)!=state)throw new AssertionError("disabled changes state");
            for(String reason:new String[]{null,"SysUIComponent#NotificationStackScrollLayoutExtImpl","OnScreenTurningOn","AOD-mask-trigger-in-anim"}){
                if(ContinuousAodPolicy.vote(true,reason,request,state)!=state)throw new AssertionError("unrelated client changed");
                checks++;
            }
            if(ContinuousAodPolicy.vote(true,"Panoramic-Aod-Show",request,state)!=(request==3&&state==4?3:state))throw new AssertionError("OFF/ON policy changed");
            checks+=2;
        }
        // Native show -> expiry -> hide: hidden AOD must be allowed to turn OFF.
        if(ContinuousAodPolicy.vote(true,"Panoramic-Aod-Show",3,4)!=3||
                ContinuousAodPolicy.vote(true,"Panoramic-Aod-Show",1,1)!=1)throw new AssertionError("show/hide lifecycle");
        for(int requested=0;requested<=6;requested++)for(int actual=0;actual<=6;actual++){
            if(ContinuousAodPolicy.keepCpu(false,true,requested,actual)||ContinuousAodPolicy.keepCpu(true,false,requested,actual))throw new AssertionError("lease outside animated AOD");
            if(ContinuousAodPolicy.keepCpu(true,true,requested,actual)!=(requested==3&&actual==3))throw new AssertionError("lease in OFF/ON/suspended state");
            checks+=3;
        }
        System.out.println("CONTINUOUS_AOD_POLICY_OK checks="+checks);
    }
}
