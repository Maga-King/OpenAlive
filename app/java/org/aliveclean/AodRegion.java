package org.aliveclean;

/** One absolute display-space rectangle. Burn-in movement is already included. */
final class AodRegion {
    final int displayId,screenWidth,screenHeight,left,top,right,bottom;
    private AodRegion(int displayId,int width,int height,int left,int top,int right,int bottom){
        this.displayId=displayId;screenWidth=width;screenHeight=height;
        this.left=left;this.top=top;this.right=right;this.bottom=bottom;
    }
    static AodRegion create(int displayId,int width,int height,int left,int top,int right,int bottom){
        if(displayId<0||width<=0||height<=0||width>32768||height>32768||left<0||top<0||right>width||bottom>height||right<=left||bottom<=top)return null;
        return new AodRegion(displayId,width,height,left,top,right,bottom);
    }
    static AodRegion parse(int displayId,int width,int height,String text){
        if(text==null)return null;
        String[] parts=text.split("\\|",-1);if(parts.length!=4)return null;
        try{return create(displayId,width,height,Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]));}
        catch(NumberFormatException error){return null;}
    }
    // Preserve the official Rect.centerX()/centerY() integer rounding and GL Y sign.
    float sceneX(){return ((left+right)/2-screenWidth/2f)/screenWidth;}
    float sceneY(){return (screenHeight/2f-(top+bottom)/2)/screenHeight;}
    boolean fits(int display,int width,int height){
        if(display!=displayId||width<=0||height<=0)return false;
        // Allow proportionally scaled surfaces, but reject stale rotation/display snapshots.
        return Math.abs((double)width*screenHeight/((double)height*screenWidth)-1)<.01;
    }
}
