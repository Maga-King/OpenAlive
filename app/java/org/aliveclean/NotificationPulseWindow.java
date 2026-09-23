package org.aliveclean;

/** Own only a notification's temporary display, never an existing AOD session. */
final class NotificationPulseWindow {
    private long started,deadline;
    boolean begin(long now,boolean dark,int seconds){
        if(deadline==0){if(!dark)return false;started=now;}
        deadline=Math.min(started+30000,now+seconds(seconds)*1000L);
        return true;
    }
    boolean active(){return deadline!=0;}
    long remaining(long now){return Math.max(0,deadline-now);}
    void cancel(){started=deadline=0;}
    static int seconds(int value){return value==5||value==15?value:10;}
}
