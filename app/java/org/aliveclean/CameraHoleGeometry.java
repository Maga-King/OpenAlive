package org.aliveclean;

import android.graphics.*;
import android.view.*;
import java.util.*;

/** Camera geometry is separate from the display's status-bar exclusion rectangle. */
final class CameraHoleGeometry {
    static List<RectF> resolve(Display display,DisplayCutout cutout,int width,int height,float density){
        // ColorOS OplusStatusBarUtils.loadScreenHoleData reads these same physical
        // rectangles. Choose the screen whose hole also matches this display's cutout.
        if(display!=null&&cutout!=null){
            Display.Mode largest=display.getMode();
            for(Display.Mode m:display.getSupportedModes())if((long)m.getPhysicalWidth()*m.getPhysicalHeight()>(long)largest.getPhysicalWidth()*largest.getPhysicalHeight())largest=m;
            for(String key:new String[]{"ro.oplus.display.screenhole.positon","ro.oplus.display.secondary.screenhole.position"}){
                List<RectF> candidates=mapProperty(property(key),largest.getPhysicalWidth(),largest.getPhysicalHeight(),width,height,display.getRotation());
                if(!candidates.isEmpty()&&matches(candidates,cutout.getBoundingRects()))return candidates;
            }
        }
        return FlymeNotificationLight.holes(cutout,width,height,density);
    }
    private static String property(String key){
        try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,"");}
        catch(Exception unavailable){return "";}
    }
    static List<RectF> mapProperty(String value,int physicalWidth,int physicalHeight,int width,int height,int rotation){
        ArrayList<RectF> result=new ArrayList<>();
        if(value==null||value.isEmpty()||physicalWidth<=0||physicalHeight<=0)return result;
        String[] parts=value.trim().split("[:,;\\s]+");
        if(parts.length%4!=0||parts.length>16)return result;
        int naturalWidth=rotation%2==0?width:height,naturalHeight=rotation%2==0?height:width;
        float scale=(float)naturalWidth/physicalWidth;
        // Reject incompatible displays; never turn a circular mask into an ellipse.
        if(Math.abs(physicalHeight*scale-naturalHeight)>Math.max(2,naturalHeight*.01f))return result;
        try{
            for(int i=0;i<parts.length;i+=4){
                RectF r=new RectF(Float.parseFloat(parts[i]),Float.parseFloat(parts[i+1]),Float.parseFloat(parts[i+2]),Float.parseFloat(parts[i+3]));
                if(r.isEmpty()||r.left<0||r.top<0||r.right>physicalWidth||r.bottom>physicalHeight
                        ||!Float.isFinite(r.left+r.top+r.right+r.bottom)||r.width()>physicalWidth*.4f||r.height()>physicalHeight*.2f)return Collections.emptyList();
                r.set(r.left*scale,r.top*scale,r.right*scale,r.bottom*scale);
                RectF rotated;
                if(rotation==Surface.ROTATION_90)rotated=new RectF(r.top,naturalWidth-r.right,r.bottom,naturalWidth-r.left);
                else if(rotation==Surface.ROTATION_180)rotated=new RectF(naturalWidth-r.right,naturalHeight-r.bottom,naturalWidth-r.left,naturalHeight-r.top);
                else if(rotation==Surface.ROTATION_270)rotated=new RectF(naturalHeight-r.bottom,r.left,naturalHeight-r.top,r.right);
                else rotated=r;
                result.add(rotated);
            }
        }catch(NumberFormatException error){return Collections.emptyList();}
        return result;
    }
    private static boolean matches(List<RectF> holes,List<Rect> excluded){
        for(RectF hole:holes){boolean matched=false;
            for(Rect r:excluded)if(r.contains(Math.round(hole.centerX()),Math.round(hole.centerY()))){matched=true;break;}
            if(!matched)return false;
        }
        return true;
    }
}
