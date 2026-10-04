package org.aliveclean;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Original editor UI with portable photo/apply transactions. */
public final class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(I18n.wrap(base));}
    private RenderLoop renderer;
    private SharedPreferences draft;
    private OfficialEditor editor;
    private int mode=1;
    private boolean importing;
    private boolean awaitingApply;
    private int previousWallpaperId;
    private int importTarget; // 0 shared/lock photo, 1 independent home, 2 frame photo.
    private Surface previewSurface;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        draft=getSharedPreferences(SceneOptions.DRAFT,0);
        if(!draft.contains("aod"))new SceneOptions(getSharedPreferences(SceneOptions.APPLIED,0)).save(draft);
        if(draft.getInt("aod",0)<0)draft.edit().putInt("aod",0).apply();
        if(state==null){
            mode=Math.max(0,Math.min(2,getIntent().getIntExtra("scene",1)));
            draft.edit().remove("vivo").apply();
            int variant=getIntent().getIntExtra("cosmic_variant",0);
            if(variant==1||variant==3||variant==4||(variant>=6&&variant<=15)||(variant>=101&&variant<=105)||(variant>=201&&variant<=205))draft.edit().putInt("cosmic",variant).apply();
            else if(getIntent().getBooleanExtra("photo_editor",false))draft.edit().putInt("cosmic",0).apply();
        }
        if(state!=null){mode=state.getInt("mode",1);awaitingApply=state.getBoolean("awaitingApply",false);previousWallpaperId=state.getInt("previousWallpaperId",0);importTarget=state.getInt("importTarget",0);}
        renderer=new RenderLoop(this,SceneOptions.DRAFT);
        try{
            editor=new OfficialEditor(this,new OfficialEditor.Actions(){
                @Override public void scene(int value){mode=value;showScene();}
                @Override public void choose(String key,int value){SharedPreferences.Editor edit=draft.edit().putInt(key,value);if(key.equals("aod"))edit.putInt("cosmic",0);edit.apply();showScene();}
                @Override public void photo(){choosePhoto();}
                @Override public void crop(){openFrameCrop(new SceneOptions(draft).framePhoto,false);}
                @Override public void example(){loadPhotoExample();}
                @Override public void follow(){draft.edit().putBoolean("home_follow_lock",true).apply();showScene();}
                @Override public void apply(){applyDraft();}
                @Override public void keepLock(boolean value){draft.edit().putBoolean("cosmic_keep_lock",value).apply();showScene();}
                @Override public void continuous(String key,boolean value){draft.edit().putBoolean(key,value).apply();showScene();}
            },this);
            showScene();
        }catch(Exception e){
            android.util.Log.e("AliveClean","Official editor initialization failed",e);
            new android.app.AlertDialog.Builder(this).setTitle(I18n.t("编辑页面无法打开")).setMessage(e.toString())
                .setPositiveButton(I18n.t("关闭"),(dialog,which)->finish()).setOnCancelListener(dialog->finish()).show();
        }
    }

    private void showScene(){
        renderer.mode(mode);
        if(editor!=null)editor.update(new SceneOptions(draft),mode,importing);
    }
    private void choosePhoto(){
        SceneOptions chosen=new SceneOptions(draft);
        importTarget=chosen.pairedFrame()?(mode==0?2:0):(mode==2?1:0);
        String title=chosen.pairedFrame()?(mode==0?I18n.t("相框照片"):I18n.t("锁屏与桌面照片")):mode==2?I18n.t("桌面照片"):I18n.t("照片");
        if(chosen.cosmic!=0){startActivity(new Intent(this,DynamicLibraryActivity.class));return;}
        editor.dialogs.choices(title,new String[]{I18n.t("魅族静态壁纸"),I18n.t("自选照片")},(dialog,which)->{
                if(which==0)WallpaperLibrary.show(this,editor.dialogs,asset->importImage(()->getAssets().open(asset)));
                else editor.dialogs.choices(I18n.t("选择照片来源"),I18n.t(PhotoSources.LABELS),(sourceDialog,source)->openPhotoSource(source)).show();
            }).show();
    }
    private void openPhotoSource(int source){
        Intent intent=PhotoSources.intent(this,source);
        if(intent==null){toast(source==PhotoSources.OPPO?I18n.t("未找到可用的 OPPO 相册，请选择其他来源"):I18n.t("此照片来源不可用，请选择其他来源"));return;}
        try{startActivityForResult(intent,10);}
        catch(ActivityNotFoundException|SecurityException e){android.util.Log.e("AliveClean","Photo source unavailable",e);toast(I18n.t("无法打开")+I18n.t(PhotoSources.LABELS[source])+I18n.t("，请选择其他来源"));}
    }

    private void applyDraft() {
        if(importing)return;
        android.app.WallpaperInfo current=WallpaperManager.getInstance(this).getWallpaperInfo();
        if(current!=null&&new ComponentName(this,CleanWallpaper.class).equals(current.getComponent())){commitDraft();return;}
        // The platform checks ambient permission when binding the service. An app-side
        // permission query follows a different path and cannot diagnose that adapter.
        Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,CleanWallpaper.class));
        try{previousWallpaperId=WallpaperManager.getInstance(this).getWallpaperId(WallpaperManager.FLAG_SYSTEM);awaitingApply=true;startActivityForResult(intent,20);}catch(RuntimeException e){awaitingApply=false;android.util.Log.e("AliveClean","Wallpaper picker failed",e);toast(I18n.t("无法打开壁纸应用页面：")+e.getMessage());}
    }

    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void importBusy(boolean busy){importing=busy;showScene();}
    private void commitDraft(){
        SceneOptions chosen=new SceneOptions(draft);
        // The SystemUI bridge may become ready a little after the editor. Do not
        // make applying a wallpaper depend on that race: the bridge observes the
        // committed configuration and attaches the AOD clock when it is ready.
        if(chosen.save(getSharedPreferences(SceneOptions.APPLIED,0))){
            SceneProvider.changed(this);
            if(chosen.aod>=0){
                prepareAod();
                if(chosen.aod==1&&!SceneChannel.hasClockHost())
                    toast(I18n.t("壁纸已应用，息屏时钟正在连接"));
            }else toast(I18n.t("壁纸已应用"));
        }else toast(I18n.t("设置保存失败，请重试"));
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==30){showScene();return;}
        if(request==31){
            if(result==RESULT_OK&&data!=null){String name=data.getStringExtra("photo");
                if(name!=null&&name.matches("crop-[A-Za-z0-9._-]+")&&new File(getFilesDir(),name).isFile()){
                    SharedPreferences.Editor edit=draft.edit().putInt("cosmic",0).putString(importTarget==1?"home_photo":"photo",name);
                    if(importTarget==1)edit.putBoolean("home_follow_lock",false);edit.apply();
                }
            }
            showScene();return;
        }
        if(request==20&&awaitingApply){
            awaitingApply=false;
            WallpaperManager manager=WallpaperManager.getInstance(this);
            android.app.WallpaperInfo info=manager.getWallpaperInfo();
            boolean bound=info!=null&&new ComponentName(this,CleanWallpaper.class).equals(info.getComponent());
            if(bound&&(result==RESULT_OK||manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)!=previousWallpaperId)){
                commitDraft();
            }else if(result==RESULT_OK)toast(I18n.t("系统未切换到此壁纸，应用没有成功"));
            else toast(I18n.t("未应用，已保留编辑内容"));
            return;
        }
        if(request!=10||result!=RESULT_OK||data==null||importing)return;
        android.net.Uri returned=data.getData();
        if(returned==null&&data.getClipData()!=null&&data.getClipData().getItemCount()==1)returned=data.getClipData().getItemAt(0).getUri();
        if(returned==null){toast(I18n.t("照片来源没有返回可读取的图片"));return;}
        final android.net.Uri uri=returned;importImage(()->getContentResolver().openInputStream(uri),true);
    }

    interface ImageInput { InputStream open() throws IOException; }
    private void prepareAod(){
        importBusy(true);toast(I18n.t("壁纸已应用，正在配置息屏模式"));
        new Thread(()->{
            String result;boolean success;
            try{result=RootBridge.prepareAod(this);success=true;}
            catch(Exception e){android.util.Log.e("AliveClean","AOD setup failed",e);result=I18n.t("壁纸已应用，息屏配置未完成。\n\n")+e.getMessage();success=false;}
            final String message=result;
            final boolean ok=success;
            getSharedPreferences("integration_diagnostics",0).edit().putBoolean("success",ok).putString("message",message).apply();
            runOnUiThread(()->{if(!isDestroyed()){
                importBusy(false);
                if(ok){{if(Diagnostics.TRACE)android.util.Log.i("AliveClean","AOD setup verified");}toast(message);}
                else new android.app.AlertDialog.Builder(this,R.style.Theme_Clean_Dialog).setTitle(I18n.t("息屏配置未完成")).setMessage(message)
                    .setPositiveButton(I18n.t("重试"),(dialog,which)->prepareAod())
                    .setNeutralButton(I18n.t("复制详情"),(dialog,which)->{getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("OpenAlive",message));toast(I18n.t("已复制错误详情"));})
                    .setNegativeButton(I18n.t("关闭"),null).show();
            }});
        },"AliveApply").start();
    }
    private void importImage(ImageInput source) {
        importImage(source,false);
    }
    private void importImage(ImageInput source,boolean cropPhoto) {
        if(importing)return;
        final int target=importTarget;
        importBusy(true);
        new Thread(()->{
            File image=null;
            try{
                image=File.createTempFile("photo-",".image",getFilesDir());
                try(InputStream in=source.open();OutputStream out=new FileOutputStream(image)){
                    if(in==null)throw new IOException(I18n.t("无法读取照片"));
                    byte[] buffer=new byte[65536];long total=0;
                    for(int n;(n=in.read(buffer))!=-1;){total+=n;if(total>100L*1024*1024)throw new IOException(I18n.t("照片不能超过 100 MB"));out.write(buffer,0,n);}
                }
                Bitmap probe=ImageDecoder.decodeBitmap(ImageDecoder.createSource(image),(decoder,info,input)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                    float scale=Math.min(1f,64f/Math.max(w,h));
                    decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                probe.recycle();
                File completed=image;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){completed.delete();return;}
                    // Immutable image + one preference transaction keeps the active wallpaper intact.
                    if(target==2){importBusy(false);openFrameCrop(completed.getName(),true);return;}
                    if(cropPhoto){importBusy(false);startActivityForResult(new Intent(this,PhotoCropActivity.class).putExtra("photo",completed.getName()),31);return;}
                    SharedPreferences.Editor edit=draft.edit().putInt("cosmic",0).putString(target==1?"home_photo":"photo",completed.getName());
                    if(target==1)edit.putBoolean("home_follow_lock",false);
                    edit.apply();importBusy(false);showScene();
                });
            }catch(Exception e){
                if(image!=null)image.delete();
                runOnUiThread(()->{if(!isDestroyed()){importBusy(false);toast(I18n.t("导入失败：")+e.getMessage());}});
            }
        },"PhotoImport").start();
    }

    private void openFrameCrop(String name,boolean newPhoto){
        if(!new File(getFilesDir(),name).isFile()){toast(I18n.t("请先选择相框照片"));return;}
        startActivityForResult(new Intent(this,FrameCropActivity.class).putExtra("photo",name).putExtra("new_photo",newPhoto),30);
    }
    private void loadPhotoExample(){
        if(importing)return;importBusy(true);
        new Thread(()->{
            File frame=null,background=null;
            try{
                frame=copyExample("examples/sakura-color.jpg");background=copyExample("examples/sakura-outline.png");
                final File selectedFrame=frame,selectedBackground=background;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){selectedFrame.delete();selectedBackground.delete();return;}
                    draft.edit().putInt("aod",1).putBoolean("frame_pair",true).putString("frame_photo",selectedFrame.getName())
                        .putInt("cosmic",0).putString("photo",selectedBackground.getName()).putFloat("frame_x",.5f).putFloat("frame_y",.5f)
                        .putFloat("frame_size",1).putFloat("frame_angle",0).apply();
                    importBusy(false);showScene();toast(I18n.t("示例已载入，两处照片均可自行更换"));
                });
            }catch(Exception e){if(frame!=null)frame.delete();if(background!=null)background.delete();runOnUiThread(()->{if(!isDestroyed()){importBusy(false);toast(I18n.t("示例载入失败：")+e.getMessage());}});}
        },"PhotoExample").start();
    }
    private File copyExample(String asset)throws IOException{
        File file=File.createTempFile("example-",".image",getFilesDir());
        try(InputStream in=getAssets().open(asset);FileOutputStream out=new FileOutputStream(file)){
            byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return file;
        }catch(IOException e){file.delete();throw e;}
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("mode",mode);state.putInt("importTarget",importTarget);state.putBoolean("awaitingApply",awaitingApply);state.putInt("previousWallpaperId",previousWallpaperId);super.onSaveInstanceState(state);}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){previewSurface=new Surface(texture);renderer.attach(previewSurface,width,height);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){renderer.attach(previewSurface,width,height);}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){renderer.detach();if(previewSurface!=null){previewSurface.release();previewSurface=null;}return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
    @Override protected void onResume(){super.onResume();renderer.visible(true);showScene();}
    @Override protected void onPause(){renderer.visible(false);super.onPause();}
    @Override protected void onDestroy(){if(editor!=null)editor.close();renderer.close();super.onDestroy();}
    @Override public void onBackPressed(){if(editor==null||!editor.closePanel())super.onBackPressed();}
}
