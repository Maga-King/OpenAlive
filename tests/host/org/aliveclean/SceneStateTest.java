package org.aliveclean;

public final class SceneStateTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("state check "+checks);}
    public static void main(String[] args){
        SceneState state=new SceneState();
        check(state.fallback(false,false)==2);
        check(state.accept(0,100));
        // Real failure sequence: the screen locks and visibility returns after AOD began.
        check(state.fallback(false,true)==0);
        check(state.fallback(true,true)==0);
        check(!state.accept(0,120));
        // A delayed lock broadcast must not rewind a newer transition.
        check(!state.accept(1,99));check(state.mode()==0);
        check(state.accept(1,200));
        check(state.fallback(true,true)==1);
        check(state.accept(2,250));check(state.fallback(false,true)==2);
        check(!state.accept(5,260));check(state.mode()==2);
        state.disconnect();check(!state.authoritative());
        check(state.fallback(true,true)==0);
        check(state.accept(1,1));check(state.mode()==1);
        // Desktop locks during sleep: that keyguard event must not expand the lens again.
        check(state.accept(2,10));check(state.accept(0,20,1));
        check(!state.accept(1,25));check(state.mode()==0);
        check(!state.accept(0,30));check(state.mode()==0);
        // Wake interrupts collapse; a late doze event must not rewind the new scene.
        check(state.accept(1,40,2));check(!state.accept(0,45));check(state.mode()==1);
        check(state.accept(2,50));check(!state.accept(0,30,1));check(state.mode()==2);
        // Next sleep must release the wake guard, including rapid power-button reversals.
        check(state.accept(0,60,1));check(state.accept(2,70,2));check(state.accept(0,80,1));
        check(!state.accept(2,81,1));check(!state.accept(0,81,2));check(!state.accept(1,81,3));
        // An accepted fingerprint unlock precedes wake and late lock notifications.
        check(state.accept(2,90,3));check(!state.accept(1,91,2));check(state.mode()==2);
        check(!state.accept(1,92));check(!state.accept(0,93));check(state.mode()==2);
        check(!state.accept(2,94,2));check(!state.accept(1,95));check(state.mode()==2);
        check(state.accept(0,100,1));check(state.accept(1,110,2));
        state.disconnect();check(state.accept(2,1));check(state.accept(0,2));
        System.out.println("Scene ordering checks passed: "+checks);
    }
}
