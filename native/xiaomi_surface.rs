//! Snow's legacy Filament releases its swapchain asynchronously on a native
//! thread without a JNIEnv. Keep the ANativeWindow alive until that destruction
//! has finished, then release the last reference on the calling Java thread.
//! No hooks, delays, engine offsets, or modifications to the vendor libraries.
use jni_sys::{jclass,jlong,jobject,jstring,JNIEnv};
use std::{ffi::{c_char,c_int,c_void},ptr};

#[link(name="android")]
extern "C" {
    fn ANativeWindow_fromSurface(env:*mut JNIEnv,surface:jobject)->*mut c_void;
    fn ANativeWindow_release(window:*mut c_void);
}
#[link(name="dl")]
extern "C" {
    fn dlopen(path:*const c_char,flags:c_int)->*mut c_void;
    fn dlsym(lib:*mut c_void,name:*const c_char)->*mut c_void;
    fn dlclose(lib:*mut c_void)->c_int;
}
type Flush = unsafe extern "C" fn(*mut c_void);
struct Lease { library:*mut c_void,engine:*const *mut c_void,flush:Flush,windows:Vec<*mut c_void> }

unsafe fn fail(env:*mut JNIEnv,message:&'static [u8]) {
    if (**env).ExceptionCheck.unwrap()(env)!=0{return;}
    let class=(**env).FindClass.unwrap()(env,b"java/lang/IllegalStateException\0".as_ptr().cast());
    if !class.is_null(){(**env).ThrowNew.unwrap()(env,class,message.as_ptr().cast());(**env).DeleteLocalRef.unwrap()(env,class);}
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_XiaomiSurfaceLease_open(env:*mut JNIEnv,_:jclass,path:jstring)->jlong {
    if path.is_null(){fail(env,b"Missing Snow framework path\0");return 0;}
    let chars=(**env).GetStringUTFChars.unwrap()(env,path,ptr::null_mut());
    if chars.is_null(){return 0;}
    // RTLD_NOW | RTLD_NOLOAD: never create another engine in another namespace.
    let library=dlopen(chars,2|4);
    (**env).ReleaseStringUTFChars.unwrap()(env,path,chars);
    if library.is_null(){fail(env,b"Snow framework is not loaded in this namespace\0");return 0;}
    let engine=dlsym(library,b"_ZN2MI7GEngineE\0".as_ptr().cast());
    let flush=dlsym(library,b"_ZN8filament6Engine12flushAndWaitEv\0".as_ptr().cast());
    if engine.is_null()||flush.is_null(){dlclose(library);fail(env,b"Unsupported Snow surface lifecycle API\0");return 0;}
    Box::into_raw(Box::new(Lease{library,engine:engine.cast(),flush:std::mem::transmute::<*mut c_void,Flush>(flush),windows:Vec::new()})) as jlong
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_XiaomiSurfaceLease_retain(env:*mut JNIEnv,_:jclass,handle:jlong,surface:jobject) {
    if handle==0||surface.is_null(){fail(env,b"Missing Snow surface lease\0");return;}
    let lease=&mut *(handle as *mut Lease);
    let window=ANativeWindow_fromSurface(env,surface);
    if window.is_null(){fail(env,b"Cannot retain Snow native window\0");return;}
    // Keep one owned reference even if a resized Surface reuses its native window.
    if lease.windows.last()==Some(&window){ANativeWindow_release(window);}else{lease.windows.push(window);}
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_XiaomiSurfaceLease_replaced(env:*mut JNIEnv,_:jclass,handle:jlong) {
    if handle==0{fail(env,b"Missing Snow surface lease\0");return;}
    let lease=&mut *(handle as *mut Lease);
    let engine=*lease.engine;
    if engine.is_null(){fail(env,b"Snow engine did not initialize\0");return;}
    (lease.flush)(engine);
    // nSetSurface has queued destruction of all previous swapchains. The fence
    // completed, and this JNI caller now owns their final native-window refs.
    if lease.windows.len()>1{let last=lease.windows.len()-1;for window in lease.windows.drain(..last){ANativeWindow_release(window);}}
}

#[no_mangle]
pub unsafe extern "system" fn Java_org_aliveclean_XiaomiSurfaceLease_destroyed(_:*mut JNIEnv,_:jclass,handle:jlong) {
    if handle==0{return;}
    // Only called AFTER the vendor nDestroy returns (Engine::destroy joins the
    // driver). If vendor teardown throws, retain the lease until process exit.
    let lease=Box::from_raw(handle as *mut Lease);
    for window in lease.windows{ANativeWindow_release(window);}
    dlclose(lease.library);
}
