package org.aliveclean;

public final class FingerprintClockAttemptTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        FingerprintClockAttempt gate=new FingerprintClockAttempt();
        check(!gate.active());
        long first=gate.begin();check(gate.active());
        gate.accepted();
        check(!gate.cancelBeforeUnlock(first)); // Finger-up or delayed failure after success.
        check(gate.active());
        check(gate.expire(first));check(!gate.active()); // Hard bound if keyguardGone never arrives.
        long old=gate.begin(),fresh=gate.begin();
        check(!gate.expire(old));check(!gate.cancelBeforeUnlock(old));check(gate.active());
        check(gate.cancelBeforeUnlock(fresh));check(!gate.active()); // Failed or abandoned touch.
        first=gate.begin();gate.clear();
        check(!gate.expire(first));check(!gate.active()); // Sleep/user switch/manual tap cancels.
        gate.accepted();check(!gate.active()); // Ordinary unlock must not create a suppression.
        first=gate.begin();gate.accepted();gate.clear();
        check(!gate.cancelBeforeUnlock(first));check(!gate.active());
        System.out.println("Fingerprint clock lifetime: "+checks+" checks passed");
    }
}
