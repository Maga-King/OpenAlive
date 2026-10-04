package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Imports a complete new snapshot; existing/applied media are never overwritten. */
final class VivoMediaSelection {
    static final int WALLPAPER=10,VIDEO=11,COVER=12,ALBUM=13;
    final VivoOptions result;
    private final Context context;
    private final ArrayList<String> created=new ArrayList<>();
    VivoMediaSelection(Context context,VivoOptions current){this.context=context;result=VivoOptions.parse(current.json());}
    static VivoOptions defaults(VivoOptions current){
        VivoOptions result=new VivoOptions(current.family);
        result.sensitivity=current.sensitivity;result.wallpaperZoom=current.wallpaperZoom;
        return result;
    }
    void importUris(VivoEngineRuntime runtime,int kind,List<Uri> uris)throws Exception{
        if(uris.size()!=1)throw new IOException(I18n.t("请选择一个素材"));
        if(kind==VIDEO){video(uris.get(0));return;}
        ArrayList<String> selected=new ArrayList<>();
        for(Uri uri:uris){
            Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),uri),(decoder,info,source)->{
                float ratio=Math.min(1,2560f/Math.max(info.getSize().getWidth(),info.getSize().getHeight()));
                decoder.setTargetSize(Math.max(1,Math.round(info.getSize().getWidth()*ratio)),Math.max(1,Math.round(info.getSize().getHeight()*ratio)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            try{selected.add(write(bitmap));}finally{bitmap.recycle();}
        }
        if(kind==COVER){
            ensureRasterPhotos(runtime);
            result.photos.set(0,selected.get(0));
        }else if(kind==ALBUM){
            ensureRasterPhotos(runtime);
            String cover=result.photos.get(0);result.photos.clear();result.photos.add(cover);result.photos.addAll(selected);
            result.video="";result.videoDuration=0;result.videoFrames=0;
        }else{
            result.photos.clear();result.photos.addAll(selected);result.video="";
            result.subject="";result.paint="";result.area=4;
        }
        result.x=result.y=.5f;result.zoom=1;
    }
    private void ensureRasterPhotos(VivoEngineRuntime runtime)throws IOException{
        if(!result.photos.isEmpty())return;
        for(int i=0;i<2;i++){
            Bitmap bitmap=VivoImages.asset(runtime,"vivo/default/raster-"+i+".jpg");
            try{result.photos.add(write(bitmap));}finally{bitmap.recycle();}
        }
    }
    private String write(Bitmap bitmap)throws IOException{
        String name="vivo-"+UUID.randomUUID()+".png";created.add(name);
        try(FileOutputStream out=context.openFileOutput(name,0)){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException(I18n.t("照片编码失败"));
        }
        return name;
    }
    private void video(Uri uri)throws Exception{
        String name="vivo-"+UUID.randomUUID()+".mp4";created.add(name);
        try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=context.openFileOutput(name,0)){
            if(in==null)throw new IOException(I18n.t("无法读取视频"));
            byte[] buffer=new byte[65536];long total=0;
            for(int n;(n=in.read(buffer))!=-1;){total+=n;if(total>512L*1024*1024)throw new IOException(I18n.t("请选择小于 512 MB 的视频"));out.write(buffer,0,n);}
        }
        MediaMetadataRetriever media=new MediaMetadataRetriever();
        try{
            media.setDataSource(new File(context.getFilesDir(),name).getPath());
            long duration=Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if(duration<=0)throw new IOException(I18n.t("视频时长无效"));
            Bitmap first=media.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if(first==null)throw new IOException(I18n.t("无法解码视频封面"));
            try{
                if(result.photos.isEmpty()){result.photos.add(write(first));}
                result.videoFirstFrame=write(first);
            }finally{first.recycle();}
            String count=media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
            String rate=media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            result.videoRate=rate==null?30:Math.max(1,Math.round(Float.parseFloat(rate)));
            result.videoFrames=count==null?Math.max(1,(int)(duration*result.videoRate/1000)):Integer.parseInt(count);
            result.videoDuration=duration;result.video=name;
        }finally{media.release();}
    }
    void discard(){for(String file:created)context.deleteFile(file);created.clear();}
}
