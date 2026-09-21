//! Independent GLES frame graph. Resources stay on the EGL thread.
use crate::{blur, gl::*, math, shaders};
use std::ffi::c_void;
mod frame_rear { include!("frame_rear.rs"); }

// SceneMotion owns interpolation and matrices. No second timeline runs on the native side.
const FRAME_FLOATS:usize=60;
struct Frame([f32;FRAME_FLOATS]);
impl Frame {
    fn ratio(&self)->[f32;3]{self.0[0..3].try_into().unwrap()}
    fn lens(&self)->[f32;10]{self.0[3..13].try_into().unwrap()}
    fn matrix(&self,offset:usize)->math::Mat{self.0[offset..offset+16].try_into().unwrap()}
}

const COPY_V: &str = "#version 300 es\nin vec3 a_position;in vec2 a_texCoord0;out vec2 uv;uniform vec2 crop;uniform float flip;void main(){gl_Position=vec4(a_position,1.);uv=(a_texCoord0-.5)*crop+.5;uv.y=mix(uv.y,1.-uv.y,flip);}";
const COPY_F: &str = "#version 300 es\nprecision highp float;in vec2 uv;uniform sampler2D photo;out vec4 color;void main(){color=texture(photo,uv);}";

unsafe fn program(base: &str) -> Result<Program, String> {
    let v = format!("shader/{base}_vertex.glsl");
    let alternate = format!("shader/{base}_vert.glsl");
    let f = format!("shader/{base}_frag.glsl");
    Program::new(shaders::source(&v).or_else(|| shaders::source(&alternate)).ok_or(&*v)?,
        shaders::source(&f).ok_or(&*f)?).map_err(|e| format!("{base}: {e}"))
}

#[derive(Clone,Copy,Default)]
struct Image { id:u32, width:i32, height:i32 }

struct Scene {
    quad:Quad, copy:Program, lock_program:Program, aod_program:Program,
    home_program:Program, blend:Program, down:Program, gaussian:Program, up:Program,
    targets:Vec<Target>, images:[Image;6], color:[f32;3], frame:Option<[f32;36]>, frame_crop:[f32;4],
    w:i32, h:i32, aod:i32, lock:i32, home:i32,
}

