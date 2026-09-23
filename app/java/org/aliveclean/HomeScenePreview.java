package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;

/** Small, lifecycle-bound previews inside the official three cards. */
final class HomeScenePreview extends FrameLayout implements TextureView.SurfaceTextureListener {
    private final TextureView texture;
    private final int mode;
    private RenderLoop renderer;
    private Surface surface;
    private boolean active;
    HomeScenePreview(Context context,int mode){
        super(context);this.mode=mode;setBackgroundColor(Color.BLACK);
        setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,getWidth(),getHeight(),10*getResources().getDisplayMetrics().density);}});
        setClipToOutline(true);texture=new TextureView(context);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));
        if(mode==0){
            EditorClockView clock=new EditorClockView(context,null);addView(clock,new LayoutParams(-1,-1));
            clock.scene(new SceneOptions(context.getSharedPreferences(SceneOptions.APPLIED,0)).aod==1);
        }
    }
    void start(){active=true;if(texture.isAvailable()&&renderer==null)attach(texture.getSurfaceTexture(),texture.getWidth(),texture.getHeight());}
    void stop(){active=false;if(renderer!=null){renderer.close();renderer=null;}if(surface!=null){surface.release();surface=null;}}
    private void attach(SurfaceTexture value,int width,int height){
        if(!active||renderer!=null)return;
        renderer=new RenderLoop(getContext());renderer.mode(mode,false);surface=new Surface(value);renderer.attach(surface,width,height);renderer.visible(true);
    }
    public void onSurfaceTextureAvailable(SurfaceTexture value,int width,int height){attach(value,width,height);}
    public void onSurfaceTextureSizeChanged(SurfaceTexture value,int width,int height){if(active){stop();start();}}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture value){stop();return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture value){}
}
