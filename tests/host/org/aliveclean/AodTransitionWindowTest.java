package org.aliveclean;

public final class AodTransitionWindowTest {
    private static final String PANORAMIC="PROCESS_TYPE_PANORAMIC_AOD";
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    public static void main(String[] args){
        // Recorded failure: the 1350 ms CPU lease ended before the 3000 ms display
        // callback, so the device suspended with its transition frame still on screen.
        AodTransitionWindow window=AodTransitionWindow.prepare(true,PANORAMIC,false,500);
        check(window!=null,"native panorama request accepted");
        check(window.screenMs==3000,"photo freeze deadline preserved");
        check(window.cpuMs>window.screenMs,"DOZE callback must run before CPU release");
        check(1350<window.screenMs&&window.cpuMs>=3500,"reported early-suspend timeline covered");
        check(AodTransitionWindow.prepare(false,PANORAMIC,false,500)==null,"other wallpapers retain native timing");
        check(AodTransitionWindow.prepare(true,PANORAMIC,true,500)==null,"reshow cannot restart transition hold");
        check(AodTransitionWindow.prepare(true,"SysUIComponent#NotificationStackScrollLayoutExtImpl",false,500)==null,"notification requests cannot repeatedly renew photo hold");
        check(AodTransitionWindow.prepare(true,null,false,500)==null,"unknown request");
        for(int duration:new int[]{Integer.MIN_VALUE,-1,0,5001,Integer.MAX_VALUE})
            check(AodTransitionWindow.prepare(true,PANORAMIC,false,duration)==null,"native invalid-duration gate "+duration);
        for(int duration=1;duration<=5000;duration++){
            AodTransitionWindow candidate=AodTransitionWindow.prepare(true,PANORAMIC,false,duration);
            check(candidate.screenMs>=duration&&candidate.screenMs>=3000&&candidate.screenMs<=5000
                    &&candidate.cpuMs>=candidate.screenMs+500L&&candidate.cpuMs<=5500,
                    "bounded CPU/display coverage "+duration);
        }
        System.out.println("AOD transition window checks passed: "+checks);
    }
}
