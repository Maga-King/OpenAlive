pub type Mat=[f32;16];
pub const ID:Mat=[1.,0.,0.,0.,0.,1.,0.,0.,0.,0.,1.,0.,0.,0.,0.,1.];
pub fn mul(a:Mat,b:Mat)->Mat {let mut c=[0.;16];for col in 0..4{for row in 0..4{for k in 0..4{c[col*4+row]+=a[k*4+row]*b[col*4+k];}}}c}
pub fn scale(x:f32,y:f32)->Mat {let mut m=ID;m[0]=x;m[5]=y;m}
pub fn translate(x:f32,y:f32)->Mat {let mut m=ID;m[12]=x;m[13]=y;m}
pub fn rotate(deg:f32)->Mat {let(c,s)=(deg.to_radians().cos(),deg.to_radians().sin());let mut m=ID;m[0]=c;m[1]=s;m[4]=-s;m[5]=c;m}
pub fn texture_matrix(w:f32,h:f32,angle:f32,x:f32,y:f32)->Mat {
    mul(scale(1./w,1./h),mul(translate(w/2.,h/2.),mul(translate(0.,-200.),mul(rotate(-angle),mul(translate(0.,200.),mul(translate(-w*x,-h*y),scale(w/2.,h/2.)))))))
}
pub fn decorator_matrix(w:f32,h:f32,lens:f32,angle:f32,x:f32,y:f32)->Mat {
    let size=w*lens*2.;
    mul(scale(1./size,1./size),mul(translate(size/2.,size/2.),mul(rotate(angle),mul(translate(-w*x,-h*y),scale(w/2.,h/2.)))))
}
#[cfg(test)]mod tests{use super::*;#[test]fn lens_identity_maps_clip_to_uv(){let m=texture_matrix(1080.,2400.,0.,0.,0.);assert!((m[0]-0.5).abs()<1e-6);assert!((m[5]-0.5).abs()<1e-6);assert!((m[12]-0.5).abs()<1e-6);assert!((m[13]-0.5).abs()<1e-6);}}
