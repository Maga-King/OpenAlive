#![allow(non_snake_case)]
use std::{collections::HashMap,ffi::{c_void,c_char,CString},ptr};
use crate::math::{Mat,ID};
#[cfg_attr(target_os="android",link(name="GLESv3"))]
#[cfg_attr(not(target_os="android"),link(name="libGLESv2.so.2",kind="dylib",modifiers="+verbatim"))]
extern "C" {
 pub fn glUniform4f(location:i32,a:f32,b:f32,c:f32,d:f32);
 pub fn glCreateShader(kind:u32)->u32; pub fn glShaderSource(shader:u32,count:i32,strings:*const *const c_char,length:*const i32);
 pub fn glCompileShader(shader:u32);pub fn glGetShaderiv(shader:u32,pname:u32,result:*mut i32);pub fn glGetShaderInfoLog(shader:u32,max:i32,len:*mut i32,log:*mut c_char);pub fn glDeleteShader(shader:u32);
 pub fn glCreateProgram()->u32;pub fn glAttachShader(program:u32,shader:u32);pub fn glBindAttribLocation(program:u32,index:u32,name:*const c_char);pub fn glLinkProgram(program:u32);pub fn glGetProgramiv(program:u32,pname:u32,result:*mut i32);pub fn glGetProgramInfoLog(program:u32,max:i32,len:*mut i32,log:*mut c_char);pub fn glDeleteProgram(program:u32);pub fn glUseProgram(program:u32);
 pub fn glGetUniformLocation(program:u32,name:*const c_char)->i32;pub fn glUniform1f(location:i32,value:f32);pub fn glUniform1i(location:i32,value:i32);pub fn glUniform2f(location:i32,a:f32,b:f32);pub fn glUniform3f(location:i32,a:f32,b:f32,c:f32);pub fn glUniform1fv(location:i32,count:i32,v:*const f32);pub fn glUniformMatrix4fv(location:i32,count:i32,transpose:u8,v:*const f32);pub fn glUniformMatrix3fv(location:i32,count:i32,transpose:u8,v:*const f32);
 pub fn glViewport(x:i32,y:i32,w:i32,h:i32);pub fn glActiveTexture(texture:u32);pub fn glBindTexture(target:u32,texture:u32);pub fn glGenTextures(n:i32,v:*mut u32);pub fn glDeleteTextures(n:i32,v:*const u32);pub fn glTexParameteri(target:u32,pname:u32,param:i32);pub fn glTexImage2D(target:u32,level:i32,internal:i32,w:i32,h:i32,border:i32,format:u32,kind:u32,data:*const c_void);
 pub fn glGenFramebuffers(n:i32,v:*mut u32);pub fn glDeleteFramebuffers(n:i32,v:*const u32);pub fn glBindFramebuffer(target:u32,v:u32);pub fn glFramebufferTexture2D(target:u32,attachment:u32,textarget:u32,texture:u32,level:i32);pub fn glCheckFramebufferStatus(target:u32)->u32;
 pub fn glGenBuffers(n:i32,v:*mut u32);pub fn glDeleteBuffers(n:i32,v:*const u32);pub fn glBindBuffer(target:u32,v:u32);pub fn glBufferData(target:u32,size:isize,data:*const c_void,usage:u32);pub fn glGenVertexArrays(n:i32,v:*mut u32);pub fn glDeleteVertexArrays(n:i32,v:*const u32);pub fn glBindVertexArray(v:u32);
 pub fn glEnableVertexAttribArray(index:u32);pub fn glVertexAttribPointer(index:u32,size:i32,kind:u32,normalized:u8,stride:i32,pointer:*const c_void);pub fn glDrawArrays(mode:u32,first:i32,count:i32);pub fn glClearColor(r:f32,g:f32,b:f32,a:f32);pub fn glClear(mask:u32);pub fn glGetError()->u32;
}
#[cfg(target_os="android")]
#[link(name="log")]extern "C"{fn __android_log_write(priority:i32,tag:*const c_char,text:*const c_char)->i32;}
#[cfg(target_os="android")]
pub fn log(text:&str){if let Ok(t)=CString::new(text){unsafe{__android_log_write(6,b"AliveClean\0".as_ptr().cast(),t.as_ptr());}}}
#[cfg(not(target_os="android"))]
pub fn log(text:&str){eprintln!("{text}");}
unsafe fn shader(kind:u32,text:&str)->Result<u32,String>{
 let id=glCreateShader(kind);let text=CString::new(text).map_err(|e|e.to_string())?;glShaderSource(id,1,&text.as_ptr(),ptr::null());glCompileShader(id);let mut ok=0;glGetShaderiv(id,0x8B81,&mut ok);
 if ok==0{let mut b=[0u8;4096];glGetShaderInfoLog(id,4096,ptr::null_mut(),b.as_mut_ptr().cast());glDeleteShader(id);return Err(String::from_utf8_lossy(&b).trim_matches('\0').into())}Ok(id)
}
pub struct Program{pub id:u32,uniforms:HashMap<&'static str,i32>}
impl Program {
 pub unsafe fn new(v:&str,f:&str)->Result<Self,String>{
  let vs=shader(0x8B31,v)?;let fs=match shader(0x8B30,f){Ok(x)=>x,Err(e)=>{glDeleteShader(vs);return Err(e)}};
  let id=glCreateProgram();glAttachShader(id,vs);glAttachShader(id,fs);glBindAttribLocation(id,0,b"a_position\0".as_ptr().cast());glBindAttribLocation(id,1,b"a_texCoord0\0".as_ptr().cast());glLinkProgram(id);glDeleteShader(vs);glDeleteShader(fs);let mut ok=0;glGetProgramiv(id,0x8B82,&mut ok);
  if ok==0{let mut b=[0u8;4096];glGetProgramInfoLog(id,4096,ptr::null_mut(),b.as_mut_ptr().cast());glDeleteProgram(id);return Err(String::from_utf8_lossy(&b).trim_matches('\0').into())}Ok(Self{id,uniforms:HashMap::new()})
 }
 fn loc(&mut self,n:&'static str)->i32{*self.uniforms.entry(n).or_insert_with(||unsafe{glGetUniformLocation(self.id,CString::new(n).unwrap().as_ptr())})}
 pub fn bind(&mut self){unsafe{glUseProgram(self.id)} self.m4("u_projectionViewMatrix",&ID);}
 pub fn f(&mut self,n:&'static str,v:f32){unsafe{glUniform1f(self.loc(n),v)}}
 pub fn i(&mut self,n:&'static str,v:i32){unsafe{glUniform1i(self.loc(n),v)}}
 pub fn v2(&mut self,n:&'static str,x:f32,y:f32){unsafe{glUniform2f(self.loc(n),x,y)}}
 pub fn v3(&mut self,n:&'static str,v:[f32;3]){unsafe{glUniform3f(self.loc(n),v[0],v[1],v[2])}}
 pub fn v4(&mut self,n:&'static str,v:[f32;4]){unsafe{glUniform4f(self.loc(n),v[0],v[1],v[2],v[3])}}
 pub fn array(&mut self,n:&'static str,v:&[f32]){unsafe{glUniform1fv(self.loc(n),v.len() as i32,v.as_ptr())}}
 pub fn m4(&mut self,n:&'static str,v:&Mat){unsafe{glUniformMatrix4fv(self.loc(n),1,0,v.as_ptr())}}
 pub fn m3(&mut self,n:&'static str,v:&[f32;9]){unsafe{glUniformMatrix3fv(self.loc(n),1,0,v.as_ptr())}}
 pub fn texture(&mut self,n:&'static str,slot:i32,id:u32){self.tex(n,slot,id,0x0DE1)}
 pub fn tex(&mut self,n:&'static str,slot:i32,id:u32,target:u32){unsafe{glActiveTexture(0x84C0+slot as u32);glBindTexture(target,id)}self.i(n,slot)}
}
impl Drop for Program{fn drop(&mut self){unsafe{glDeleteProgram(self.id)}}}
#[derive(Default)]pub struct Target{pub fbo:u32,pub texture:u32,pub w:i32,pub h:i32}
impl Target{
 pub unsafe fn new(w:i32,h:i32)->Result<Self,String>{
  let mut t=Self{w,h,..Self::default()};glGenTextures(1,&mut t.texture);glBindTexture(0x0DE1,t.texture);
  for(p,v)in[(0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812F),(0x2803,0x812F)]{glTexParameteri(0x0DE1,p,v)}
  glTexImage2D(0x0DE1,0,0x1908,w,h,0,0x1908,0x1401,ptr::null());glGenFramebuffers(1,&mut t.fbo);glBindFramebuffer(0x8D40,t.fbo);glFramebufferTexture2D(0x8D40,0x8CE0,0x0DE1,t.texture,0);
  if glCheckFramebufferStatus(0x8D40)!=0x8CD5{return Err("incomplete framebuffer".into())}Ok(t)
 }
 pub fn bind(&self){unsafe{glBindFramebuffer(0x8D40,self.fbo);glViewport(0,0,self.w,self.h);glClearColor(0.,0.,0.,1.);glClear(0x4000)}}
}
impl Drop for Target{fn drop(&mut self){unsafe{glDeleteFramebuffers(1,&self.fbo);glDeleteTextures(1,&self.texture)}}}
pub struct Quad{vao:u32,vbo:u32}
impl Quad {
 pub unsafe fn new()->Self{let mut q=Self{vao:0,vbo:0};glGenVertexArrays(1,&mut q.vao);glBindVertexArray(q.vao);glGenBuffers(1,&mut q.vbo);glBindBuffer(0x8892,q.vbo);
 let v:[f32;20]=[-1.,-1.,0.,0.,0.,1.,-1.,0.,1.,0.,-1.,1.,0.,0.,1.,1.,1.,0.,1.,1.];glBufferData(0x8892,80,v.as_ptr().cast(),0x88E4);glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,0x1406,0,20,ptr::null());glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,0x1406,0,20,12usize as *const c_void);q}
 pub fn draw(&self){unsafe{glBindVertexArray(self.vao);glDrawArrays(5,0,4)}}
}
impl Drop for Quad{fn drop(&mut self){unsafe{glDeleteBuffers(1,&self.vbo);glDeleteVertexArrays(1,&self.vao)}}}
