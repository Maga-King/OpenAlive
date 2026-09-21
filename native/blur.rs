//! z1.c Gaussian coefficients, paired taps and >12px downsample factor.
pub fn kernel(radius:f32)->(f32,Vec<f32>,Vec<f32>) {
    kernel_with_limit(radius,12.0)
}
pub fn kernel_with_limit(radius:f32,limit:f32)->(f32,Vec<f32>,Vec<f32>) {
    if !radius.is_finite()||radius<2.0{return (1.,vec![1.],vec![0.])}
    let limit=if limit.is_finite(){limit.clamp(2.,12.)}else{12.};
    let sigma=radius.min(limit) as f64;
    let mut extent=(-2.0*sigma*sigma*(0.00390625*(sigma*sigma*std::f64::consts::TAU).sqrt()).ln()).sqrt().floor() as usize;
    extent+=extent%2;
    let mut weights:Vec<f32>=(0..=extent).map(|i|((-((i*i) as f64)/(2.*sigma*sigma)).exp()/(sigma*sigma*std::f64::consts::TAU).sqrt()) as f32).collect();
    let sum=weights[0]+2.*weights.iter().skip(1).sum::<f32>();for w in &mut weights{*w/=sum;}
    let mut paired=vec![weights[0]];let mut offsets=vec![0.];
    for i in (1..extent).step_by(2){let s=weights[i]+weights[i+1];paired.push(s);offsets.push((i as f32*weights[i]+(i+1) as f32*weights[i+1])/s);}
    ((radius/limit).max(1.),paired,offsets)
}
#[cfg(test)]mod tests {
 use super::*;
 #[test]fn normalized(){for r in [0.,1.,2.,9.,12.,16.,22.]{let(s,w,o)=kernel(r);assert!((w[0]+2.*w.iter().skip(1).sum::<f32>()-1.).abs()<0.00001);assert_eq!(w.len(),o.len());assert!(s>=1.);}}
}