impl Scene {
    unsafe fn new(w:i32,h:i32,aod:i32,lock:i32,home:i32)->Result<Self,String> {
        let mut targets=Vec::new();
        for _ in 0..6 { targets.push(Target::new(w,h)?); }
        let lock_path=match lock {
            1=>"photo/lock/straightGlass/straight_glass",2=>"photo/lock/curveGlass/curve_glass",
            3=>"photo/lock/groundGlass/ground_glass",5=>"photo/lock/fillMask/fill_mask",
            _=>"photo/lock/original/original",
        };
        let aod_path=if aod==101 {"photo/aod/fullAod/full_aod"}else{"photo/aod/lensPhoto/lens_photo"};
        let blend_path=match aod {0=>"photo/blend/lens/lens_blend",1..=5=>"photo/blend/chromakey/chromakey_blend",101=>"photo/blend/fullCommon/full_common_blend",_=>"photo/blend/common/common_blend"};
        let aod_program=if (1..=5).contains(&aod) {
            // The shared quad uses bottom-up UVs; source masks use top-down RGB
            // and the existing photo targets are bottom-origin FBOs.
            let vertex="#version 300 es\nin vec3 a_position;in vec2 a_texCoord0;uniform mat4 u_modelMatrix;uniform mat4 u_contentMatrix;out vec2 v_snapshot_texCoord;out vec2 v_content_texCoord;out vec2 v_video_texCoord;void main(){gl_Position=u_modelMatrix*vec4(a_position,1.);v_snapshot_texCoord=a_texCoord0;v_video_texCoord=a_texCoord0;v_content_texCoord=(u_contentMatrix*vec4(a_texCoord0,0.,1.)).xy;v_content_texCoord.y=1.-v_content_texCoord.y;}";
            let fragment=shaders::source("shader/photo/aod/chromakey/chromakey_photo_frag.glsl").unwrap()
                .replace("#extension GL_OES_EGL_image_external_essl3 : require", "")
                .replace("samplerExternalOES", "sampler2D")
                .replace("uniform sampler2D u_photo;", "uniform sampler2D u_photo;uniform sampler2D u_frame_photo;uniform bool u_pair;uniform mat3 u_frame_crop;uniform mat3 u_rear_transform;")
                .replace("vec3 finalColor = vec3(0.0);",
                    "vec2 frameUv=(u_frame_crop*vec3(v_snapshot_texCoord-vec2(0.5,348.5/720.0),1.0)).xy;vec3 frameColor=u_pair?texture(u_frame_photo,frameUv).rgb:vec3(0.0);vec3 finalColor=vec3(0.0);")
                // The rear paper rotates and translates independently inside the
                // mask. Unproject its own aperture before applying the user's crop.
                .replace("finalColor = blendNormal(finalColor, u_first_color, videoColor.r);",
                    "vec3 rearColor=u_first_color;if(u_pair&&videoColor.r>0.0){vec2 rearUv=(u_frame_crop*u_rear_transform*vec3(v_snapshot_texCoord,1.0)).xy;rearColor=texture(u_frame_photo,rearUv).rgb;}finalColor=blendNormal(finalColor,rearColor,videoColor.r);")
                .replace("vec3 contentColor = texture(u_photo, v_content_texCoord).rgb;",
                    "vec3 contentColor=u_pair?frameColor:texture(u_photo,v_content_texCoord).rgb;");
            Program::new(vertex,&fragment)?
        }else{program(aod_path)?};
        let home_path=match home {7=>"photo/launcher/blur/blur",9=>"photo/lock/fillMask/fill_mask",_=>"photo/launcher/original/original"};
        Ok(Self {
            quad:Quad::new(),copy:Program::new(COPY_V,COPY_F)?,lock_program:program(lock_path)?,
            aod_program,home_program:program(home_path)?,blend:program(blend_path)?,
            down:program("blur/down_sample")?,gaussian:program("blur/gaussian")?,up:program("blur/up_sample")?,
            targets,images:[Image::default();6],color:[0.15,0.27,0.32],frame:None,frame_crop:[0.5,0.5,1.,0.],w,h,aod,lock,home,
        })
    }
    fn copy(&mut self,input:u32,target:usize,aspect:f32,flip:bool) {
        self.targets[target].bind();self.copy.bind();self.copy.texture("photo",0,input);
        let screen=self.w as f32/self.h as f32;
        let crop=if aspect>screen{[screen/aspect,1.]}else{[1.,aspect/screen]};
        self.copy.v2("crop",crop[0],crop[1]);self.copy.f("flip",if flip{1.}else{0.});self.quad.draw();
    }
    fn blurred(&mut self,input:u32,radius:f32,limit:f32)->u32 {
        if radius<2. { return input; }
        let(factor,weights,offsets)=blur::kernel_with_limit(radius,limit);let s=1./factor;
        let matrix=[s,0.,0.,0.,s,0.,(1.-s)/2.,(1.-s)/2.,1.];
        self.targets[4].bind();self.down.bind();self.down.f("u_scale",s);self.down.texture("u_photo",0,input);self.quad.draw();
        for (src,dst,horizontal) in [(4,5,1),(5,4,0)] {
            self.targets[dst].bind();self.gaussian.bind();self.gaussian.texture("u_blur_texture",0,self.targets[src].texture);
            self.gaussian.f("u_scale",s);self.gaussian.m3("u_texture_matrix",&matrix);
            self.gaussian.array("u_weights",&weights);self.gaussian.array("u_offsets",&offsets);self.gaussian.i("u_radius",weights.len() as i32);
            self.gaussian.i("u_horizontal",horizontal);self.gaussian.v2("u_step",1./self.w as f32,1./self.h as f32);
            self.gaussian.f("u_texture_min",(1.-s)/2.);self.gaussian.f("u_texture_max",(1.+s)/2.);self.quad.draw();
        }
        self.targets[5].bind();self.up.bind();self.up.texture("u_photo",0,self.targets[4].texture);self.up.m3("u_texture_matrix",&matrix);self.quad.draw();
        self.targets[5].texture
    }
    unsafe fn lock_pass(&mut self,frame:&Frame) {
        self.targets[1].bind();
        if self.lock==4 {glClearColor(self.color[0],self.color[1],self.color[2],1.);glClear(0x4000);return;}
        let p=&mut self.lock_program;p.bind();p.texture("u_lockTex",0,self.targets[0].texture);p.texture("u_texture",0,self.targets[0].texture);
        p.v4("u_display_uv_rect",[0.,0.,1.,1.]);p.v3("u_color",self.color);
        if self.lock==1||self.lock==2 {
            let g=&frame.0[13..19];
            p.f("u_tiling",g[0]);p.f("u_lineWidth",g[1]);p.f("u_lineOffset",g[2]);p.f("u_maskThreshold",g[3]);p.v2("u_offsetStrength",g[4],g[5]);p.f("u_lineFrequency",40.);p.f("u_lineAmplitude",0.045);
        } else if self.lock==3 {
            let gray=self.images[1];p.texture("u_grayTex",1,gray.id);p.texture("u_maskTex",2,self.images[2].id);
            p.v2("u_maskOffset",frame.0[23],frame.0[24]);p.f("u_minGray",frame.0[19]);p.f("u_maskThreshold",frame.0[20]);
            p.v2("u_dudv",1./gray.width.max(1) as f32,1./gray.height.max(1) as f32);
        }
        self.quad.draw();
    }
    fn aod_pass(&mut self,frame:&Frame,decor:u32) {
        if self.aod<0 {self.targets[2].bind();return;}
        if (1..=5).contains(&self.aod) {
            let Some(f)=self.frame else{self.targets[2].bind();return;};
            let source=self.blurred(self.targets[1].texture,f[33],12.);
            self.targets[2].bind();let p=&mut self.aod_program;p.bind();
            p.texture("u_photo",0,source);p.texture("u_launcher_effect",1,self.targets[3].texture);
            let image=self.images[5];p.i("u_pair",if self.aod==1&&image.id!=0{1}else{0});
            if image.id!=0{
                p.texture("u_frame_photo",3,image.id);
                // Square selection in original image coordinates. The front
                // mask's photo width is 320/720 of the square mesh. Keep image
                // aspect while its lower edge is covered by the paper border.
                let [cx,cy,size,angle]=self.frame_crop;
                let pixels=image.width.min(image.height) as f32*size*720./320.;
                let (s,c)=angle.to_radians().sin_cos();
                let x=pixels/image.width as f32;let y=pixels/image.height as f32;
                p.m3("u_frame_crop",&[c*x,-s*y,0.,s*x,c*y,0.,cx,cy,1.]);
                p.m3("u_rear_transform",&frame_rear::matrix(f[34]));
            }
            p.texture("u_snapshot",2,self.images[0].id);p.texture("u_green_screen",2,self.images[0].id);
            p.i("u_use_snapshot",1);p.f("u_photo_alpha",f[32]);p.v3("u_first_color",self.color);
            p.m4("u_modelMatrix",f[0..16].try_into().unwrap());
            p.m4("u_contentMatrix",f[16..32].try_into().unwrap());
            p.m4("u_video_transform",&math::ID);self.quad.draw();return;
        }
        if self.aod==101 {
            self.targets[2].bind();let p=&mut self.aod_program;p.bind();p.texture("u_lockTex",0,self.targets[1].texture);
            p.texture("u_grayTex1",1,self.images[3].id);p.f("u_alpha1",frame.0[21]);self.quad.draw();return;
        }
        let l=frame.lens();
        let source=self.blurred(self.targets[1].texture,l[0],12.);
        self.targets[2].bind();let p=&mut self.aod_program;p.bind();p.texture("u_photo",0,source);p.texture("u_lens_decorator",1,decor);
        p.f("u_lens_scale",l[1]);p.f("u_zoom",1./(l[1].min(0.5)*4.));p.f("u_aspect_ratio",self.h as f32/self.w as f32);p.f("u_aspect_ratio_reciprocal",self.w as f32/self.h as f32);
        p.f("u_distortion",l[2]/l[1]);p.f("u_decorator_alpha",l[3]);p.v3("u_translation",[l[5],l[6],0.]);p.f("u_dark_strength",0.);
        p.f("u_ripple_radius",l[8]);p.f("u_ripple_boundary",0.12);
        // Adapt official top-origin sampling to the graph's bottom-origin FBO.
        let matrix=frame.matrix(25);
        let matrix=math::mul(math::translate(0.,1.),math::mul(math::scale(1.,-1.),matrix));
        p.m4("u_texture_matrix",&matrix);p.m4("u_decorator_matrix",&frame.matrix(41));self.quad.draw();
    }
    unsafe fn home_pass(&mut self) {
        let input=if self.home==7{self.blurred(self.targets[0].texture,22.,4.4)}else{self.targets[0].texture};
        self.targets[3].bind();
        if self.home==8 {glClearColor(self.color[0],self.color[1],self.color[2],1.);glClear(0x4000);return;}
        let p=&mut self.home_program;p.bind();p.texture("u_texture",0,input);p.v3("u_color",self.color);p.v4("u_display_uv_rect",[0.,0.,1.,1.]);self.quad.draw();
    }
    unsafe fn draw(&mut self,photo:u32,decor:u32,aspect:f32,frame:&Frame) {
        let l=frame.lens();
        let ratio=frame.ratio();
        let aod_visible=ratio[0]>0.;
        let lock_visible=ratio[1]>0.||(aod_visible&&self.aod>=0);
        let home_visible=ratio[2]>0.;
        if (lock_visible&&self.lock!=4)||(home_visible&&self.home!=8){self.copy(photo,0,aspect,true);}
        if lock_visible{self.lock_pass(frame);}
        if !(1..=5).contains(&self.aod)&&aod_visible{self.aod_pass(frame,decor);}
        if home_visible||(1..=5).contains(&self.aod)&&aod_visible{
            let home=if (1..=5).contains(&self.aod)&&self.images[5].id!=0{Image::default()}else{self.images[4]};
            if home.id!=0{self.copy(home.id,0,home.width as f32/home.height as f32,true);}
            self.home_pass();
        }
        if (1..=5).contains(&self.aod)&&aod_visible{self.aod_pass(frame,decor);}
        glBindFramebuffer(0x8D40,0);glViewport(0,0,self.w,self.h);self.blend.bind();
        self.blend.texture("u_aod_effect",0,self.targets[2].texture);self.blend.texture("u_lock_effect",1,self.targets[1].texture);self.blend.texture("u_launcher_effect",2,self.targets[3].texture);
        self.blend.v3("u_ratio",ratio);self.blend.f("u_dark_strength",frame.0[57]);self.blend.i("u_support_lock_launcher_blend",frame.0[58] as i32);
        // Official blend samples top-origin textures; this frame graph uses bottom-origin FBOs.
        self.blend.v2("u_translation",l[5],-l[6]);self.blend.f("u_lens_scale",l[1]);self.blend.f("u_aspect_ratio",self.h as f32/self.w as f32);self.quad.draw();
    }
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_NativeScene_create(_: *mut c_void,_:*mut c_void,w:i32,h:i32,mode:i32,aod:i32,lock:i32,home:i32)->i64 {
    if w<=0||h<=0||!(0..=2).contains(&mode)||![-1,0,1,2,3,4,5,101].contains(&aod)||!(0..=5).contains(&lock)||!(6..=9).contains(&home) {return 0;}
    match Scene::new(w,h,aod,lock,home){Ok(s)=>Box::into_raw(Box::new(s)) as i64,Err(e)=>{log(&e);0}}
}
#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_NativeScene_texture(_: *mut c_void,_:*mut c_void,handle:i64,slot:i32,id:i32,w:i32,h:i32) {
    if handle!=0 && (0..6).contains(&slot) && id>0 && w>0 && h>0 {(*(handle as *mut Scene)).images[slot as usize]=Image{id:id as u32,width:w,height:h};}
}
#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_NativeScene_frameCrop(_: *mut c_void,_:*mut c_void,handle:i64,x:f32,y:f32,size:f32,angle:f32) {
    if handle!=0&&[x,y,size,angle].iter().all(|v|v.is_finite())&&
        (0.0..=1.0).contains(&x)&&(0.0..=1.0).contains(&y)&&(0.05..=1.0).contains(&size)&&(-45.0..=45.0).contains(&angle){
        (*(handle as *mut Scene)).frame_crop=[x,y,size,angle];
    }
}
#[no_mangle]
pub unsafe extern "C" fn alive_scene_frame(handle:i64,values:*const f32,count:usize)->u8 {
    if handle==0||values.is_null()||count!=36{return 0;}
    let mut data=[0.;36];std::ptr::copy_nonoverlapping(values,data.as_mut_ptr(),36);
    if data.iter().any(|v|!v.is_finite())||data[35]!=1.||!(0.0..=1.0).contains(&data[32]){return 0;}
    (*(handle as *mut Scene)).frame=Some(data);1
}
#[cfg(target_os="android")]
#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_NativeScene_frame(env:*mut jni_sys::JNIEnv,_:jni_sys::jclass,handle:i64,buffer:jni_sys::jobject)->u8 {
    if env.is_null()||buffer.is_null(){return 0;}let jni=&**env;
    if jni.GetDirectBufferCapacity.unwrap()(env,buffer)!=144{return 0;}
    alive_scene_frame(handle,jni.GetDirectBufferAddress.unwrap()(env,buffer).cast(),36)
}
#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_NativeScene_tint(_: *mut c_void,_:*mut c_void,handle:i64,color:i32) {
    if handle!=0 {(*(handle as *mut Scene)).color=[((color>>16)&255) as f32/255.,((color>>8)&255) as f32/255.,(color&255) as f32/255.];}
}
#[no_mangle]
pub unsafe extern "C" fn alive_scene_render(handle:i64,photo:i32,decor:i32,aspect:f32,values:*const f32,count:usize)->u8 {
    if handle==0||photo<=0||decor<=0||!aspect.is_finite()||aspect<=0.||values.is_null()||count!=FRAME_FLOATS{return 0;}
    let mut frame=Frame([0.;FRAME_FLOATS]);
    std::ptr::copy_nonoverlapping(values,frame.0.as_mut_ptr(),FRAME_FLOATS);
    if frame.0.iter().any(|v|!v.is_finite())||frame.0[4]<=0.||frame.0[59]!=1.{return 0;}
    (*(handle as *mut Scene)).draw(photo as u32,decor as u32,aspect,&frame);1
}

#[cfg(target_os="android")]
#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_NativeScene_render(env:*mut jni_sys::JNIEnv,_:jni_sys::jclass,handle:i64,photo:i32,decor:i32,aspect:f32,buffer:jni_sys::jobject)->u8 {
    if env.is_null()||buffer.is_null(){return 0;}
    let jni=&**env;
    let capacity=jni.GetDirectBufferCapacity.unwrap()(env,buffer);
    if capacity!=(FRAME_FLOATS*4) as i64{return 0;}
    let address=jni.GetDirectBufferAddress.unwrap()(env,buffer);
    alive_scene_render(handle,photo,decor,aspect,address.cast(),FRAME_FLOATS)
}
#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_NativeScene_destroy(_: *mut c_void,_:*mut c_void,handle:i64) {if handle!=0 {drop(Box::from_raw(handle as *mut Scene));}}
