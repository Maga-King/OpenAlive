package org.aliveclean;

import org.json.*;
import java.util.ArrayList;

/** A self-contained snapshot, shared by the editor and the wallpaper engine. */
final class VivoOptions {
    static final int LIQUID=1,RASTER=2,FLASH=3;
    int family,style=1,area=4,refraction=1,sensitivity;
    String pattern="d5_pattern_circle",paint="",subject="",video="";
    String videoFirstFrame="";
    long videoDuration;
    int videoFrames,videoRate=30;
    final ArrayList<String> photos=new ArrayList<>();
    // Parallel to photos: an empty entry denotes a still image. The still remains
    // a decoded poster so paused/initial frames always have a valid texture.
    final ArrayList<String> rasterVideos=new ArrayList<>();
    final Slot[] slots={new Slot(),new Slot(),new Slot()};
    static final class Slot {
        float x=.5f,y=.5f,zoom=1;
        int rotation;
        PhotoViewport viewport(){PhotoViewport p=new PhotoViewport();p.restore(x,y,zoom);return p;}
        Slot copy(){Slot s=new Slot();s.x=x;s.y=y;s.zoom=zoom;s.rotation=rotation;return s;}
        JSONObject json()throws JSONException{return new JSONObject().put("x",x).put("y",y).put("zoom",zoom).put("rotation",rotation);}
    }
    float x=.5f,y=.5f,zoom=1;
    boolean wallpaperZoom=true;
    VivoOptions(int family){this.family=family;area=family==LIQUID?2:family==FLASH?1:4;}
    static VivoOptions parse(String text){
        if(text==null||text.isEmpty())return null;
        try{
            JSONObject j=new JSONObject(text);int family=j.getInt("family");if(family<1||family>3)return null;
            VivoOptions o=new VivoOptions(family);
            o.style=Math.max(1,Math.min(family==FLASH?5:4,j.optInt("style",1)));
            o.area=Math.max(1,Math.min(4,j.optInt("area",o.area)));o.refraction=Math.max(1,Math.min(4,j.optInt("refraction",1)));
            o.sensitivity=Math.max(-50,Math.min(50,j.optInt("sensitivity",0)));
            o.wallpaperZoom=j.optBoolean("wallpaperZoom",true);
            String pattern=j.optString("pattern",o.pattern);if(pattern.matches("d5_pattern_[a-z0-9_-]+"))o.pattern=pattern;
            o.paint=safe(j.optString("paint"));o.subject=safe(j.optString("subject"));o.video=safe(j.optString("video"));
            o.videoFirstFrame=safe(j.optString("videoFirstFrame"));o.videoDuration=Math.max(0,j.optLong("videoDuration"));
            o.videoFrames=Math.max(0,j.optInt("videoFrames"));o.videoRate=Math.max(1,j.optInt("videoRate",30));
            JSONArray images=j.optJSONArray("photos");
            if(images!=null)for(int i=0;i<Math.min(family==RASTER?3:4,images.length());i++){String name=safe(images.optString(i));if(!name.isEmpty())o.photos.add(name);}
            JSONArray clips=j.optJSONArray("rasterVideos");
            for(int i=0;i<o.photos.size();i++)o.rasterVideos.add(clips==null?"":safe(clips.optString(i)));
            o.x=number(j,"x",.5f,0,1);o.y=number(j,"y",.5f,0,1);o.zoom=number(j,"zoom",1,1,8);
            JSONArray slots=j.optJSONArray("slots");
            for(int i=0;i<3;i++){
                JSONObject s=slots==null?null:slots.optJSONObject(i);Slot slot=o.slots[i];
                slot.x=s==null?o.x:number(s,"x",.5f,0,1);slot.y=s==null?o.y:number(s,"y",.5f,0,1);slot.zoom=s==null?o.zoom:number(s,"zoom",1,1,8);
                slot.rotation=s==null?0:Math.floorMod(s.optInt("rotation"),4);
            }
            return o;
        }catch(JSONException e){return null;}
    }
    private static float number(JSONObject j,String key,float fallback,float lo,float hi){float v=(float)j.optDouble(key,fallback);return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):fallback;}
    private static String safe(String s){return s!=null&&s.matches("vivo-[A-Za-z0-9._-]+")?s:"";}
    String json(){
        try{JSONArray transforms=new JSONArray();for(Slot slot:slots)transforms.put(slot.json());
            JSONArray clips=new JSONArray();for(int i=0;i<photos.size();i++)clips.put(i<rasterVideos.size()?rasterVideos.get(i):"");
            return new JSONObject().put("slots",transforms).put("family",family).put("style",style).put("area",area).put("refraction",refraction)
            .put("pattern",pattern).put("paint",paint).put("subject",subject).put("video",video).put("sensitivity",sensitivity)
            .put("videoFirstFrame",videoFirstFrame).put("videoDuration",videoDuration).put("videoFrames",videoFrames).put("videoRate",videoRate)
            .put("photos",new JSONArray(photos)).put("rasterVideos",clips).put("x",x).put("y",y).put("zoom",zoom).put("wallpaperZoom",wallpaperZoom).toString();}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    String engineJson(){
        try{
            if(family==RASTER)return new JSONObject().put("resType",video.isEmpty()?"image":"video")
                .put("resNum",photos.isEmpty()?3:photos.size()).put("styleId",style).toString();
            return new JSONObject().put("service","com.vivo.livewallpaper.box.wallpaper."+(family==LIQUID?"NaturalEffects":"FlashCard"))
                .put("effectType",style).put("effectArea",area).put("refractionGear",refraction).put("patternStyle",pattern)
                .put("subjectState",true).put("hasDoodled",!paint.isEmpty()||(family==FLASH&&photos.isEmpty())).toString();
        }catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    PhotoViewport viewport(){PhotoViewport p=new PhotoViewport();p.restore(x,y,zoom);return p;}
    boolean animateInAod(){return family==RASTER||family==FLASH;}
    boolean sameScene(VivoOptions other){
        if(other==null)return false;
        VivoOptions copy=parse(json());copy.sensitivity=other.sensitivity;
        copy.wallpaperZoom=other.wallpaperZoom;
        return copy.json().equals(other.json());
    }
}
