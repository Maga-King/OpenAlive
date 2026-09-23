package org.aliveclean;

/** Panoramic visibility is owned by its native show/hide client. */
final class ContinuousAodPolicy {
    static int vote(boolean enabled,String reason,int request,int state){
        return enabled&&"Panoramic-Aod-Show".equals(reason)&&request==3&&state==4?3:state;
    }
    static boolean keepCpu(boolean enabled,boolean dreaming,int requested,int displayed){
        return enabled&&dreaming&&requested==3&&displayed==3;
    }
}
