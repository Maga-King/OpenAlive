package org.aliveclean;

/** Host regression for gyro lifecycle, scene timing and display-rate independence. */
public final class StereoMotionTest {
    private static int checks;
    private static void near(float actual,float expected,float tolerance,String message){
        checks++;if(!Float.isFinite(actual)||Math.abs(actual-expected)>tolerance)
            throw new AssertionError(message+": "+actual+" != "+expected);
    }
    private static void truth(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void frames(StereoMotion motion,long start,int count,long step,boolean gyro){
        for(int i=0;i<=count;i++){
            long now=start+i*step;
            if(gyro)motion.gyroscope(.35f,.7f,.1f,now);
            motion.advance(now,1);
        }
    }
    public static void main(String[] args){
        StereoMotion motion=new StereoMotion(1);
        near(motion.scale(),.87f,0,"lock crop");truth(!motion.active(),"initial idle");
        motion.change(2,true);frames(motion,1_000_000_000L,12,50_000_000L,false);
        truth(motion.scale()>.96f&&motion.scale()<1,"original unlock ease");
        frames(motion,1_600_000_000L,12,50_000_000L,false);
        near(motion.scale(),1,1e-6f,"1150ms unlock target");truth(!motion.active(),"unlock settles");
        motion.configure(0,false);motion.change(0,false);near(motion.scale(),.87f,0,"AOD crop");
        motion.gyroscope(1,1,1,3_000_000_000L);frames(motion,3_000_000_000L,10,16_666_667L,false);
        near(motion.shiftX(),0,0,"AOD ignores gyro");
        StereoMotion slow=new StereoMotion(1),fast=new StereoMotion(1);
        frames(slow,1_000_000_000L,60,16_666_667L,true);
        frames(fast,1_000_000_000L,120,8_333_333L,true);
        near(slow.shiftX(),fast.shiftX(),1e-5f,"gyro x display-rate independence");
        near(slow.shiftY(),fast.shiftY(),1e-5f,"gyro y display-rate independence");
        truth(Math.abs(slow.shiftX())>.005f,"depth moves with gyro");
        truth(Math.abs(slow.shiftX())<=.04f&&Math.abs(slow.shiftY())<=.04f,"original shift clamp");
        float x=slow.shiftX(),y=slow.shiftY();slow.pause();slow.advance(90_000_000_000L,1);
        near(slow.shiftX(),x,0,"resume retains x without elapsed-time jump");
        near(slow.shiftY(),y,0,"resume retains y without elapsed-time jump");
        frames(slow,90_000_000_000L,300,16_666_667L,false);
        near(slow.shiftX(),0,0,"stationary returns x");near(slow.shiftY(),0,0,"stationary returns y");
        truth(!slow.active(),"stationary stops rendering");
        slow.gyroscope(Float.NaN,0,0,99_000_000_000L);slow.advance(99_000_000_000L,1);
        near(slow.shiftX(),0,0,"bad sensor sample ignored");
        StereoMotion keep=new StereoMotion(2,true);
        near(keep.scale(),.87f,0,"keep-lock initial desktop crop");
        keep.change(1,false);keep.change(2,true);frames(keep,1_000_000_000L,90,16_666_667L,true);
        near(keep.scale(),.87f,0,"keep-lock unlock crop");
        truth(Math.abs(keep.shiftX())>.005f,"keep-lock desktop retains gyro");
        near(StereoMotion.multiplier(-50),.1f,1e-7f,"minimum sensitivity");
        near(StereoMotion.multiplier(0),1,0,"original sensitivity");
        near(StereoMotion.multiplier(50),2,0,"maximum sensitivity");
        near(StereoMotion.multiplier(-500),.1f,1e-7f,"low clamp");
        near(StereoMotion.multiplier(500),2,0,"high clamp");
        StereoMotion original=new StereoMotion(1),explicitZero=new StereoMotion(1),boosted=new StereoMotion(1),reduced=new StereoMotion(1);
        explicitZero.configure(0,true);boosted.configure(50,true);reduced.configure(-50,true);
        for(int i=0;i<=30;i++){
            long stamp=1_000_000_000L+i*16_666_667L;
            for(StereoMotion item:new StereoMotion[]{original,explicitZero,boosted,reduced}){
                item.gyroscope(0,.2f,0,stamp);item.advance(stamp,1);
            }
        }
        near(original.shiftX(),explicitZero.shiftX(),0,"zero sensitivity preserves original motion");
        truth(Math.abs(boosted.shiftX())>Math.abs(original.shiftX())*1.9f,"positive setting noticeably amplifies tilt");
        truth(Math.abs(reduced.shiftX())<Math.abs(original.shiftX())*.2f,"negative setting reduces tilt");
        float tilt=boosted.shiftX();boosted.configure(-50,true);
        near(boosted.shiftX(),tilt,0,"live sensitivity changes preserve position");
        StereoMotion aod=new StereoMotion(0);
        frames(aod,1_000_000_000L,30,16_666_667L,true);
        near(aod.scale(),.87f,0,"dynamic AOD retains actual scene crop");
        truth(Math.abs(aod.shiftX())>.005f&&aod.active(),"AOD gyro enabled by default");
        aod.change(1,false);aod.change(0,false);
        truth(aod.active(),"lock to AOD preserves in-flight tilt");
        tilt=aod.shiftX();aod.pause();aod.advance(900_000_000_000L,1);
        near(aod.shiftX(),tilt,0,"black-screen pause excludes hidden elapsed time in AOD");
        aod.configure(50,false);frames(aod,900_000_000_000L,360,16_666_667L,true);
        near(aod.shiftX(),0,0,"disabled AOD returns to neutral despite gyro");
        truth(!aod.active(),"disabled AOD settles without continuous rendering");
        aod.configure(50,true);frames(aod,910_000_000_000L,30,16_666_667L,true);
        truth(Math.abs(aod.shiftX())>.005f,"AOD setting re-enables gyro without rebuilding");
        frames(aod,911_000_000_000L,360,16_666_667L,false);
        truth(!aod.active(),"enabled AOD still stops frames when stationary");
        System.out.println("Stereo motion: "+checks+" checks passed");
    }
}
