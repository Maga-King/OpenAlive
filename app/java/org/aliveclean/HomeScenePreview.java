package org.aliveclean;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Small, lifecycle-bound previews inside the official three cards. */
final class HomeScenePreview extends FrameLayout implements TextureView.SurfaceTextureListener {
    private final TextureView texture;
    private final int mode;
    private RenderLoop renderer;
    private Surface surface;
    private boolean active;
    private final ImageView thumbnail;
    private final EditorClockView clock;
    private XiaomiHomePreview.Card card;
    private XiaomiHomePreview.Clock xiaomiClock;
    private final View dim;
    private boolean adaptive;
    private float radius=10;
    HomeScenePreview(Context context,int mode){
        super(context);this.mode=mode;setBackgroundColor(Color.BLACK);
        setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,getWidth(),getHeight(),radius*getResources().getDisplayMetrics().density);}});
        setClipToOutline(true);texture=new TextureView(context);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));
        thumbnail=new ImageView(context);thumbnail.setScaleType(ImageView.ScaleType.FIT_XY);thumbnail.setVisibility(GONE);addView(thumbnail,new LayoutParams(-1,-1));
        clock=mode==0?new EditorClockView(context,null):null;if(clock!=null)addView(clock,new LayoutParams(-1,-1));
        // 自适应 AOD previews the lock composition under the dimmed ColorOS AOD.
        dim=new View(context);dim.setBackgroundColor(0x99000000);dim.setVisibility(GONE);addView(dim,new LayoutParams(-1,-1));
    }
    /** Current frame, small: the home page blurs it into the page backdrop. */
    Bitmap snapshot(){
        if(card!=null&&card.image!=null)return card.image;
        if(!texture.isAvailable()||texture.getWidth()==0)return null;
        return texture.getBitmap(Math.max(1,texture.getWidth()/2),Math.max(1,texture.getHeight()/2));
    }
    int mode(){return mode;}
    void radius(float dp){radius=dp;invalidateOutline();}
    void start(){start(null);}
    void start(XiaomiHomePreview.Card value){
        stop();card=value;active=true;
        adaptive=mode==0&&AdaptiveAod.active(getContext());dim.setVisibility(adaptive?VISIBLE:GONE);
        if(xiaomiClock!=null){removeView(xiaomiClock);xiaomiClock=null;}
        texture.setVisibility(card==null?VISIBLE:GONE);thumbnail.setVisibility(card==null?GONE:VISIBLE);thumbnail.setImageBitmap(card==null?null:card.image);
        if(clock!=null)clock.scene(!adaptive&&card==null&&new SceneOptions(getContext().getSharedPreferences(SceneOptions.APPLIED,0)).aod==1);
        if(card!=null&&mode==0&&!adaptive)try{xiaomiClock=new XiaomiHomePreview.Clock(getContext(),card.pack.clock);addView(xiaomiClock,new LayoutParams(-1,-1));xiaomiClock.active(true);}
        catch(Exception|LinkageError error){android.util.Log.w("OpenAliveXiaomi","Home AOD clock",error);}
        if(card==null&&texture.isAvailable())attach(texture.getSurfaceTexture(),texture.getWidth(),texture.getHeight());
    }
    boolean openXiaomi(){if(card==null)return false;getContext().startActivity(XiaomiPreviewActivity.intent(getContext(),card.pack,adaptive?1:mode));return true;}
    void stop(){active=false;releaseSurface();if(clock!=null)clock.scene(false);if(xiaomiClock!=null)xiaomiClock.active(false);}
    private void releaseSurface(){if(renderer!=null){renderer.close();renderer=null;}if(surface!=null){surface.release();surface=null;}}
    private void attach(SurfaceTexture value,int width,int height){
        if(!active||card!=null||renderer!=null)return;
        renderer=new RenderLoop(getContext());renderer.mode(adaptive?1:mode,false);surface=new Surface(value);renderer.attach(surface,width,height);renderer.visible(true);
    }
    public void onSurfaceTextureAvailable(SurfaceTexture value,int width,int height){attach(value,width,height);}
    public void onSurfaceTextureSizeChanged(SurfaceTexture value,int width,int height){releaseSurface();attach(value,width,height);}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture value){releaseSurface();return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture value){}
}
