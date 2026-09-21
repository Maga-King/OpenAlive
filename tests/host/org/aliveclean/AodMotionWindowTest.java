package org.aliveclean;
public final class AodMotionWindowTest {
    public static void main(String[] args){
        AodMotionWindow clock=new AodMotionWindow();
        if(clock.running(0))throw new AssertionError("not entered");
        clock.enter(1_000_000_000L);
        if(clock.speed(1_000_000_000L)!=1f||clock.speed(2_500_000_000L)!=.5f)throw new AssertionError("speed countdown");
        if(!clock.running(3_999_000_000L)||clock.running(4_000_000_000L)||clock.speed(Long.MAX_VALUE)!=0)throw new AssertionError("bounded end");
        clock.enter(5_000_000_000L);if(clock.speed(5_000_000_000L)!=1)throw new AssertionError("next sleep");
        clock.leave();if(clock.running(6_000_000_000L))throw new AssertionError("wake cancels");
        System.out.println("AOD countdown checks passed: 8");
    }
}
