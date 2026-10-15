package org.aliveclean;

public final class ImmersiveBackdropPolicyTest {
    private static boolean covered(int missing,float view,float surface){
        return ImmersiveBackdropPolicy.covered((missing&1)==0,(missing&2)==0,(missing&4)==0,
                (missing&8)==0,(missing&16)==0,(missing&32)==0,(missing&64)==0,
                (missing&128)==0,(missing&256)!=0,view,surface);
    }
    public static void main(String[] args){
        if(!covered(0,1,1))throw new AssertionError("stable opaque music should suspend covered wallpaper");
        // Each failure independently restores rendering: switching wallpaper, music exit,
        // entry/exit animation, unlock, detached/reduced/transparent host, invalid surfaces.
        for(int missing=1;missing<=256;missing<<=1)
            if(covered(missing,1,1))throw new AssertionError("unsafe coverage: "+missing);
        for(float alpha:new float[]{0f,.5f,.999f,Float.NaN,Float.POSITIVE_INFINITY}){
            if(covered(0,alpha,1)||covered(0,1,alpha))throw new AssertionError("fade must restore renderer: "+alpha);
        }
        System.out.println("IMMERSIVE_BACKDROP_POLICY_OK");
    }
}
