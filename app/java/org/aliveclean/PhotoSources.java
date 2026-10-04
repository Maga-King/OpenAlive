package org.aliveclean;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Three distinct user-selected image providers; returned URIs are copied privately. */
final class PhotoSources {
    static final int OPPO=0,GALLERY=1,FILES=2;
    static final String[] LABELS={I18n.mark("OPPO 相册"),I18n.mark("通用相册"),I18n.mark("Android 文件")};

    static Intent intent(Context context,int source){
        if(source==OPPO){
            Intent pick=new Intent(Intent.ACTION_PICK).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false);
            return installed(context,pick,new String[]{"com.coloros.gallery3d","com.oplus.gallery3d"});
        }
        if(source==GALLERY){
            Intent pick=new Intent(Intent.ACTION_PICK).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false);
            // GET_CONTENT can be intercepted by the platform Photo Picker. PICK
            // exposes the installed gallery providers as an explicit chooser.
            if(context.getPackageManager().queryIntentActivities(pick,PackageManager.MATCH_DEFAULT_ONLY).isEmpty())return null;
            return Intent.createChooser(pick,I18n.t("选择相册"));
        }
        if(source==FILES){
            Intent files=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false);
            Intent nativeFiles=installed(context,files,new String[]{"com.android.documentsui","com.google.android.documentsui"});
            if(nativeFiles!=null)return nativeFiles;
            return files.resolveActivity(context.getPackageManager())!=null?files:null;
        }
        throw new IllegalArgumentException("Photo source: "+source);
    }
    static Intent vivoMedia(Context context,String mime,boolean multiple){
        Intent pick=new Intent(Intent.ACTION_PICK).setType(mime)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent gallery=installed(context,pick,new String[]{"com.coloros.gallery3d","com.oplus.gallery3d"});
        if(gallery!=null)return gallery;
        return new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime)
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    private static Intent installed(Context context,Intent base,String[] packages){
        for(String name:packages){
            Intent intent=new Intent(base).setPackage(name);
            if(intent.resolveActivity(context.getPackageManager())!=null)return intent;
        }
        return null;
    }
}
