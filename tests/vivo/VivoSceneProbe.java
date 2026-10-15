package org.aliveclean;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.hardware.*;
import android.opengl.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the production host with the unchanged Vivo engine on the connected ROM. */
public final class VivoSceneProbe extends Instrumentation {
    public static final class ProbeActivity extends android.app.Activity {
        @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            android.widget.TextView label=new android.widget.TextView(this);label.setText("OpenAlive 渲染校验中");label.setGravity(17);label.setTextSize(22);setContentView(label);}
    }
    static final int W=360,H=800;
    final StringBuilder report=new StringBuilder();
    boolean coordinateOnly,mediaOnly,editorOnly;
    interface Work {void run()throws Exception;}
    void main(Work work)throws Exception{
        Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable t){error[0]=t;}});
        if(error[0]!=null)throw new Exception("Main-thread test failed",error[0]);
    }
    static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    void liquidSensitivity(VivoEngineRuntime runtime,VivoCalls api)throws Exception{
        Class<?> callback=api.type("e4.a");float[][] samples={new float[3],new float[3]};Object[] sensors=new Object[2];
        for(int i=0;i<2;i++){
            final int index=i;
            Object receiver=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,args)->{
                if(m.getName().equals("p"))for(int axis=0;axis<3;axis++)samples[index][axis]=(Float)args[axis];return null;
            });
            sensors[i]=api.make("e4.b",new Class<?>[]{Context.class,callback,int.class},runtime,receiver,1);
        }
        Object original=api.get(sensors[1],"b");
        Constructor<SensorEvent> ctor=SensorEvent.class.getDeclaredConstructor(int.class);ctor.setAccessible(true);
        SensorEvent event=ctor.newInstance(3);event.sensor=(Sensor)api.get(sensors[0],"c");
        try(VivoLiquidSensitivity scaled=new VivoLiquidSensitivity(api,sensors[1],0)){
            for(int relative:new int[]{0,-50,50,0,25,-25,0}){
                scaled.relative(relative);float factor=VivoSensorGain.multiplier(relative);
                for(int step=0;step<100;step++){
                    event.values[0]=step<80?0:(step%3-1)*1.2f;event.values[1]=step<80?0:(step%2)*.8f;event.values[2]=9.81f;
                    float[] before=event.values.clone();
                    ((SensorEventListener)sensors[0]).onSensorChanged(event);((SensorEventListener)sensors[1]).onSensorChanged(event);
                    check(Arrays.equals(event.values,before),"Liquid gain mutates raw sensor event");
                    for(String field:new String[]{"g","h","i"})check(api.get(sensors[0],field).equals(api.get(sensors[1],field)),"Slider disturbed gravity filter");
                    for(int axis=0;axis<3;axis++)check(Math.abs(samples[1][axis]-samples[0][axis]*factor)<.000001,"Liquid motion gain mismatch "+relative);
                }
            }
        }
        check(api.get(sensors[1],"b")==original,"Liquid callback not restored on release");
        report.append("Liquid native accelerometer: -50..50 motion gain, exact default, unchanged gravity filter and release OK\n");
    }
    void aodToggle(VivoEngineScene scene,VivoOptions options,VivoEngineRuntime runtime,int baseline)throws Exception{
        check(VivoOptions.parse("{\"family\":"+options.family+"}").animateInAod(),"Legacy config must default to animated AOD");
        Object controller=new VivoCalls(runtime).get(scene,"controller");
        VivoCalls api=new VivoCalls(runtime);
        scene.mode(0,false);main(()->{});
        check((Boolean)api.get(scene,"running"),"Default AOD stopped family="+options.family);
        for(int i=0;i<2;i++){
            options.aodAnimation=false;
            VivoOptions saved=VivoOptions.parse(options.json());check(!saved.animateInAod(),"AOD off was not saved");
            check(scene.updateSensitivity(saved),"AOD toggle rebuilt scene");main(()->{});scene.draw();
            check(!(Boolean)api.get(scene,"running"),"AOD off still animating");
            main(()->check(listeners(runtime)==baseline,"AOD off retained sensors"));
            scene.mode(1,false);main(()->{});
            check((Boolean)api.get(scene,"running"),"AOD off incorrectly stops lock-screen animation");
            scene.mode(0,false);main(()->{});
            check(!(Boolean)api.get(scene,"running"),"Next AOD ignores disabled option");
            options.aodAnimation=true;check(scene.updateSensitivity(options),"AOD resume rebuilt scene");main(()->{});
            check((Boolean)api.get(scene,"running"),"AOD reenable did not resume");
            scene.visible(false);main(()->{});
            check(!(Boolean)api.get(scene,"running"),"Black screen still animating");
            main(()->check(listeners(runtime)==baseline,"Black screen retained sensors"));
            scene.visible(true);main(()->{});scene.draw();
            check((Boolean)api.get(scene,"running"),"Black-to-AOD did not resume");
            check(controller==api.get(scene,"controller"),"AOD lifecycle replaced controller");
        }
        scene.mode(1,false);main(()->{});
    }
    int[] pixels(){
        ByteBuffer bytes=ByteBuffer.allocateDirect(W*H*4);GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,bytes);
        int[] result=new int[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++){int k=4*((H-1-y)*W+x);result[y*W+x]=0xff000000|((bytes.get(k)&255)<<16)|((bytes.get(k+1)&255)<<8)|(bytes.get(k+2)&255);}return result;
    }
    void coordinates(VivoEngineRuntime runtime,VivoCalls api)throws Exception{
        Bitmap chart=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(chart);Paint paint=new Paint();
        for(int y=0;y<H;y+=10)for(int x=0;x<W;x+=10){paint.setColor(((x/10+y/10)%2)==0?Color.WHITE:Color.rgb(30,70,110));canvas.drawRect(x,y,x+10,y+10,paint);}
        for(float ty:new float[]{.25f,.75f}){
            VivoOptions options=new VivoOptions(1);options.area=4;options.wallpaperZoom=false;
            try(VivoEngineScene scene=new VivoEngineScene(runtime,options,W,H,Collections.singletonList(chart),null,null,true,()->{})){
                scene.visible(true);main(()->{});scene.visible(false);main(()->{});api.set(scene,"visible",true);
                Object lattice=api.get(api.get(api.get(scene,"controller"),"mStrategy"),"e");
                api.call(lattice,"g");scene.draw();int[] before=pixels();
                scene.touch(0,.5f,ty);main(()->{});
                for(int i=0;i<8;i++)api.call(lattice,"i",new Class<?>[]{float.class},.016f);
                scene.draw();int[] after=pixels();double mass=0,moment=0;
                for(int y=0;y<H;y++)for(int x=0;x<W;x++){int p=y*W+x;int d=Math.abs(Color.red(before[p])-Color.red(after[p]));mass+=d;moment+=d*y;}
                check(mass>1000&&Math.abs(moment/mass/H-ty)<.04,"Liquid touch lands on the wrong region");
                report.append("liquid touch ").append(ty).append(" pixel centroid=").append(moment/mass/H).append(" mass=").append(mass).append('\n');
                frame("liquid-touch-"+(int)(ty*100)+".png");
            }
        }chart.recycle();
        for(int axis=0;axis<2;axis++){
            VivoOptions options=new VivoOptions(3);options.wallpaperZoom=false;final int selectedAxis=axis;
            try(VivoImages images=VivoImages.load(getContext(),runtime,options);VivoEngineScene scene=new VivoEngineScene(runtime,options,W,H,images.photos,images.subject,images.paint,true,()->{})){
                check(images.paint!=null&&images.paint.getWidth()==1080&&images.paint.getHeight()==2400,"Factory flash drawing mask missing or still padded");
                scene.visible(true);main(()->{});Object animation=api.get(api.get(scene,"controller"),"f"),sensor=api.get(animation,"e");
                main(()->api.call(sensor,"d"));
                Constructor<SensorEvent> ctor=SensorEvent.class.getDeclaredConstructor(int.class);ctor.setAccessible(true);SensorEvent event=ctor.newInstance(3);event.sensor=(Sensor)api.get(sensor,"d");event.accuracy=3;
                for(int i=0;i<30;i++){
                    event.timestamp=20_000_000_000L+i*60_000_000L;event.values[selectedAxis]=i<5?0:.25f;
                    main(()->((SensorEventListener)sensor).onSensorChanged(event));SystemClock.sleep(60);scene.draw();
                }
                report.append("flash axis ").append(axis).append(" sensor=").append(api.get(sensor,"f")).append(',').append(api.get(sensor,"g")).append(" angle=").append(api.get(animation,"n")).append(',').append(api.get(animation,"o")).append(" intensity=").append(api.call(animation,"c")).append('\n');
                float horizontal=(Float)api.get(animation,"n"),vertical=(Float)api.get(animation,"o");
                check(axis==0?Math.abs(horizontal)<.0001f&&vertical>.35f&&vertical<.45f:Math.abs(vertical)<.0001f&&horizontal>.35f&&horizontal<.45f,"Flash loses or amplifies a gyro axis");
                frame("flash-axis-"+axis+".png");
                scene.visible(false);scene.draw();
                Object parameters=api.get(api.get(api.get(api.get(scene,"controller"),"c"),"i"),"c");
                check((Float)api.get(parameters,"d")==0f&&(Float)api.get(parameters,"e")==0f&&(Float)api.get(parameters,"f")==0f,"Hidden flash retains previous posture before queued sensor stop");
                main(()->{});scene.visible(true);main(()->{});
                main(()->{api.call(sensor,"d");check((Float)api.get(animation,"n")==0f&&(Float)api.get(animation,"o")==0f,"Flash wake did not clear both old axes");});
            }
        }

    }
    void frame(String name)throws Exception{
        ByteBuffer bytes=ByteBuffer.allocateDirect(W*H*4);GLES30.glReadPixels(0,0,W,H,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,bytes);
        int[] colors=new int[W*H];long light=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int k=4*((H-1-y)*W+x),r=bytes.get(k)&255,g=bytes.get(k+1)&255,b=bytes.get(k+2)&255;colors[y*W+x]=0xff000000|(r<<16)|(g<<8)|b;light+=r+g+b;}
        check(light>10000,"Black frame: "+name);Bitmap image=Bitmap.createBitmap(colors,W,H,Bitmap.Config.ARGB_8888);
        try(OutputStream out=new FileOutputStream(new File(getContext().getFilesDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    float[] response(VivoCalls api,Object sensor,SensorEventListener listener)throws Exception{
        api.set(sensor,"e",0L);api.set(sensor,"f",0f);api.set(sensor,"g",0f);api.set(sensor,"h",0f);
        Constructor<SensorEvent> constructor=SensorEvent.class.getDeclaredConstructor(int.class);constructor.setAccessible(true);
        SensorEvent event=constructor.newInstance(3);event.sensor=(Sensor)api.get(sensor,"d");event.accuracy=3;
        for(int i=0;i<12;i++){
            event.timestamp=10_000_000_000L+i*20_000_000L;event.values[0]=.2f;event.values[1]=.3f;event.values[2]=.4f;
            listener.onSensorChanged(event);
            check(event.values[0]==.2f&&event.values[1]==.3f&&event.values[2]==.4f,"Shared event values changed");
        }
        return new float[]{(Float)api.get(sensor,"f"),(Float)api.get(sensor,"g"),(Float)api.get(sensor,"h")};
    }
    int listeners(Context host)throws Exception{
        SensorManager manager=host.getSystemService(SensorManager.class);Field field=manager.getClass().getDeclaredField("mSensorListeners");field.setAccessible(true);
        return ((Map<?,?>)field.get(manager)).size();
    }
    void media(VivoEngineRuntime runtime,VivoCalls api)throws Exception{
        File photo=new File(getContext().getFilesDir(),"source.png"),clip=new File(getContext().getFilesDir(),"source.mp4");
        Bitmap color=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);color.eraseColor(Color.RED);
        try(OutputStream out=new FileOutputStream(photo)){color.compress(Bitmap.CompressFormat.PNG,100,out);}color.recycle();
        try(InputStream in=getContext().getAssets().open("media.mp4");OutputStream out=new FileOutputStream(clip)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
        VivoMediaSelection cover=new VivoMediaSelection(getContext(),new VivoOptions(2));
        cover.importUris(runtime,VivoMediaSelection.COVER,Collections.singletonList(android.net.Uri.fromFile(photo)));
        check(cover.result.photos.size()==3,"Replacing the cover dropped a factory raster image");
        String first=cover.result.photos.get(0);
        VivoMediaSelection album=new VivoMediaSelection(getContext(),cover.result);
        album.importUris(runtime,VivoMediaSelection.ALBUM,Collections.singletonList(android.net.Uri.fromFile(photo)));
        check(first.equals(album.result.photos.get(0))&&album.result.photos.size()==2,"Replacing raster changed cover");
        VivoMediaSelection three=new VivoMediaSelection(getContext(),album.result);
        three.importUris(runtime,VivoMediaSelection.ALBUM,Arrays.asList(android.net.Uri.fromFile(photo),android.net.Uri.fromFile(photo)));
        check(first.equals(three.result.photos.get(0))&&three.result.photos.size()==3,"Two raster images did not preserve the cover");
        VivoOptions saved=VivoOptions.parse(three.result.json());
        check(saved.photos.equals(three.result.photos)&&new org.json.JSONObject(saved.engineJson()).getInt("resNum")==3,"Three-image raster did not persist into the engine configuration");
        VivoMediaSelection overflow=new VivoMediaSelection(getContext(),saved);
        try{overflow.importUris(runtime,VivoMediaSelection.ALBUM,Arrays.asList(android.net.Uri.fromFile(photo),android.net.Uri.fromFile(photo),android.net.Uri.fromFile(photo)));throw new AssertionError("Too many raster images accepted");}
        catch(IOException expected){check(overflow.result.json().equals(saved.json()),"Rejected import changed the existing image set");}
        try(VivoImages images=VivoImages.load(getContext(),runtime,saved);VivoEngineScene scene=new VivoEngineScene(runtime,saved,W,H,images.photos,images.subject,images.paint,true,()->{})){
            check(images.photos.size()==3,"Three-image raster did not load all textures");
            scene.visible(true);main(()->{});scene.draw();frame("raster-three-custom.png");
        }
        VivoMediaSelection movie=new VivoMediaSelection(getContext(),album.result);
        movie.importUris(runtime,VivoMediaSelection.VIDEO,Collections.singletonList(android.net.Uri.fromFile(clip)));
        check(first.equals(movie.result.photos.get(0)),"Video changed selected cover");
        VivoOptions options=VivoOptions.parse(movie.result.json());
        check(options.videoFrames>0&&options.videoDuration>0,"Video metadata lost in saved options");
        check(VivoMediaSelection.defaults(options).photos.isEmpty()&&VivoMediaSelection.defaults(options).video.isEmpty(),"Default did not clear custom sources");
        check(new File(getContext().getFilesDir(),first).isFile(),"Reset deleted applied media");
        report.append("Independent cover/raster, private video import, persistence and factory reset OK\n");
        final int[] baseline={0};main(()->baseline[0]=listeners(runtime));
        try(VivoImages images=VivoImages.load(getContext(),runtime,options);VivoEngineScene scene=new VivoEngineScene(runtime,options,W,H,images.photos,images.subject,images.paint,true,()->{})){
            Object controller=api.get(scene,"controller");
            check(controller.getClass().getName().equals("Y3.c"),"Video did not select original Vivo controller");
            Object sensor=api.get(api.get(controller,"f"),"d");
            scene.visible(true);main(()->{});
            for(int i=0;i<80;i++){scene.draw();SystemClock.sleep(20);}
            int[] decoded=pixels();int varied=0;for(int pixel:decoded)if((pixel&0xffffff)!=0xff0000)varied++;
            check(varied>W*H/4,"Video stayed on selected static cover instead of decoding");
            main(()->api.call(sensor,"d"));
            Constructor<SensorEvent> ctor=SensorEvent.class.getDeclaredConstructor(int.class);ctor.setAccessible(true);
            SensorEvent tilt=ctor.newInstance(3);tilt.sensor=(Sensor)api.get(sensor,"d");tilt.values[1]=1f;
            for(int i=0;i<40;i++){tilt.timestamp=30_000_000_000L+i*30_000_000L;main(()->((SensorEventListener)sensor).onSensorChanged(tilt));scene.draw();SystemClock.sleep(30);}
            for(int i=0;i<30;i++){scene.draw();SystemClock.sleep(20);}
            int[] tilted=pixels();int changed=0;for(int i=0;i<tilted.length;i++)if(tilted[i]!=decoded[i])changed++;
            check(changed>1000,"Video did not seek when tilted");
            scene.visible(true);main(()->{});
            frame("scene-video.png");check(GLES30.glGetError()==0,"Video renderer GL error");
            main(()->check(listeners(runtime)==baseline[0]+1,"Video sensor not subscribed"));
            scene.mode(0,false);main(()->{});scene.draw();
            main(()->check(listeners(runtime)==baseline[0]+1,"Video AOD lost sensor"));
            scene.visible(false);main(()->{});
            main(()->check(listeners(runtime)==baseline[0],"Hidden video retained sensor"));
            scene.visible(true);main(()->{});
            main(()->check(listeners(runtime)==baseline[0]+1,"Video AOD wake lost sensor"));
            aodToggle(scene,options,runtime,baseline[0]);
            PhotoViewport crop=new PhotoViewport();crop.restore(.6f,.5f,1.2f);scene.crop(crop);scene.draw();
        }
        main(()->check(listeners(runtime)==baseline[0],"Closed video retained sensor"));
        report.append("Original video render, crop, visible AOD, suspend and release OK\n");
        mixedMedia(runtime,api,photo,clip);
        orderedSlots(runtime,api,photo,clip);
        subjectMask(runtime,api);
    }
    void orderedSlots(VivoEngineRuntime runtime,VivoCalls api,File photo,File clip)throws Exception{
        videoRotations(api);
        android.net.Uri imageUri=android.net.Uri.fromFile(photo),videoUri=android.net.Uri.fromFile(clip);
        VivoMediaSelection initial=new VivoMediaSelection(getContext(),new VivoOptions(2));initial.importUris(runtime,VivoMediaSelection.BATCH_IMAGES,Arrays.asList(imageUri,imageUri,imageUri));
        for(int bits=0;bits<8;bits++){
            android.util.Log.i("AliveClean","Ordered media test combination "+bits);
            VivoOptions current=VivoOptions.parse(initial.result.json());current.wallpaperZoom=false;
            for(int i=0;i<3;i++){
                VivoMediaSelection selected=new VivoMediaSelection(getContext(),current);
                selected.importUris(runtime,((bits&(1<<i))==0?VivoMediaSelection.SLOT_IMAGE:VivoMediaSelection.SLOT_VIDEO)+i,
                    Collections.singletonList((bits&(1<<i))==0?imageUri:videoUri));
                for(int j=0;j<i;j++)check(selected.result.photos.get(j).equals(current.photos.get(j))&&selected.result.rasterVideos.get(j).equals(current.rasterVideos.get(j)),"Selecting slot overwrote earlier slot");
                current=selected.result;
            }
            current.slots[0].rotation=1;current.slots[0].zoom=1.5f;
            current.slots[1].rotation=2;current.slots[1].x=.65f;current.slots[1].zoom=2;
            current.slots[2].rotation=3;current.slots[2].y=.6f;current.slots[2].zoom=2.5f;
            VivoOptions restored=VivoOptions.parse(current.json());
            check(restored.json().equals(current.json()),"Per-slot transforms did not persist");
            try(VivoImages images=VivoImages.load(getContext(),runtime,restored);VivoEngineScene scene=new VivoEngineScene(runtime,restored,W,H,images.photos,null,null,true,()->{})){
                check(images.photos.get(0).getWidth()==800&&images.photos.get(0).getHeight()==360,"Still/video poster rotation did not swap dimensions");
                Object layer=api.get(scene,"layer");List<?> crops=(List<?>)api.get(layer,"crops");
                for(int i=0;i<3;i++){
                    Bitmap b=images.photos.get(i);PhotoViewport v=restored.slots[i].viewport();v.dimensions(b.getWidth(),b.getHeight(),W,H);
                    Rect expected=new Rect(Math.round(v.left()*b.getWidth()),Math.round(v.top()*b.getHeight()),Math.round((v.left()+v.width())*b.getWidth()),Math.round((v.top()+v.height())*b.getHeight()));
                    check(expected.equals(crops.get(i)),"Native crop changed another slot or used a shared crop");
                }
                check(((List<?>)api.get(layer,"clips")).size()==Integer.bitCount(bits),"Mixed slot types opened wrong video decoders");
                scene.visible(true);main(()->{});for(int i=0;i<12;i++){SystemClock.sleep(20);scene.draw();}
                check(GLES30.glGetError()==0,"Rotated mixed slots leaked GL state");
                int lit=0;for(int color:pixels())if((color&0xffffff)!=0)lit++;check(lit>W*H/2,"Rotated mixed media rendered black");
            }
            VivoMediaSelection swapped=new VivoMediaSelection(getContext(),restored);swapped.swapSlots(runtime,0,2);
            check(swapped.result.photos.get(0).equals(restored.photos.get(2))&&swapped.result.rasterVideos.get(0).equals(restored.rasterVideos.get(2))&&swapped.result.slots[0].rotation==3&&swapped.result.slots[0].zoom==2.5f,"Reorder detached transform or video from its material");
            check(swapped.result.photos.get(1).equals(restored.photos.get(1)),"Reorder changed unrelated slot");
        }
        VivoMediaSelection batch=new VivoMediaSelection(getContext(),new VivoOptions(2));batch.importUris(runtime,VivoMediaSelection.BATCH_IMAGES,Arrays.asList(imageUri,imageUri,imageUri));
        check(batch.result.photos.size()==3&&batch.result.video.isEmpty(),"Batch selection did not fill three slots");
        VivoMediaSelection rejected=new VivoMediaSelection(getContext(),batch.result);
        try{rejected.importUris(runtime,VivoMediaSelection.BATCH_IMAGES,Arrays.asList(imageUri,imageUri,imageUri,imageUri));throw new AssertionError("Four-image batch accepted");}
        catch(IOException expected){check(rejected.result.json().equals(batch.result.json()),"Rejected batch mutated draft");}
        VivoMediaSelection nativeVideo=new VivoMediaSelection(getContext(),batch.result);nativeVideo.importUris(runtime,VivoMediaSelection.VIDEO,Collections.singletonList(videoUri));
        String path=nativeVideo.result.video;
        VivoMediaSelection cover=new VivoMediaSelection(getContext(),nativeVideo.result);cover.importUris(runtime,VivoMediaSelection.SLOT_VIDEO,Collections.singletonList(videoUri));
        check(cover.result.video.equals(path),"New cover button discarded native tilt video");
        report.append("All eight ordered image/video combinations, independent crops/rotation, reordering, three-image batch and native-video cover preservation OK\n");
    }
    void videoRotations(VivoCalls api)throws Exception{
        File clip=new File(getContext().getFilesDir(),"rotation.mp4");
        try(InputStream in=getContext().getAssets().open("rotation.mp4");OutputStream out=new FileOutputStream(clip)){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
        // Top-left, top-right, bottom-left, bottom-right: an asymmetric static
        // video distinguishes real rotation from mirrored or merely resized frames.
        int[][] corners={{Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW},{Color.BLUE,Color.RED,Color.YELLOW,Color.GREEN},
            {Color.YELLOW,Color.BLUE,Color.GREEN,Color.RED},{Color.GREEN,Color.YELLOW,Color.RED,Color.BLUE}};
        for(boolean flip:new boolean[]{false,true})for(int rotation=0;rotation<4;rotation++){
            int w=rotation%2==0?160:96,h=rotation%2==0?96:160;
            int[] texture=new int[1];GLES30.glGenTextures(1,texture,0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture[0]);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA,w,h,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,null);
            try(VivoVideoTexture player=new VivoVideoTexture(clip,texture[0],w,h,flip,false,rotation,()->{})){
                player.visible(true);for(int i=0;i<15;i++){SystemClock.sleep(20);player.update();}
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,(Integer)api.get(player,"fbo"));
                ByteBuffer bytes=ByteBuffer.allocateDirect(w*h*4);GLES30.glReadPixels(0,0,w,h,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,bytes);
                for(int corner=0;corner<4;corner++){
                    int x=(corner%2==0?w/4:3*w/4),y=(corner<2?h/4:3*h/4);if(flip)y=h-1-y;int at=4*(y*w+x),expected=corners[rotation][corner];
                    check(Math.abs((bytes.get(at)&255)-Color.red(expected))<35&&Math.abs((bytes.get(at+1)&255)-Color.green(expected))<35&&Math.abs((bytes.get(at+2)&255)-Color.blue(expected))<35,"Video rotation/mirror mismatch rotation="+rotation+" flip="+flip+" corner="+corner+" actual="+(bytes.get(at)&255)+","+(bytes.get(at+1)&255)+","+(bytes.get(at+2)&255));
                }
            }finally{GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);GLES30.glDeleteTextures(1,texture,0);GLES30.glViewport(0,0,W,H);}
        }
        report.append("Video quadrant pixels match all four rotations in both texture orientations OK\n");
    }
    void mixedMedia(VivoEngineRuntime runtime,VivoCalls api,File photo,File clip)throws Exception{
        VivoMediaSelection cover=new VivoMediaSelection(getContext(),new VivoOptions(2));
        cover.importUris(runtime,VivoMediaSelection.COVER_VIDEO,Collections.singletonList(android.net.Uri.fromFile(clip)));
        check(cover.result.photos.size()==3&&!cover.result.rasterVideos.get(0).isEmpty(),"Cover video discarded the album");
        String coverClip=cover.result.rasterVideos.get(0);
        VivoMediaSelection album=new VivoMediaSelection(getContext(),cover.result);
        album.importUris(runtime,VivoMediaSelection.ALBUM_VIDEO,Arrays.asList(android.net.Uri.fromFile(clip),android.net.Uri.fromFile(clip)));
        VivoOptions options=VivoOptions.parse(album.result.json());options.wallpaperZoom=false;
        check(options.video.isEmpty()&&options.rasterVideos.size()==3&&options.rasterVideos.get(0).equals(coverClip),"Independent video slots lost in serialization");
        try(VivoImages images=VivoImages.load(getContext(),runtime,options);VivoEngineScene scene=new VivoEngineScene(runtime,options,W,H,images.photos,null,null,true,()->{})){
            check(api.get(scene,"controller").getClass().getName().equals("M5.b"),"Mixed media bypassed original raster effect controller");
            scene.visible(true);main(()->{});for(int i=0;i<20;i++){SystemClock.sleep(20);scene.draw();}
            int[] before=pixels();for(int i=0;i<30;i++){SystemClock.sleep(20);scene.draw();}
            int[] after=pixels();int changed=0;for(int i=0;i<before.length;i++)if(before[i]!=after[i])changed++;
            check(changed>2000,"Mixed cover/content videos did not decode into native image textures");
            frame("raster-mixed-video.png");check(GLES30.glGetError()==0,"Mixed video GL state leaked");
            scene.visible(false);main(()->{});SystemClock.sleep(120);scene.draw();int[] paused=pixels();
            SystemClock.sleep(200);scene.draw();check(Arrays.equals(paused,pixels()),"Hidden video continues advancing");
            scene.visible(true);main(()->{});for(int i=0;i<15;i++){SystemClock.sleep(20);scene.draw();}
            check(!Arrays.equals(paused,pixels()),"Video did not resume");
        }
        VivoMediaSelection still=new VivoMediaSelection(getContext(),options);
        still.importUris(runtime,VivoMediaSelection.COVER,Collections.singletonList(android.net.Uri.fromFile(photo)));
        check(still.result.rasterVideos.get(0).isEmpty()&&!still.result.rasterVideos.get(1).isEmpty(),"Cover image replacement removed content videos");
        still=new VivoMediaSelection(getContext(),options);still.importUris(runtime,VivoMediaSelection.ALBUM,Collections.singletonList(android.net.Uri.fromFile(photo)));
        check(still.result.rasterVideos.get(0).equals(coverClip)&&still.result.rasterVideos.get(1).isEmpty(),"Content image replacement removed video cover");
        VivoMediaSelection legacy=new VivoMediaSelection(getContext(),new VivoOptions(2));legacy.importUris(runtime,VivoMediaSelection.VIDEO,Collections.singletonList(android.net.Uri.fromFile(clip)));
        String oldClip=legacy.result.video;
        VivoMediaSelection migration=new VivoMediaSelection(getContext(),legacy.result);migration.importUris(runtime,VivoMediaSelection.COVER_VIDEO,Collections.singletonList(android.net.Uri.fromFile(clip)));
        check(migration.result.video.equals(oldClip)&&!migration.result.rasterVideos.get(0).isEmpty(),"Selecting video cover changed native tilt-video content");
        try(VivoImages images=VivoImages.load(getContext(),runtime,migration.result);VivoEngineScene scene=new VivoEngineScene(runtime,migration.result,W,H,images.photos,null,null,true,()->{})){
            Object controller=api.get(scene,"controller"),layer=api.get(scene,"layer");
            check(controller.getClass().getName().equals("Y3.c"),"Video cover changed native seek into looping raster");
            List<?> players=(List<?>)api.get(layer,"clips");check(players.size()==1,"Native tilt-video content must bind only independent cover clip");
            check(api.get(api.get(controller,"h"),"textureId").equals(((List<?>)api.get(layer,"images")).get(0)),"Original video controller disconnected the changing cover texture");
            scene.visible(true);main(()->{});for(int i=0;i<15;i++){SystemClock.sleep(20);scene.draw();}
            int fbo=(Integer)api.get(players.get(0),"fbo");GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,fbo);int[] before=pixels();GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
            for(int i=0;i<25;i++){SystemClock.sleep(20);scene.draw();}
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,fbo);int[] after=pixels();GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
            check(!Arrays.equals(before,after),"Native tilt video cover stayed on a still poster");
        }
        VivoMediaSelection nativeImageCover=new VivoMediaSelection(getContext(),migration.result);nativeImageCover.importUris(runtime,VivoMediaSelection.COVER,Collections.singletonList(android.net.Uri.fromFile(photo)));
        check(nativeImageCover.result.video.equals(oldClip)&&nativeImageCover.result.rasterVideos.get(0).isEmpty(),"Changing cover to image changed tilt-video content");
        report.append("Three independent video textures, pause/resume, cover/content replacement OK\n");
    }
    void subjectMask(VivoEngineRuntime runtime,VivoCalls api)throws Exception{
        VivoSubjectMask.Job cancelled=new VivoSubjectMask.Job();cancelled.cancel();
        try{cancelled.check();throw new AssertionError("Cancellation ignored");}catch(java.util.concurrent.CancellationException expected){}
        try(VivoImages images=VivoImages.load(getContext(),runtime,new VivoOptions(1))){
            Bitmap source=Bitmap.createScaledBitmap(images.photos.get(0),360,800,true);
            long start=SystemClock.uptimeMillis();Bitmap mask=new VivoSubjectMask.Job().generate(getContext(),source,new RectF(0,0,1,1));
            check(mask.getWidth()==source.getWidth()&&mask.getHeight()==source.getHeight(),"Model mask coordinates changed");
            int[] values=new int[360*800];mask.getPixels(values,0,360,0,0,360,800);int foreground=0,background=0;
            for(int value:values){if(Color.red(value)>200)foreground++;if(Color.red(value)<30)background++;check(Color.red(value)==Color.green(value)&&Color.red(value)==Color.blue(value)&&Color.alpha(value)==255,"Invalid native mask channel");}
            check(foreground>1000&&background>1000,"Semantic mask failed to separate foreground/background");
            try(OutputStream out=getContext().openFileOutput("subject-model.png",0)){mask.compress(Bitmap.CompressFormat.PNG,100,out);}
            report.append("Offline semantic mask foreground=").append(foreground).append(" background=").append(background).append(" ms=").append(SystemClock.uptimeMillis()-start).append('\n');
            float[][] weights=new float[2][];
            for(int area=2;area<=3;area++){
                VivoOptions options=new VivoOptions(1);options.area=area;options.wallpaperZoom=false;
                try(VivoEngineScene scene=new VivoEngineScene(runtime,options,W,H,Collections.singletonList(source),mask,null,true,()->{})){
                    Bitmap actual=(Bitmap)api.get(scene,"liquidMask");int[] actualPixels=new int[values.length];actual.getPixels(actualPixels,0,360,0,0,360,800);
                    for(int i=0;i<values.length;i++)check(Color.red(actualPixels[i])==(area==2?Color.red(values[i]):255-Color.red(values[i])),"Wrong subject/background pixel polarity");
                    Object config=api.call(api.get(scene,"controller"),"getConfig");weights[area-2]=((float[])api.get(config,"b")).clone();scene.draw();
                }
            }
            check(weights[0].length==weights[1].length,"Subject/background lattice shapes differ");int selected=0;
            int backgroundSelected=0,different=0;
            for(int i=0;i<weights[0].length;i++){if(weights[0][i]>.5)selected++;if(weights[1][i]>.5)backgroundSelected++;if(Math.abs(weights[0][i]-weights[1][i])>.1)different++;}
            check(selected>0&&selected<weights[0].length,"Semantic mask never reached the native effect lattice");
            check(backgroundSelected>0&&different>20,"Background failed to change native lattice weights");
            VivoSubjectMask.Job running=new VivoSubjectMask.Job();final Throwable[] outcome={null};
            Thread worker=new Thread(()->{try{Bitmap unexpected=running.generate(getContext(),source,new RectF(0,0,1,1));unexpected.recycle();}catch(Throwable error){outcome[0]=error;}});worker.start();
            boolean entered=false;long deadline=SystemClock.uptimeMillis()+4000;
            while(worker.isAlive()&&SystemClock.uptimeMillis()<deadline){synchronized(running){entered=api.get(running,"running")!=null;}if(entered)break;SystemClock.sleep(2);}
            running.cancel();worker.join(5000);check(entered&&!worker.isAlive()&&outcome[0]!=null,"Running semantic inference did not cancel");
            report.append("Native subject/background lattice complement and in-flight inference cancellation OK\n");
            mask.recycle();if(source!=images.photos.get(0))source.recycle();
        }
    }
    void colors(VivoEngineRuntime runtime,EGLDisplay display,EGLConfig config,EGLContext gl,EGLSurface original)throws Exception{
        for(String name:new String[]{"raster-0.jpg","raster-1.jpg","raster-2.jpg","liquid.jpg","flash.jpg"}){
            Bitmap source=VivoImages.asset(runtime,"vivo/default/"+name);
            check(ColorSpace.get(ColorSpace.Named.DISPLAY_P3).equals(source.getColorSpace()),"Factory ICC profile lost: "+name);source.recycle();
        }
        colorFrame(runtime,false);
        android.graphics.SurfaceTexture texture=new android.graphics.SurfaceTexture(false);texture.setDefaultBufferSize(W,H);
        android.view.Surface nativeSurface=new android.view.Surface(texture);
        EGLSurface wide=VivoColorSpace.window(display,config,nativeSurface);
        try{
            check(wide!=EGL14.EGL_NO_SURFACE&&EGL14.eglMakeCurrent(display,wide,wide,gl),"Vivo native window EGL failed");
            boolean p3=VivoColorSpace.isDisplayP3(display,wide);
            colorFrame(runtime,p3);
            if(mediaOnly)media(runtime,new VivoCalls(runtime));
            report.append("Factory Display P3 ICC preserved; sRGB fallback and native window color path ").append(p3?"Display P3":"sRGB").append(" OK\n");
        }finally{
            EGL14.eglMakeCurrent(display,original,original,gl);
            if(wide!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,wide);
            nativeSurface.release();texture.release();
        }
    }
    void colorFrame(VivoEngineRuntime runtime,boolean displayP3)throws Exception{
        for(boolean sourceP3:new boolean[]{false,true}){
            Bitmap image=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888,false,ColorSpace.get(sourceP3?ColorSpace.Named.DISPLAY_P3:ColorSpace.Named.SRGB));
            ByteBuffer raw=ByteBuffer.allocateDirect(W*H*4);
            for(int i=0;i<W*H;i++)raw.put((byte)90).put((byte)160).put((byte)180).put((byte)255);
            raw.rewind();image.copyPixelsFromBuffer(raw);
            Color expected=Color.valueOf(90/255f,160/255f,180/255f,1,image.getColorSpace()).convert(ColorSpace.get(displayP3?ColorSpace.Named.DISPLAY_P3:ColorSpace.Named.SRGB));
            int[] rgb={Math.round(expected.red()*255),Math.round(expected.green()*255),Math.round(expected.blue()*255)};
            VivoOptions options=new VivoOptions(2);options.wallpaperZoom=false;
            try(VivoEngineScene scene=new VivoEngineScene(runtime,options,W,H,Arrays.asList(image,image,image),null,null,true,()->{})){
                scene.draw();ByteBuffer actual=ByteBuffer.allocateDirect(4);GLES30.glReadPixels(W/2,H/2,1,1,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,actual);
                for(int i=0;i<3;i++)check(Math.abs((actual.get(i)&255)-rgb[i])<=3,"Color profile mismatch: sourceP3="+sourceP3+" targetP3="+displayP3+" channel="+i+" expected="+rgb[i]+" actual="+(actual.get(i)&255));
                check(GLES30.glGetError()==0,"Color frame GL error");
            }finally{image.recycle();}
        }
    }
    void editor(android.app.Activity activity,VivoCalls api)throws Exception{
        final VivoMaskEditor[] holder=new VivoMaskEditor[1];final String[] saved={null};java.util.concurrent.CountDownLatch complete=new java.util.concurrent.CountDownLatch(1);
        main(()->{Bitmap photo=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);photo.eraseColor(0xff245b8b);holder[0]=new VivoMaskEditor(activity,photo,null,false,name->{saved[0]=name;complete.countDown();});});
        SystemClock.sleep(250);
        main(()->{
            android.view.View view=(android.view.View)api.get(holder[0],"canvas");Bitmap mask=(Bitmap)api.get(view,"mask");
            check(view.getWidth()>0&&view.getHeight()>0,"Mask editor was not laid out");
            float x=view.getWidth()/2f,y=view.getHeight()/2f;
            for(int action:new int[]{0,1}){android.view.MotionEvent event=android.view.MotionEvent.obtain(0,0,action,x,y,0);view.dispatchTouchEvent(event);event.recycle();}
            check(Color.red(mask.getPixel(180,400))==255,"Brush did not paint in source-photo coordinates");
            api.set(view,"erase",true);
            for(int action:new int[]{0,1}){android.view.MotionEvent event=android.view.MotionEvent.obtain(0,0,action,x,y,0);view.dispatchTouchEvent(event);event.recycle();}
            check(Color.red(mask.getPixel(180,400))==0,"Eraser did not remove mask");
            api.call(view,"undo");check(Color.red(mask.getPixel(180,400))==255,"Undo did not restore brush stroke");
            android.widget.TextView done=(android.widget.TextView)api.get(holder[0],"done");int[] location=new int[2];done.getLocationOnScreen(location);
            check(location[1]>=done.getRootWindowInsets().getInsets(android.view.WindowInsets.Type.statusBars()).top,"Done overlaps status bar");
        });
        Bitmap shot=getUiAutomation().takeScreenshot();try(OutputStream out=getContext().openFileOutput("mask-editor.png",0)){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();
        main(()->((android.view.View)api.get(holder[0],"done")).performClick());
        check(complete.await(5,java.util.concurrent.TimeUnit.SECONDS),"Mask editor failed to save");
        Bitmap persisted=BitmapFactory.decodeFile(new File(getContext().getFilesDir(),saved[0]).getPath());
        check(persisted!=null&&Color.red(persisted.getPixel(180,400))==255&&Color.red(persisted.getPixel(0,0))==0,"Saved PNG is not the edited mask");persisted.recycle();
        report.append("Actual mask dialog: photo-space brush, eraser, undo, insets and persisted PNG OK\n");
        slotEditor(activity,api);
    }
    android.view.View button(android.view.View view,String text){
        if(view instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)view).getText()))return view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View found=button(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    void slotEditor(android.app.Activity activity,VivoCalls api)throws Exception{
        VivoSlotCropEditor[] holder=new VivoSlotCropEditor[1];VivoOptions.Slot[] saved=new VivoOptions.Slot[1];
        VivoOptions.Slot initial=new VivoOptions.Slot();initial.zoom=1.4f;initial.x=.55f;
        main(()->{Bitmap photo=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(photo);Paint p=new Paint();p.setColor(Color.RED);canvas.drawRect(0,0,320,240,p);p.setColor(Color.GREEN);canvas.drawRect(320,0,640,240,p);p.setColor(Color.BLUE);canvas.drawRect(0,240,320,480,p);p.setColor(Color.YELLOW);canvas.drawRect(320,240,640,480,p);
            holder[0]=new VivoSlotCropEditor(activity,"光栅图片/视频1",photo,initial,true,slot->saved[0]=slot);
        });SystemClock.sleep(250);
        main(()->{
            android.app.Dialog dialog=(android.app.Dialog)api.get(holder[0],"dialog");android.view.View root=dialog.getWindow().getDecorView();
            button(root,"旋转 90°").performClick();Bitmap rotated=(Bitmap)api.get(holder[0],"rotated");
            check(rotated.getWidth()==480&&rotated.getHeight()==640&&rotated.getPixel(120,160)==Color.BLUE,"Crop UI rotates incorrectly");
            android.view.View view=(android.view.View)api.get(holder[0],"view");PhotoViewport viewport=(PhotoViewport)api.get(holder[0],"viewport");viewport.scale(2,.5f,.5f);
            float before=viewport.centerX(),x=view.getWidth()/2f,y=view.getHeight()/2f;
            for(int action:new int[]{0,2,1}){android.view.MotionEvent event=android.view.MotionEvent.obtain(0,10,action,action==0?x:x+60,y,0);view.dispatchTouchEvent(event);event.recycle();}
            check(viewport.centerX()<before,"Crop drag did not move current material");
            android.view.View done=button(root,"保存");int[] location=new int[2];done.getLocationOnScreen(location);int nav=done.getRootWindowInsets().getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            check(location[1]+done.getHeight()<=activity.getResources().getDisplayMetrics().heightPixels-nav,"Crop toolbar overlaps navigation bar");
        });
        Bitmap shot=getUiAutomation().takeScreenshot();try(OutputStream out=getContext().openFileOutput("slot-editor.png",0)){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();
        main(()->button(((android.app.Dialog)api.get(holder[0],"dialog")).getWindow().getDecorView(),"保存").performClick());
        check(saved[0]!=null&&saved[0].rotation==1&&saved[0].zoom==2&&initial.rotation==0&&initial.zoom==1.4f,"Crop save mutated initial values or lost rotation/zoom");
        main(()->{Bitmap photo=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);holder[0]=new VivoSlotCropEditor(activity,"封面图片/视频",photo,initial,false,slot->{throw new AssertionError("Cancel unexpectedly saved crop");});
            android.view.View root=((android.app.Dialog)api.get(holder[0],"dialog")).getWindow().getDecorView();button(root,"旋转 90°").performClick();button(root,"取消").performClick();
        });
        report.append("Actual slot editor: rotation, drag, zoom, save/cancel isolation and toolbar insets OK\n");
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);editorOnly=b!=null&&"true".equals(b.getString("editor"));coordinateOnly=b!=null&&"true".equals(b.getString("coordinates"));mediaOnly=b!=null&&"true".equals(b.getString("media"));start();}
    @Override public void onStart(){
        EGLDisplay display=EGL14.eglGetDisplay(0);EGLContext gl=EGL14.EGL_NO_CONTEXT;EGLSurface surface=EGL14.EGL_NO_SURFACE;
        try{
            android.app.Activity activity=startActivitySync(new android.content.Intent(getContext(),ProbeActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            VivoEngineRuntime runtime=VivoEngineRuntime.prepare(getContext()).get();VivoCalls api=new VivoCalls(runtime);
            if(editorOnly){editor(activity,api);Bundle result=new Bundle();result.putString("stream",report+"VIVO_SCENE_OK\n");finish(-1,result);return;}
            check(new VivoOptions(1).area==2&&new VivoOptions(3).area==1,"Downloaded native defaults lost");
            check(VivoOptions.parse("{\"family\":3,\"area\":4}").area==4,"Explicit user area overwritten");
            check(VivoSensorGain.multiplier(0)==1&&VivoSensorGain.multiplier(-50)==.1f,"Native gain/range changed");
            main(()->liquidSensitivity(runtime,api));
            VivoWallpaperZoom zoom=new VivoWallpaperZoom();long now=1_000_000_000L;
            zoom.mode(0,true,now);float previous=1.05f;
            for(int i=1;i<=90;i++){float current=zoom.sample(now+i*16_666_667L);check(current<=previous+.00001&&current>=1,"AOD zoom overshoots");previous=current;}
            check(!zoom.active()&&previous==1,"AOD zoom did not settle");
            now+=2_000_000_000L;zoom.mode(1,true,now);float halfway=zoom.sample(now+100_000_000L);zoom.mode(0,true,now+100_000_000L);check(zoom.sample(now+100_000_000L)==halfway,"Interrupted zoom jumps");
            zoom.enabled(false,1,now);check(zoom.sample(now)==1&&!zoom.active(),"Zoom off must be unscaled");
            check(VivoOptions.parse("{\"family\":2}").wallpaperZoom,"Old preferences must default to zoom on");
            VivoWallpaperDim dim=new VivoWallpaperDim();long begin=10_000_000_000L;
            for(float night:new float[]{1f,.76f})for(int destination:new int[]{1,2}){
                dim.target(night,.5f,0,false,begin);float prior=dim.sample(begin);
                dim.target(night,.5f,destination,true,begin);
                check(dim.sample(begin)==prior,"Wake resets the AOD first frame");
                for(int i=1;i<=120;i++){
                    float value=dim.sample(begin+i*16_666_667L);
                    check(value>=prior-.00001f&&value-prior<.04f&&value<=night+.00001f,"Wake flash/overshoot before night dim: "+value);prior=value;
                }
                check(!dim.active()&&Math.abs(prior-night)<.00001,"Wake target incorrect");
                begin+=3_000_000_000L;
            }
            dim.target(.76f,.5f,0,false,begin);dim.target(.76f,.5f,1,true,begin);
            float interrupted=dim.sample(begin+150_000_000L);
            dim.target(.76f,.5f,0,true,begin+150_000_000L);
            check(dim.sample(begin+150_000_000L)==interrupted,"Reversed reveal jumps");
            dim.pause();check(!dim.active()&&Math.abs(dim.sample(begin+2_000_000_000L)-.38f)<.00001,"Hidden reveal keeps animating");
            dim.target(1f,.5f,1,true,begin+3_000_000_000L);
            check(dim.sample(begin+5_000_000_000L)==1&&!dim.active(),"Disabling night dim leaves image dark");
            main(()->{
                VivoUi ui=new VivoUi(getContext(),runtime);android.widget.LinearLayout row=new android.widget.LinearLayout(ui);
                ui.inflate("item_flashcard_interact_option",row);
                android.view.View tile=ui.inflate("raster_trans_list_item_layout",row);
                Object pag=tile.findViewById(ui.id("id","iv_style_img"));
                Object file=api.type("org.libpag.PAGFile").getMethod("Load",android.content.res.AssetManager.class,String.class).invoke(null,runtime.getAssets(),"vivo/ui/raster/icon_1.pag");
                check(file!=null,"Original raster PAG resource unavailable");
                api.call(pag,"setComposition",new Class<?>[]{api.type("org.libpag.PAGComposition")},file);
                report.append("Original Vivo area/raster XML and PAG native runtime OK\n");
            });
            EGL14.eglInitialize(display,new int[1],0,new int[1],0);EGLConfig[] configs=new EGLConfig[1];int[] count={0};
            EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT|EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,configs,0,1,count,0);
            gl=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
            check(EGL14.eglMakeCurrent(display,surface,surface,gl),"EGL failed");
            GLES30.glViewport(0,0,W,H);GLES30.glClearColor(.8f,.6f,.4f,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST);GLES30.glScissor(0,0,1,1);GLES30.glColorMask(false,true,false,true);
            try(VivoDimPass pass=new VivoDimPass()){
                pass.draw(.76f,W,H);
                ByteBuffer pixel=ByteBuffer.allocateDirect(4);GLES30.glReadPixels(W/2,H/2,1,1,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixel);
                int[] expected={155,116,78};for(int i=0;i<3;i++)check(Math.abs((pixel.get(i)&255)-expected[i])<=2,"Dim pass RGB is wrong: "+i+"="+(pixel.get(i)&255));
                boolean[] mask=new boolean[4];GLES30.glGetBooleanv(GLES30.GL_COLOR_WRITEMASK,mask,0);
                check(GLES30.glIsEnabled(GLES30.GL_SCISSOR_TEST)&&!mask[0]&&mask[1]&&!mask[2]&&mask[3],"Dim pass corrupts original renderer GL state");
                check(GLES30.glGetError()==GLES30.GL_NO_ERROR,"Dim pass GL error");
            }
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST);GLES30.glColorMask(true,true,true,true);
            colors(runtime,display,configs[0],gl,surface);
            report.append("Unified AOD reveal: day/night wake has no full-bright frame; reversal, pause, RGB and GL restore OK\n");
            final int[] baseline={0};main(()->baseline[0]=listeners(runtime));
            if(mediaOnly){media(runtime,api);}else if(coordinateOnly){coordinates(runtime,api);}else{
            for(int family=1;family<=3;family++){
                VivoOptions options=new VivoOptions(family);AtomicInteger requests=new AtomicInteger();
                VivoEngineScene scene;
                try(VivoImages images=VivoImages.load(getContext(),runtime,options)){
                    scene=new VivoEngineScene(runtime,options,W,H,images.photos,images.subject,images.paint,true,requests::incrementAndGet);
                }
                try{
                    check(GLES30.glGetError()==0,"Family "+family+" construction GL error");
                    Object ctrl=api.get(scene,"controller");
                    if(family==1){
                        Object config=api.call(ctrl,"getConfig"),strategy=api.get(ctrl,"mStrategy");
                        check(strategy!=null,"Live liquid preview has no native sensor strategy");
                        float[] expected=(float[])api.get(config,"b");
                        float[] live=(float[])api.get(api.get(strategy,"e"),"q");
                        check(expected!=null&&Arrays.equals(expected,live),"Applied liquid ignores selected mask");
                        long zeros=0;for(float weight:live)if(weight==0)zeros++;
                        check(zeros>live.length/2,"Subject deformation spills into background");
                        PhotoViewport crop=new PhotoViewport();crop.restore(.6f,.5f,1.5f);scene.crop(crop);
                        float[] moved=(float[])api.get(api.get(strategy,"e"),"q");
                        check(!Arrays.equals(live,moved),"Liquid crop leaves stale deformation mask");
                        crop.reset();scene.crop(crop);
                    }else if(family==3){
                        check((Integer)api.get(api.get(ctrl,"g"),"effectArea")==options.area,"Flash host omitted original per-frame area configuration");
                        check(!(Boolean)api.get(api.get(ctrl,"g"),"b"),"Flash uses the editor highlight coordinate system");
                    }
                    VivoWallpaperDim sceneDim=(VivoWallpaperDim)api.get(scene,"dim");
                    for(float night:new float[]{.76f,1f}){
                        scene.transition(0,false,night,.5f);
                        check(Math.abs(sceneDim.sample(System.nanoTime())-night*.5f)<.00001,"Recreated AOD loses precomputed dim");
                        scene.transition(1,true,night,.5f);
                        float first=sceneDim.sample(System.nanoTime());
                        check(first>=night*.5f&&first<night*.5f+.01,"Atomic wake snapshot exposes an undimmed frame");
                    }
                    scene.transition(1,false,1f,.5f);
                    scene.visible(true);main(()->{});
                    for(int i=0;i<45;i++){scene.draw();int error=GLES30.glGetError();check(error==0,"Family "+family+" frame "+i+" GL error "+error);SystemClock.sleep(16);}frame("scene-"+family+".png");
                    scene.darkWallpaper(true,false);scene.draw();frame("scene-"+family+"-dim.png");
                    check(GLES30.glGetError()==GLES30.GL_NO_ERROR,"Dimmed original Vivo frame GL error");
                    scene.darkWallpaper(false,false);scene.draw();
                    aodToggle(scene,options,runtime,baseline[0]);
                    if(family!=1){
                        Object controller=api.get(scene,"controller"),sensor=api.get(api.get(controller,"f"),"e");
                        main(()->{
                            api.call(sensor,"d"); // Deterministic samples while the real handset stays still.
                            float[] original=response(api,sensor,(SensorEventListener)sensor);
                            for(int relative:new int[]{-50,0,50}){
                                VivoSensorGain gain=new VivoSensorGain(runtime,api,sensor,relative);
                                float[] measured=response(api,sensor,gain);float multiplier=VivoSensorGain.multiplier(relative);
                                for(int i=0;i<3;i++)check(Math.abs(measured[i]-original[i]*multiplier)<.0001,"Original integration does not scale: "+relative);
                                report.append("sensitivity ").append(relative).append(" original angles ").append(Arrays.toString(measured)).append('\n');
                            }
                            api.call(sensor,"c");
                        });
                        Object identity=api.get(scene,"controller");
                        for(int relative:new int[]{50,-50,0,25,0}){
                            options.sensitivity=relative;check(scene.updateSensitivity(options),"Sensitivity caused renderer rebuild");main(()->{});
                            check(identity==api.get(scene,"controller"),"Controller changed");
                            main(()->check(listeners(runtime)==baseline[0]+1,"Duplicate sensor subscription"));
                            scene.draw();
                        }
                        VivoOptions saved=VivoOptions.parse(options.json());check(saved.sensitivity==0,"Default not saved");
                        options.wallpaperZoom=false;check(scene.updateSensitivity(options),"Zoom switch rebuilds controller");
                        scene.mode(0,true);main(()->{});
                        if(options.animateInAod()){
                            main(()->check(listeners(runtime)==baseline[0]+1,"Visible AOD lost sensor, family="+options.family));
                            check(identity==api.get(scene,"controller"),"AOD rebuilt controller");
                            scene.draw();frame("sensor-aod-"+family+".png");
                            for(int relative:new int[]{0,25}){
                                options.sensitivity=relative;scene.updateSensitivity(options);main(()->{});
                                for(int wake=0;wake<3;wake++){
                                    scene.visible(false);main(()->{});
                                    main(()->check(listeners(runtime)==baseline[0],"Black screen retained gyro"));
                                    scene.visible(true);main(()->{});
                                    main(()->check(listeners(runtime)==baseline[0]+1,"Black-to-AOD failed to restore gyro"));
                                }
                                // Simulate a lost registration while the host still
                                // considers the scene visible, without restarting it.
                                main(()->{
                                    SensorManager manager=runtime.getSystemService(SensorManager.class);
                                    manager.unregisterListener((SensorEventListener)sensor);
                                    manager.unregisterListener((SensorEventListener)api.get(scene,"sensorGain"));
                                    check(listeners(runtime)==baseline[0],"Sensor loss was not injected");
                                });
                                scene.visible(true);main(()->{});
                                main(()->check(listeners(runtime)==baseline[0]+1,"Visible AOD did not recover lost gyro"));
                                check(identity==api.get(scene,"controller"),"Recovery rebuilt controller");
                                scene.draw();
                            }
                            options.sensitivity=0;scene.updateSensitivity(options);main(()->{});
                            report.append("family ").append(family).append(" repeated black-to-AOD and lost subscription recovery OK\n");
                            scene.mode(2,true);main(()->{});
                            main(()->check(listeners(runtime)==baseline[0]+1,"Home duplicated sensor"));
                        }else {main(()->check(listeners(runtime)==baseline[0],"Static AOD should pause sensors"));scene.mode(1,true);main(()->{});}
                        saved.style=2;check(!scene.updateSensitivity(saved),"Style incorrectly treated as sensitivity");
                    }
                    scene.visible(false);main(()->{});SystemClock.sleep(150);
                    main(()->check(listeners(runtime)==baseline[0],"Hidden scene still listening to sensors, family="+options.family));
                    scene.visible(true);main(()->{});scene.draw();
                }finally{scene.close();main(()->{});}
                main(()->check(listeners(runtime)==baseline[0],"Closed scene still listening"));
                report.append("family ").append(family).append(" production render, sensitivity, pause/resume/release OK\n");
            }
            // Exercise every liquid/flash style through both real-wallpaper and editor hosts.
            for(int family:new int[]{1,3})for(int style=1;style<=(family==1?4:5);style++)for(int area=1;area<=4;area++){
                VivoOptions options=new VivoOptions(family);options.style=style;options.area=area;
                VivoEngineScene scene;
                try(VivoImages images=VivoImages.load(getContext(),runtime,options)){
                    scene=new VivoEngineScene(runtime,options,W,H,images.photos,images.subject,images.paint,(area&1)==0,()->{});
                }
                try{
                    scene.visible(true);main(()->{});
                    if(family==1){scene.touch(0,.5f,.6f);scene.touch(1,.5f,.6f);main(()->{});}
                    if(family==1){
                        Object identity=api.get(scene,"controller");
                        for(int relative:new int[]{-50,50,0}){
                            options.sensitivity=relative;check(scene.updateSensitivity(options),"Liquid sensitivity rebuilt scene");main(()->{});
                            check(identity==api.get(scene,"controller"),"Liquid slider changed controller");
                            Object gain=api.get(scene,"liquidSensitivity");
                            check((Float)api.get(gain,"gain")==VivoSensorGain.multiplier(relative),"Liquid style did not receive gain");
                            scene.draw();
                        }
                    }
                    aodToggle(scene,options,runtime,baseline[0]);
                    for(int i=0;i<4;i++){scene.draw();SystemClock.sleep(16);}
                    check(GLES30.glGetError()==0,"Variant GL error family="+family+" style="+style+" area="+area);
                    PhotoViewport moved=new PhotoViewport();moved.restore(.6f,.5f,1.2f);scene.crop(moved);scene.draw();
                    check(GLES30.glGetError()==0,"Cropped variant GL error family="+family+" style="+style+" area="+area);
                }finally{scene.close();main(()->{});}
                main(()->check(listeners(runtime)==baseline[0],"Variant leaked sensor"));
            }
            report.append("All 36 liquid/flash style and area combinations: render, touch, crop, AOD toggle, black-screen suspend, release OK\n");
            }
            report.append("VIVO_SCENE_OK\n");
        }catch(Throwable error){StringWriter writer=new StringWriter();error.printStackTrace(new PrintWriter(writer));report.append(writer);}
        finally{EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);if(gl!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,gl);EGL14.eglTerminate(display);}
        Bundle result=new Bundle();result.putString("stream",report.toString());finish(report.toString().contains("VIVO_SCENE_OK")?-1:0,result);
    }
}
