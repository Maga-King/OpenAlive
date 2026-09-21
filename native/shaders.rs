// Generated from the user-supplied APK. See THIRD_PARTY.md.
pub fn source(path: &str) -> Option<&'static str> { match path {
"shader/base/blend_utils.glsl" => Some(r####"vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}"####),
"shader/base/dark_utlis.glsl" => Some(r####"precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}"####),
"shader/base/dithering_utils.glsl" => Some(r####"highp float basicDithering (in highp vec2 st, highp float intensity) {
    return mix(-intensity/ 255., intensity/255., fract(sin(dot(st.xy, vec2(12.9898, 78.233)))* 43758.5453123));
}"####),
"shader/base/noise_2d.glsl" => Some(r####"//
// Description : Array and textureless GLSL 2D simplex noise function.
//      Author : Ian McEwan, Ashima Arts.
//  Maintainer : stegu
//     Lastmod : 20110822 (ijm)
//     License : Copyright (C) 2011 Ashima Arts. All rights reserved.
//               Distributed under the MIT License. See LICENSE file.
//               https://github.com/ashima/webgl-noise
//               https://github.com/stegu/webgl-noise
//

vec3 mod289(vec3 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec2 mod289(vec2 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec3 permute(vec3 x) {
    return mod289(((x*34.0)+10.0)*x);
}

float snoise(vec2 v)
{
    const vec4 C = vec4(0.211324865405187,  // (3.0-sqrt(3.0))/6.0
    0.366025403784439,  // 0.5*(sqrt(3.0)-1.0)
    -0.577350269189626,  // -1.0 + 2.0 * C.x
    0.024390243902439); // 1.0 / 41.0
    // First corner
    vec2 i  = floor(v + dot(v, C.yy) );
    vec2 x0 = v -   i + dot(i, C.xx);

    // Other corners
    vec2 i1;
    //i1.x = step( x0.y, x0.x ); // x0.x > x0.y ? 1.0 : 0.0
    //i1.y = 1.0 - i1.x;
    i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    // x0 = x0 - 0.0 + 0.0 * C.xx ;
    // x1 = x0 - i1 + 1.0 * C.xx ;
    // x2 = x0 - 1.0 + 2.0 * C.xx ;
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;

    // Permutations
    i = mod289(i); // Avoid truncation effects in permutation
    vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 ))
    + i.x + vec3(0.0, i1.x, 1.0 ));

    vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
    m = m*m ;
    m = m*m ;

    // Gradients: 41 points uniformly over a line, mapped onto a diamond.
    // The ring size 17*17 = 289 is close to a multiple of 41 (41*7 = 287)

    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;

    // Normalise gradients implicitly by scaling m
    // Approximation of: m *= inversesqrt( a0*a0 + h*h );
    m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );

    // Compute final noise value at P
    vec3 g;
    g.x  = a0.x  * x0.x  + h.x  * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}"####),
"shader/base/noise_3d.glsl" => Some(r####"//
// Description : Array and textureless GLSL 2D/3D/4D simplex
//               noise functions.
//      Author : Ian McEwan, Ashima Arts.
//  Maintainer : stegu
//     Lastmod : 20201014 (stegu)
//     License : Copyright (C) 2011 Ashima Arts. All rights reserved.
//               Distributed under the MIT License. See LICENSE file.
//               https://github.com/ashima/webgl-noise
//               https://github.com/stegu/webgl-noise
//

vec3 mod289(vec3 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 mod289(vec4 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 permute(vec4 x) {
    return mod289(((x*34.0)+1.0)*x);
}

vec4 taylorInvSqrt(vec4 r)
{
    return 1.79284291400159 - 0.85373472095314 * r;
}

float snoise(vec3 v)
{
    const vec2  C = vec2(1.0/6.0, 1.0/3.0) ;
    const vec4  D = vec4(0.0, 0.5, 1.0, 2.0);

    // First corner
    vec3 i  = floor(v + dot(v, C.yyy) );
    vec3 x0 =   v - i + dot(i, C.xxx) ;

    // Other corners
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min( g.xyz, l.zxy );
    vec3 i2 = max( g.xyz, l.zxy );

    //   x0 = x0 - 0.0 + 0.0 * C.xxx;
    //   x1 = x0 - i1  + 1.0 * C.xxx;
    //   x2 = x0 - i2  + 2.0 * C.xxx;
    //   x3 = x0 - 1.0 + 3.0 * C.xxx;
    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy; // 2.0*C.x = 1/3 = C.y
    vec3 x3 = x0 - D.yyy;      // -1.0+3.0*C.x = -0.5 = -D.y

    // Permutations
    i = mod289(i);
    vec4 p = permute( permute( permute(
    i.z + vec4(0.0, i1.z, i2.z, 1.0 ))
    + i.y + vec4(0.0, i1.y, i2.y, 1.0 ))
    + i.x + vec4(0.0, i1.x, i2.x, 1.0 ));

    // Gradients: 7x7 points over a square, mapped onto an octahedron.
    // The ring size 17*17 = 289 is close to a multiple of 49 (49*6 = 294)
    float n_ = 0.142857142857; // 1.0/7.0
    vec3  ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);  //  mod(p,7*7)

    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_ );    // mod(j,N)

    vec4 x = x_ *ns.x + ns.yyyy;
    vec4 y = y_ *ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4( x.xy, y.xy );
    vec4 b1 = vec4( x.zw, y.zw );

    //vec4 s0 = vec4(lessThan(b0,0.0))*2.0 - 1.0;
    //vec4 s1 = vec4(lessThan(b1,0.0))*2.0 - 1.0;
    vec4 s0 = floor(b0)*2.0 + 1.0;
    vec4 s1 = floor(b1)*2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy ;
    vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww ;

    vec3 p0 = vec3(a0.xy,h.x);
    vec3 p1 = vec3(a0.zw,h.y);
    vec3 p2 = vec3(a1.xy,h.z);
    vec3 p3 = vec3(a1.zw,h.w);

    //Normalise gradients
    vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2, p2), dot(p3,p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    // Mix final noise value
    vec4 m = max(0.5 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
    m = m * m;
    return 105.0 * dot( m*m, vec4( dot(p0,x0), dot(p1,x1),
    dot(p2,x2), dot(p3,x3) ) );
}"####),
"shader/base/noise_3d_classic.glsl" => Some(r####"//
// GLSL textureless classic 3D noise "cnoise",
// with an RSL-style periodic variant "pnoise".
// Author:  Stefan Gustavson (stefan.gustavson@liu.se)
// Version: 2011-10-11
//
// Many thanks to Ian McEwan of Ashima Arts for the
// ideas for permutation and gradient selection.
//
// Copyright (c) 2011 Stefan Gustavson. All rights reserved.
// Distributed under the MIT license. See LICENSE file.
// https://github.com/stegu/webgl-noise
//

vec3 mod289(vec3 x)
{
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 mod289(vec4 x)
{
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 permute(vec4 x)
{
    return mod289(((x*34.0)+1.0)*x);
}

vec4 taylorInvSqrt(vec4 r)
{
    return 1.79284291400159 - 0.85373472095314 * r;
}

vec3 fade(vec3 t) {
    return t*t*t*(t*(t*6.0-15.0)+10.0);
}

// Classic Perlin noise
float cnoise(vec3 P)
{
    vec3 Pi0 = floor(P); // Integer part for indexing
    vec3 Pi1 = Pi0 + vec3(1.0); // Integer part + 1
    Pi0 = mod289(Pi0);
    Pi1 = mod289(Pi1);
    vec3 Pf0 = fract(P); // Fractional part for interpolation
    vec3 Pf1 = Pf0 - vec3(1.0); // Fractional part - 1.0
    vec4 ix = vec4(Pi0.x, Pi1.x, Pi0.x, Pi1.x);
    vec4 iy = vec4(Pi0.yy, Pi1.yy);
    vec4 iz0 = Pi0.zzzz;
    vec4 iz1 = Pi1.zzzz;

    vec4 ixy = permute(permute(ix) + iy);
    vec4 ixy0 = permute(ixy + iz0);
    vec4 ixy1 = permute(ixy + iz1);

    vec4 gx0 = ixy0 * (1.0 / 7.0);
    vec4 gy0 = fract(floor(gx0) * (1.0 / 7.0)) - 0.5;
    gx0 = fract(gx0);
    vec4 gz0 = vec4(0.5) - abs(gx0) - abs(gy0);
    vec4 sz0 = step(gz0, vec4(0.0));
    gx0 -= sz0 * (step(0.0, gx0) - 0.5);
    gy0 -= sz0 * (step(0.0, gy0) - 0.5);

    vec4 gx1 = ixy1 * (1.0 / 7.0);
    vec4 gy1 = fract(floor(gx1) * (1.0 / 7.0)) - 0.5;
    gx1 = fract(gx1);
    vec4 gz1 = vec4(0.5) - abs(gx1) - abs(gy1);
    vec4 sz1 = step(gz1, vec4(0.0));
    gx1 -= sz1 * (step(0.0, gx1) - 0.5);
    gy1 -= sz1 * (step(0.0, gy1) - 0.5);

    vec3 g000 = vec3(gx0.x,gy0.x,gz0.x);
    vec3 g100 = vec3(gx0.y,gy0.y,gz0.y);
    vec3 g010 = vec3(gx0.z,gy0.z,gz0.z);
    vec3 g110 = vec3(gx0.w,gy0.w,gz0.w);
    vec3 g001 = vec3(gx1.x,gy1.x,gz1.x);
    vec3 g101 = vec3(gx1.y,gy1.y,gz1.y);
    vec3 g011 = vec3(gx1.z,gy1.z,gz1.z);
    vec3 g111 = vec3(gx1.w,gy1.w,gz1.w);

    vec4 norm0 = taylorInvSqrt(vec4(dot(g000, g000), dot(g010, g010), dot(g100, g100), dot(g110, g110)));
    g000 *= norm0.x;
    g010 *= norm0.y;
    g100 *= norm0.z;
    g110 *= norm0.w;
    vec4 norm1 = taylorInvSqrt(vec4(dot(g001, g001), dot(g011, g011), dot(g101, g101), dot(g111, g111)));
    g001 *= norm1.x;
    g011 *= norm1.y;
    g101 *= norm1.z;
    g111 *= norm1.w;

    float n000 = dot(g000, Pf0);
    float n100 = dot(g100, vec3(Pf1.x, Pf0.yz));
    float n010 = dot(g010, vec3(Pf0.x, Pf1.y, Pf0.z));
    float n110 = dot(g110, vec3(Pf1.xy, Pf0.z));
    float n001 = dot(g001, vec3(Pf0.xy, Pf1.z));
    float n101 = dot(g101, vec3(Pf1.x, Pf0.y, Pf1.z));
    float n011 = dot(g011, vec3(Pf0.x, Pf1.yz));
    float n111 = dot(g111, Pf1);

    vec3 fade_xyz = fade(Pf0);
    vec4 n_z = mix(vec4(n000, n100, n010, n110), vec4(n001, n101, n011, n111), fade_xyz.z);
    vec2 n_yz = mix(n_z.xy, n_z.zw, fade_xyz.y);
    float n_xyz = mix(n_yz.x, n_yz.y, fade_xyz.x);
    return 2.2 * n_xyz;
}

// Classic Perlin noise, periodic variant
float pnoise(vec3 P, vec3 rep)
{
    vec3 Pi0 = mod(floor(P), rep); // Integer part, modulo period
    vec3 Pi1 = mod(Pi0 + vec3(1.0), rep); // Integer part + 1, mod period
    Pi0 = mod289(Pi0);
    Pi1 = mod289(Pi1);
    vec3 Pf0 = fract(P); // Fractional part for interpolation
    vec3 Pf1 = Pf0 - vec3(1.0); // Fractional part - 1.0
    vec4 ix = vec4(Pi0.x, Pi1.x, Pi0.x, Pi1.x);
    vec4 iy = vec4(Pi0.yy, Pi1.yy);
    vec4 iz0 = Pi0.zzzz;
    vec4 iz1 = Pi1.zzzz;

    vec4 ixy = permute(permute(ix) + iy);
    vec4 ixy0 = permute(ixy + iz0);
    vec4 ixy1 = permute(ixy + iz1);

    vec4 gx0 = ixy0 * (1.0 / 7.0);
    vec4 gy0 = fract(floor(gx0) * (1.0 / 7.0)) - 0.5;
    gx0 = fract(gx0);
    vec4 gz0 = vec4(0.5) - abs(gx0) - abs(gy0);
    vec4 sz0 = step(gz0, vec4(0.0));
    gx0 -= sz0 * (step(0.0, gx0) - 0.5);
    gy0 -= sz0 * (step(0.0, gy0) - 0.5);

    vec4 gx1 = ixy1 * (1.0 / 7.0);
    vec4 gy1 = fract(floor(gx1) * (1.0 / 7.0)) - 0.5;
    gx1 = fract(gx1);
    vec4 gz1 = vec4(0.5) - abs(gx1) - abs(gy1);
    vec4 sz1 = step(gz1, vec4(0.0));
    gx1 -= sz1 * (step(0.0, gx1) - 0.5);
    gy1 -= sz1 * (step(0.0, gy1) - 0.5);

    vec3 g000 = vec3(gx0.x,gy0.x,gz0.x);
    vec3 g100 = vec3(gx0.y,gy0.y,gz0.y);
    vec3 g010 = vec3(gx0.z,gy0.z,gz0.z);
    vec3 g110 = vec3(gx0.w,gy0.w,gz0.w);
    vec3 g001 = vec3(gx1.x,gy1.x,gz1.x);
    vec3 g101 = vec3(gx1.y,gy1.y,gz1.y);
    vec3 g011 = vec3(gx1.z,gy1.z,gz1.z);
    vec3 g111 = vec3(gx1.w,gy1.w,gz1.w);

    vec4 norm0 = taylorInvSqrt(vec4(dot(g000, g000), dot(g010, g010), dot(g100, g100), dot(g110, g110)));
    g000 *= norm0.x;
    g010 *= norm0.y;
    g100 *= norm0.z;
    g110 *= norm0.w;
    vec4 norm1 = taylorInvSqrt(vec4(dot(g001, g001), dot(g011, g011), dot(g101, g101), dot(g111, g111)));
    g001 *= norm1.x;
    g011 *= norm1.y;
    g101 *= norm1.z;
    g111 *= norm1.w;

    float n000 = dot(g000, Pf0);
    float n100 = dot(g100, vec3(Pf1.x, Pf0.yz));
    float n010 = dot(g010, vec3(Pf0.x, Pf1.y, Pf0.z));
    float n110 = dot(g110, vec3(Pf1.xy, Pf0.z));
    float n001 = dot(g001, vec3(Pf0.xy, Pf1.z));
    float n101 = dot(g101, vec3(Pf1.x, Pf0.y, Pf1.z));
    float n011 = dot(g011, vec3(Pf0.x, Pf1.yz));
    float n111 = dot(g111, Pf1);

    vec3 fade_xyz = fade(Pf0);
    vec4 n_z = mix(vec4(n000, n100, n010, n110), vec4(n001, n101, n011, n111), fade_xyz.z);
    vec2 n_yz = mix(n_z.xy, n_z.zw, fade_xyz.y);
    float n_xyz = mix(n_yz.x, n_yz.y, fade_xyz.x);
    return 2.2 * n_xyz;
}"####),
"shader/base/noise_3d_grad.glsl" => Some(r####"//
// Description : Array and textureless GLSL 2D/3D/4D simplex
//               noise functions.
//      Author : Ian McEwan, Ashima Arts.
//  Maintainer : stegu
//     Lastmod : 20201014 (stegu)
//     License : Copyright (C) 2011 Ashima Arts. All rights reserved.
//               Distributed under the MIT License. See LICENSE file.
//               https://github.com/ashima/webgl-noise
//               https://github.com/stegu/webgl-noise
//

vec3 mod289(vec3 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 mod289(vec4 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 permute(vec4 x) {
    return mod289(((x*34.0)+1.0)*x);
}

vec4 taylorInvSqrt(vec4 r)
{
    return 1.79284291400159 - 0.85373472095314 * r;
}

float snoise(vec3 v, out vec3 gradient)
{
    const vec2  C = vec2(1.0/6.0, 1.0/3.0) ;
    const vec4  D = vec4(0.0, 0.5, 1.0, 2.0);

    // First corner
    vec3 i  = floor(v + dot(v, C.yyy) );
    vec3 x0 =   v - i + dot(i, C.xxx) ;

    // Other corners
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min( g.xyz, l.zxy );
    vec3 i2 = max( g.xyz, l.zxy );

    //   x0 = x0 - 0.0 + 0.0 * C.xxx;
    //   x1 = x0 - i1  + 1.0 * C.xxx;
    //   x2 = x0 - i2  + 2.0 * C.xxx;
    //   x3 = x0 - 1.0 + 3.0 * C.xxx;
    vec3 x1 = x0 - i1 + C.xxx;
    vec3 x2 = x0 - i2 + C.yyy; // 2.0*C.x = 1/3 = C.y
    vec3 x3 = x0 - D.yyy;      // -1.0+3.0*C.x = -0.5 = -D.y

    // Permutations
    i = mod289(i);
    vec4 p = permute( permute( permute(
    i.z + vec4(0.0, i1.z, i2.z, 1.0 ))
    + i.y + vec4(0.0, i1.y, i2.y, 1.0 ))
    + i.x + vec4(0.0, i1.x, i2.x, 1.0 ));

    // Gradients: 7x7 points over a square, mapped onto an octahedron.
    // The ring size 17*17 = 289 is close to a multiple of 49 (49*6 = 294)
    float n_ = 0.142857142857; // 1.0/7.0
    vec3  ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z * ns.z);  //  mod(p,7*7)

    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_ );    // mod(j,N)

    vec4 x = x_ *ns.x + ns.yyyy;
    vec4 y = y_ *ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4( x.xy, y.xy );
    vec4 b1 = vec4( x.zw, y.zw );

    //vec4 s0 = vec4(lessThan(b0,0.0))*2.0 - 1.0;
    //vec4 s1 = vec4(lessThan(b1,0.0))*2.0 - 1.0;
    vec4 s0 = floor(b0)*2.0 + 1.0;
    vec4 s1 = floor(b1)*2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy ;
    vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww ;

    vec3 p0 = vec3(a0.xy,h.x);
    vec3 p1 = vec3(a0.zw,h.y);
    vec3 p2 = vec3(a1.xy,h.z);
    vec3 p3 = vec3(a1.zw,h.w);

    //Normalise gradients
    vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2, p2), dot(p3,p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    // Mix final noise value
    vec4 m = max(0.5 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
    vec4 m2 = m * m;
    vec4 m4 = m2 * m2;
    vec4 pdotx = vec4(dot(p0,x0), dot(p1,x1), dot(p2,x2), dot(p3,x3));

    // Determine noise gradient
    vec4 temp = m2 * m * pdotx;
    gradient = -8.0 * (temp.x * x0 + temp.y * x1 + temp.z * x2 + temp.w * x3);
    gradient += m4.x * p0 + m4.y * p1 + m4.z * p2 + m4.w * p3;
    gradient *= 105.0;

    return 105.0 * dot(m4, pdotx);
}"####),
"shader/blur/down_sample_frag.glsl" => Some(r####"#version 300 es
precision highp float;

in vec2 v_texCoord;
uniform sampler2D u_photo;
out vec4 fragColor;

void main () {
    fragColor = vec4(texture(u_photo, v_texCoord).rgb, 1.0);
}"####),
"shader/blur/down_sample_vertex.glsl" => Some(r####"#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform float u_scale;

out vec2 v_texCoord;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position * u_scale, 1.0);
    v_texCoord = a_texCoord0;
}"####),
"shader/blur/gaussian_frag.glsl" => Some(r####"#version 300 es
precision highp float;

in vec2 v_texCoord;

uniform sampler2D u_blur_texture;
uniform float u_weights[50];
uniform float u_offsets[50];
uniform int u_radius;
uniform bool u_horizontal;
uniform vec2 u_step;
uniform float u_texture_min;
uniform float u_texture_max;

out vec4 fragColor;

vec2 getTextureCoord(float x, float y) {
    return clamp(vec2(x, y), u_texture_min, u_texture_max);
}

vec4 gassian() {
    vec3 sum;
    for (int i = 0; i < u_radius; i++) {
        if (i == 0) {
            sum = texture(u_blur_texture, v_texCoord).rgb * u_weights[0];
        } else {
            if (u_horizontal) {
                sum += texture(u_blur_texture, getTextureCoord(v_texCoord.x + u_offsets[i] * u_step.x, v_texCoord.y)).rgb * u_weights[i];
                sum += texture(u_blur_texture, getTextureCoord(v_texCoord.x - u_offsets[i] * u_step.x, v_texCoord.y)).rgb * u_weights[i];
            } else {
                sum += texture(u_blur_texture, getTextureCoord(v_texCoord.x, v_texCoord.y + u_offsets[i] * u_step.y)).rgb * u_weights[i];
                sum += texture(u_blur_texture, getTextureCoord(v_texCoord.x, v_texCoord.y - u_offsets[i] * u_step.y)).rgb * u_weights[i];
            }
        }
    }
    return vec4(sum, 1.0);
}

void main () {
    fragColor = gassian();
}"####),
"shader/blur/gaussian_vertex.glsl" => Some(r####"#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform float u_scale;
uniform mat3 u_texture_matrix;

out vec2 v_texCoord;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position * u_scale, 1);
    v_texCoord = (u_texture_matrix * vec3(a_texCoord0, 1.0)).xy;
}"####),
"shader/blur/up_sample_frag.glsl" => Some(r####"#version 300 es
precision highp float;

in vec2 v_texCoord;
uniform sampler2D u_photo;
out vec4 fragColor;

void main () {
    fragColor = vec4(texture(u_photo, vec2(v_texCoord)).xyz, 1.0);
}"####),
"shader/blur/up_sample_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform mat3 u_texture_matrix;

out vec2 v_texCoord;
out vec2 v_uv;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position, 1);
    v_texCoord = (u_texture_matrix * vec3(a_texCoord0, 1.0)).xy;
}"####),
"shader/bubble/bubble_bg_frag.glsl" => Some(r####"#version 300 es
precision lowp float;

highp float basicDithering (in highp vec2 st, highp float intensity) {
    return mix(-intensity/ 255., intensity/255., fract(sin(dot(st.xy, vec2(12.9898, 78.233)))* 43758.5453123));
}

in vec3 v_color;
in vec2 v_uv;

out vec4 fragColor;

void main () {
    fragColor = vec4(v_color, 1.0);
    fragColor.rgb += basicDithering(v_uv, 2.);
}"####),
"shader/bubble/bubble_bg_vertex.glsl" => Some(r####"#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

uniform vec3 u_startColor;
uniform vec3 u_endColor;

out vec3 v_color;
out vec2 v_uv;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_uv = a_texCoord0;

    float f = a_texCoord0.x * (1.0 - a_texCoord0.y);
    f = (a_texCoord0.x + a_texCoord0.y) * 0.5;
    v_color = mix(u_startColor, u_endColor, f);
}"####),
"shader/bubble/bubble_frag.glsl" => Some(r####"#version 300 es
precision lowp float;

uniform vec4 u_materialColor;
uniform vec4 u_lightColor1;
uniform vec4 u_lightColor2;
uniform vec4 u_lightColor3;
//uniform vec4 u_lightColor4;
//uniform vec4 u_lightColor5;
uniform float u_alpha;

in float v_light1;
in float v_light2;
in float v_light3;
//in float v_light4;
//in float v_light5;
in float v_alpha;

out vec4 fragColor;
void main () {
    vec4 color = mix(u_materialColor, u_lightColor1, v_light1);
    color = mix(color, u_lightColor2, v_light2);
    color = mix(color, u_lightColor3, v_light3);
//    color = mix(color, u_lightColor4, v_light4);
//    color = mix(color, u_lightColor5, v_light5);
    fragColor = vec4(color.rgb, color.a * v_alpha * u_alpha);
}"####),
"shader/bubble/bubble_vertex.glsl" => Some(r####"#version 300 es
in vec3 a_position;
in vec3 a_normal;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform vec4 u_location;

uniform vec4 u_lightLocation1;
uniform vec4 u_lightLocation2;
uniform vec4 u_lightLocation3;
//uniform vec4 u_lightLocation4;
//uniform vec4 u_lightLocation5;

uniform float u_lightRadius1;
uniform float u_lightRadius2;
uniform float u_lightRadius3;
//uniform float u_lightRadius4;
//uniform float u_lightRadius5;

out vec2 v_uv;
out float v_alpha;
out float v_light1;
out float v_light2;
out float v_light3;
//out float v_light4;
//out float v_light5;

float calcAttenuation(vec3 pos, vec4 light, float R) {
    float distance = distance(light.xyz, pos);
    float w = 1.0 - clamp(distance / R, 0.0, 1.0);
    return w * light.w;
}

void main() {
    vec3 center = vec3(u_location.xy - vec2(512.0, 1080.0), u_location.z);
    vec3 wordPosition = a_position * u_location.w + center;
    gl_Position = u_projectionViewMatrix * vec4(wordPosition, 1.0);
    v_uv = a_texCoord0;

    vec4 light1 = vec4(u_lightLocation1.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation1.w);
    vec4 light2 = vec4(u_lightLocation2.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation2.w);
    vec4 light3 = vec4(u_lightLocation3.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation3.w);
//    vec4 light4 = vec4(u_lightLocation4.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation4.w);
//    vec4 light5 = vec4(u_lightLocation5.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation5.w);
    v_light1 = calcAttenuation(wordPosition, light1, u_lightRadius1) * 2.0;
    v_light2 = calcAttenuation(wordPosition, light2, u_lightRadius2) * 2.0;
    v_light3 = calcAttenuation(wordPosition, light3, u_lightRadius3) * 2.0;
//    v_light4 = calcAttenuation(wordPosition, light4, u_lightRadius4) * 2.0;
//    v_light5 = calcAttenuation(wordPosition, light5, u_lightRadius5) * 2.0;


    v_alpha = dot(normalize(a_normal), normalize(light1.xyz - wordPosition)) + 1.0f;
}"####),
"shader/cosmic/cosmic_frag.glsl" => Some(r####"#version 320 es
precision highp float;

float hash(float n) {
    return fract(sin(n) * 753.5453123);
}

float noise(in vec3 floor, in vec3 fract) {
    vec3 p = floor;
    vec3 f = fract;
    f = f*f*(3.0-2.0*f);

    float n = p.x + p.y * 157.0 + 113.0 * p.z;
    return mix(mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),
    mix(hash(n + 157.0), hash(n + 158.0), f.x), f.y),
    mix(mix(hash(n + 113.0), hash(n + 114.0), f.x),
    mix(hash(n + 270.0), hash(n + 271.0), f.x), f.y), f.z);
}

float precision_noise(in vec3 base, in vec3 time) {
    vec3 _fract = fract(base) + fract(time);
    vec3 _floor = floor(base) + floor(time) + floor(_fract);
    _fract = fract(_fract);
    return noise(_floor, _fract);
}

vec3 noisePosition(in vec3 position, float time, float amplitude) {
    return position * (1.0 + precision_noise(position, vec3(time)) * 0.35 * amplitude);
}

vec3 orthogonal(in vec3 v) {
    return normalize(abs(v.x) > abs(v.z) ? vec3(-v.y, v.x, 0.0) : vec3(0.0, -v.z, v.y));
}

vec3 calcNormal(in vec3 position, float time, float amplitude) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition(position, time, amplitude);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition(nearby1, time, amplitude);
    vec3 distorted2 = noisePosition(nearby2, time, amplitude);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}

vec3 noisePosition1(in vec3 position, float amplitude, float noise) {
    return position * (1.0 + noise * 0.35 * amplitude);
}

vec3 calcNormal1(in vec3 position, float amplitude, float noise) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition1(position, amplitude, noise);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition1(nearby1, amplitude, noise);
    vec3 distorted2 = noisePosition1(nearby2, amplitude, noise);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}
vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}

out vec4 fragColor;
in vec3 v_origin_position;
in vec3 v_position;
in vec3 v_normal;

uniform vec3 u_eye_position;
uniform float u_time;
uniform vec4 u_base_color;
uniform vec4 u_stripe_color_1;
uniform vec4 u_stripe_color_2;
uniform vec4 u_stripe_color_3;
uniform float u_stripe_divergent;
uniform float u_stripe_thickness;
uniform float u_highlight_thickness;
uniform float u_highlight_strength;
uniform vec4 u_highlight_color;
uniform float u_alpha;

vec3 mapColor(vec3 mapR, vec3 mapG, vec3 mapB, vec3 inputColor) {
    vec3 baseColor = mapR;
    vec3 blendColor = blendNormal(baseColor, mapG, clamp(inputColor.g, 0.0, 1.0));
    return blendNormal(blendColor, mapB, clamp(inputColor.b, 0.0, 1.0));
}

void main () {

    vec3 rd = normalize(u_eye_position - v_position);

    vec3 position_distorted = v_origin_position +  precision_noise(v_origin_position * 1.5, vec3(0.0, 0.0, sin(u_time * 0.15))) * u_stripe_divergent;
    vec3 normal_distorted = calcNormal(position_distorted, u_time * 0.45, 1.0 + (19.0 * u_stripe_divergent));
    float ndotl_distorted = abs(dot(-rd, normal_distorted));
    // 计算条纹颜色
    vec3 stripe_color = mapColor(u_stripe_color_1.rgb, u_stripe_color_2.rgb, u_stripe_color_3.rgb, v_normal);
    // 计算条纹纹理
    float rim_distorted = pow(1.0 - ndotl_distorted, u_stripe_thickness/*条纹粗细*/);
    // 将基础色和条纹进行混合
    vec3 finalColor = mix(u_base_color.rgb, stripe_color.rgb, rim_distorted);

    float ndotl = abs(dot(-rd, v_normal));
    // 计算高光轮廓
    float rim = pow(1.0 - ndotl, u_highlight_thickness/*条纹粗细*/);
    // 叠加高光
    finalColor = blendNormal(finalColor, u_highlight_color.rgb, rim * u_highlight_strength);

    fragColor = vec4(finalColor, u_alpha);
}"####),
"shader/cosmic/cosmic_utils.glsl" => Some(r####"float hash(float n) {
    return fract(sin(n) * 753.5453123);
}

float noise(in vec3 floor, in vec3 fract) {
    vec3 p = floor;
    vec3 f = fract;
    f = f*f*(3.0-2.0*f);

    float n = p.x + p.y * 157.0 + 113.0 * p.z;
    return mix(mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),
    mix(hash(n + 157.0), hash(n + 158.0), f.x), f.y),
    mix(mix(hash(n + 113.0), hash(n + 114.0), f.x),
    mix(hash(n + 270.0), hash(n + 271.0), f.x), f.y), f.z);
}

float precision_noise(in vec3 base, in vec3 time) {
    vec3 _fract = fract(base) + fract(time);
    vec3 _floor = floor(base) + floor(time) + floor(_fract);
    _fract = fract(_fract);
    return noise(_floor, _fract);
}

vec3 noisePosition(in vec3 position, float time, float amplitude) {
    return position * (1.0 + precision_noise(position, vec3(time)) * 0.35 * amplitude);
}

vec3 orthogonal(in vec3 v) {
    return normalize(abs(v.x) > abs(v.z) ? vec3(-v.y, v.x, 0.0) : vec3(0.0, -v.z, v.y));
}

vec3 calcNormal(in vec3 position, float time, float amplitude) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition(position, time, amplitude);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition(nearby1, time, amplitude);
    vec3 distorted2 = noisePosition(nearby2, time, amplitude);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}

vec3 noisePosition1(in vec3 position, float amplitude, float noise) {
    return position * (1.0 + noise * 0.35 * amplitude);
}

vec3 calcNormal1(in vec3 position, float amplitude, float noise) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition1(position, amplitude, noise);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition1(nearby1, amplitude, noise);
    vec3 distorted2 = noisePosition1(nearby2, amplitude, noise);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}"####),
"shader/cosmic/cosmic_vertex.glsl" => Some(r####"#version 320 es
precision highp float;

float hash(float n) {
    return fract(sin(n) * 753.5453123);
}

float noise(in vec3 floor, in vec3 fract) {
    vec3 p = floor;
    vec3 f = fract;
    f = f*f*(3.0-2.0*f);

    float n = p.x + p.y * 157.0 + 113.0 * p.z;
    return mix(mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),
    mix(hash(n + 157.0), hash(n + 158.0), f.x), f.y),
    mix(mix(hash(n + 113.0), hash(n + 114.0), f.x),
    mix(hash(n + 270.0), hash(n + 271.0), f.x), f.y), f.z);
}

float precision_noise(in vec3 base, in vec3 time) {
    vec3 _fract = fract(base) + fract(time);
    vec3 _floor = floor(base) + floor(time) + floor(_fract);
    _fract = fract(_fract);
    return noise(_floor, _fract);
}

vec3 noisePosition(in vec3 position, float time, float amplitude) {
    return position * (1.0 + precision_noise(position, vec3(time)) * 0.35 * amplitude);
}

vec3 orthogonal(in vec3 v) {
    return normalize(abs(v.x) > abs(v.z) ? vec3(-v.y, v.x, 0.0) : vec3(0.0, -v.z, v.y));
}

vec3 calcNormal(in vec3 position, float time, float amplitude) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition(position, time, amplitude);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition(nearby1, time, amplitude);
    vec3 distorted2 = noisePosition(nearby2, time, amplitude);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}

vec3 noisePosition1(in vec3 position, float amplitude, float noise) {
    return position * (1.0 + noise * 0.35 * amplitude);
}

vec3 calcNormal1(in vec3 position, float amplitude, float noise) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition1(position, amplitude, noise);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition1(nearby1, amplitude, noise);
    vec3 distorted2 = noisePosition1(nearby2, amplitude, noise);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}

in vec3 a_position;
in vec3 a_normal;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_modelMatrix;
uniform float u_time;
uniform vec3 u_translation;
uniform float u_scale;
uniform float u_noise_amplitude;

out vec3 v_origin_position;
out vec3 v_position;
out vec3 v_normal;

void main() {
    v_origin_position = a_position;
    v_position = noisePosition(a_position, u_time * 0.45, u_noise_amplitude);
    v_normal = calcNormal(a_position, u_time * 0.45, 1.0);
    gl_Position = u_projectionViewMatrix * (u_modelMatrix * vec4(v_position * u_scale, 1.0) + vec4(u_translation.z, u_translation.y, -u_translation.x, 0.0));
}"####),
"shader/cosmic/gradient_background_frag.glsl" => Some(r####"#version 320 es
precision highp float;

highp float basicDithering (in highp vec2 st, highp float intensity) {
    return mix(-intensity/ 255., intensity/255., fract(sin(dot(st.xy, vec2(12.9898, 78.233)))* 43758.5453123));
}

in vec2 v_uv;
uniform vec2 u_start_pos;
uniform vec2 u_end_pos;
uniform vec4 u_start_color;
uniform vec4 u_end_color;
out vec4 fragColor;

void main() {
    vec2 startToPosition = normalize(v_uv - u_start_pos);
    vec2 startToEnd = normalize(u_end_pos - u_start_pos);

    float angle = dot(startToPosition, startToEnd);
    if (angle < 0.0) {
        fragColor = vec4(u_start_color.rgb, 1.0);
    } else {
        float distance = length(v_uv - u_start_pos);
        float percentage = angle * distance / length(u_end_pos - u_start_pos);
        fragColor = vec4(mix(u_start_color.rgb, u_end_color.rgb, percentage), 1.0);
    }
    fragColor.rgb += basicDithering(v_uv, 1.0);
}"####),
"shader/cosmic/gradient_background_vertex.glsl" => Some(r####"#version 320 es

in vec3 a_position;
in vec2 a_texCoord0;
uniform mat4 u_projectionViewMatrix;
out vec2 v_uv;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position, 1.0);
    v_uv = vec2(a_texCoord0.x, 1.0 - a_texCoord0.y);
}"####),
"shader/phoenix/cosmicPhoenix_frag.glsl" => Some(r####"#version 320 es
precision highp float;

float hash(float n) {
    return fract(sin(n) * 753.5453123);
}

float noise(in vec3 floor, in vec3 fract) {
    vec3 p = floor;
    vec3 f = fract;
    f = f*f*(3.0-2.0*f);

    float n = p.x + p.y * 157.0 + 113.0 * p.z;
    return mix(mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),
    mix(hash(n + 157.0), hash(n + 158.0), f.x), f.y),
    mix(mix(hash(n + 113.0), hash(n + 114.0), f.x),
    mix(hash(n + 270.0), hash(n + 271.0), f.x), f.y), f.z);
}

float precision_noise(in vec3 base, in vec3 time) {
    vec3 _fract = fract(base) + fract(time);
    vec3 _floor = floor(base) + floor(time) + floor(_fract);
    _fract = fract(_fract);
    return noise(_floor, _fract);
}

vec3 noisePosition(in vec3 position, float time, float amplitude) {
    return position * (1.0 + precision_noise(position, vec3(time)) * 0.35 * amplitude);
}

vec3 orthogonal(in vec3 v) {
    return normalize(abs(v.x) > abs(v.z) ? vec3(-v.y, v.x, 0.0) : vec3(0.0, -v.z, v.y));
}

vec3 calcNormal(in vec3 position, float time, float amplitude) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition(position, time, amplitude);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition(nearby1, time, amplitude);
    vec3 distorted2 = noisePosition(nearby2, time, amplitude);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}

vec3 noisePosition1(in vec3 position, float amplitude, float noise) {
    return position * (1.0 + noise * 0.35 * amplitude);
}

vec3 calcNormal1(in vec3 position, float amplitude, float noise) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition1(position, amplitude, noise);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition1(nearby1, amplitude, noise);
    vec3 distorted2 = noisePosition1(nearby2, amplitude, noise);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}
vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}

out vec4 fragColor;
in vec3 v_origin_position;
in vec3 v_position;
in vec3 v_normal;
in vec3 v_normal1;
in vec4 v_GlPosition;
in vec3 finalNoise;
in float noiseValue;
in float noiseValue2;
in float MaskValue;

uniform vec3 u_eye_position;
uniform float u_time;

//@Color(边缘高光),#00FF00) aod.baseColor
uniform vec4 u_base_color;
//@Range(高光厚度,2,0,10,0.1) aod.stripeThickness
uniform float u_stripe_thickness;

//@Color(中心,#00FF00) aod.stripeColor2
uniform vec4 u_stripe_color_2;
//@Color(中心外,#00FF00) aod.u_base_color_flow
uniform vec4 u_base_color_flow;
//@Color(中横-变亮,#FFFFFF) aod.highlightColor
uniform vec4 u_highlight_color;
//@Range(中横强度,0.6,0,10,0.1) aod.highlightStrength
uniform float u_highlight_strength;
//uniform float u_highlight_strength1;

//@Color(上条纹凹槽,#00FF00) aod.stripeColor3
uniform vec4 u_stripe_color_3;
//@Color(上球-变亮,#00FF00) aod.u_stripe_color_flow_2
uniform vec4 u_stripe_color_flow_2;
//@Color(下外圈,#00FF00) aod.u_stripe_color_flow_1
uniform vec4 u_stripe_color_flow_1;
//@Color(下最外圈),#00FF00) aod.stripeColor1
uniform vec4 u_stripe_color_1;


//@Color(流动条纹3,#00FF00) aod.u_stripe_color_flow_3
uniform vec4 u_stripe_color_flow_3;

//@Range(条纹范围,0.4,-10,10,0.1) aod.stripeDivergent
uniform float u_stripe_divergent;
//@Range(颜色条纹范围,0.4,-10,10,0.1) aod.u_stripe_divergent1
uniform float u_stripe_divergent1;
//@Range(颜色条纹厚度,2,0,10,0.1) aod.u_stripe_thickness1
uniform float u_stripe_thickness1;
//@Range(高亮厚度,6,0,10,0.1) aod.highlightThickness
uniform float u_highlight_thickness;
//@Range(颜色高亮厚度,6,0,10,0.1) aod.u_highlight_thickness1
uniform float u_highlight_thickness1;

//@Range(透明度,1,0,1,0.1) aod.alpha
uniform float u_alpha;
//@Range(黑线抽搐,6,0,10,0.1) aod.u_positionTime
uniform float u_positionTime;
//@Range(黑线变化,6,0,10,0.1) aod.u_normalTime
uniform float u_normalTime;


vec3 mapColor(vec3 mapR, vec3 mapG, vec3 mapB, vec3 inputColor) {
    vec3 baseColor = mapR;
    vec3 blendColor = blendNormal(baseColor, mapG, clamp(inputColor.g, 0.0, 1.0));
    return blendNormal(blendColor, mapB, clamp(inputColor.b, 0.0, 1.0));
}

void main () {

    vec3 rd = normalize(u_eye_position - v_position);


    vec3 position_distorted1 = v_origin_position +  precision_noise(v_origin_position * 1.5, vec3(0.0, 0.0, sin(u_time * u_positionTime))) * u_stripe_divergent1;
    vec3 normal_distorted1 = calcNormal(position_distorted1, u_time * u_normalTime, 1.0 + (19.0 * u_stripe_divergent1));
    float ndotl_distorted1 = abs(dot(-rd, normal_distorted1));
    // 计算条纹颜色
    vec3 stripe_color1 = mapColor(u_stripe_color_flow_1.rgb, u_stripe_color_flow_2.rgb, u_stripe_color_flow_3.rgb, v_normal1);
    // 计算条纹纹理
    float rim_distorted1 = pow(1.0 - ndotl_distorted1, u_stripe_thickness1/*条纹粗细*/);
    // 将基础色和条纹进行混合
    vec3 finalColor1 = mix(u_base_color_flow.rgb, stripe_color1.rgb, rim_distorted1);

    //float ndotl1 = abs(dot(-rd, v_normal1));
    // 计算高光轮廓
    //float rim1 = pow(1.0 - ndotl1, u_highlight_thickness1/*条纹粗细*/);
    // 叠加高光
    //finalColor1 = blendNormal(finalColor1, u_highlight_color.rgb, rim1 * u_highlight_strength1);



    vec3 position_distorted = v_origin_position +  finalNoise;
    vec3 normal_distorted = calcNormal1(position_distorted, 1.0 + (19.0 * u_stripe_divergent), dot(finalNoise, finalNoise));
    float ndotV_distorted = dot(-rd, normal_distorted)*0.5+0.5;

    // 计算条纹颜色
    vec3 stripe_color = mapColor(u_stripe_color_1.rgb, u_stripe_color_2.rgb, u_stripe_color_3.rgb, v_normal);
    // 计算条纹纹理
    float rim_distorted = pow(1.0 - ndotV_distorted, u_stripe_thickness/*条纹粗细*/);
    // 将基础色和条纹进行混合
    vec3 finalColor = mix(u_base_color.rgb, stripe_color.rgb, rim_distorted);
    //vec3 finalColor = vec3(0.0);

    float ndotl = abs(dot(-rd, v_normal));
    // 计算高光轮廓
    float rim = pow(1.0 - ndotl, u_highlight_thickness/*条纹粗细*/);
    // 叠加高光
    finalColor += blendNormal(finalColor, u_highlight_color.rgb, rim * u_highlight_strength) * finalColor1;

    fragColor = vec4(finalColor, u_alpha);
}
"####),
"shader/phoenix/cosmicPhoenix_vertex.glsl" => Some(r####"#version 320 es
precision highp float;

float hash(float n) {
    return fract(sin(n) * 753.5453123);
}

float noise(in vec3 floor, in vec3 fract) {
    vec3 p = floor;
    vec3 f = fract;
    f = f*f*(3.0-2.0*f);

    float n = p.x + p.y * 157.0 + 113.0 * p.z;
    return mix(mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),
    mix(hash(n + 157.0), hash(n + 158.0), f.x), f.y),
    mix(mix(hash(n + 113.0), hash(n + 114.0), f.x),
    mix(hash(n + 270.0), hash(n + 271.0), f.x), f.y), f.z);
}

float precision_noise(in vec3 base, in vec3 time) {
    vec3 _fract = fract(base) + fract(time);
    vec3 _floor = floor(base) + floor(time) + floor(_fract);
    _fract = fract(_fract);
    return noise(_floor, _fract);
}

vec3 noisePosition(in vec3 position, float time, float amplitude) {
    return position * (1.0 + precision_noise(position, vec3(time)) * 0.35 * amplitude);
}

vec3 orthogonal(in vec3 v) {
    return normalize(abs(v.x) > abs(v.z) ? vec3(-v.y, v.x, 0.0) : vec3(0.0, -v.z, v.y));
}

vec3 calcNormal(in vec3 position, float time, float amplitude) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition(position, time, amplitude);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition(nearby1, time, amplitude);
    vec3 distorted2 = noisePosition(nearby2, time, amplitude);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}

vec3 noisePosition1(in vec3 position, float amplitude, float noise) {
    return position * (1.0 + noise * 0.35 * amplitude);
}

vec3 calcNormal1(in vec3 position, float amplitude, float noise) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition1(position, amplitude, noise);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition1(nearby1, amplitude, noise);
    vec3 distorted2 = noisePosition1(nearby2, amplitude, noise);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}

in highp vec3 a_position;
in highp vec3 a_normal;
in highp vec2 a_texCoord0;

//@Camera(CameraPos,0,0,0,-2000,2000,1) aod.cameraPos
uniform vec3 camera_position;
//@Camera(Zoom,1,0,2,0.01) aod.cameraZoom
uniform float camera_zoom;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_modelMatrix;
uniform float u_time;
uniform vec3 u_translation;
//uniform float u_scale;
uniform float u_noise_amplitude;
uniform highp sampler2D u_Texture2D;

//@Range(波纹速度X,0.2,0,1,0.01) aod.OffsetSpeedX
uniform float OffsetSpeedX;// = 2.4;
//@Range(波纹速度Y,0.2,0,1,0.01) aod.OffsetSpeedY
uniform float OffsetSpeedY;// = 0.55;
//@Range(扰乱强度,0.5,0,5,0.1) aod.TwistScale
uniform float TwistScale;// = 2.0;
//@Range(波纹强度,0.5,0,5,0.1) aod.OffsetScale
uniform float OffsetScale;// = 0.6;

vec4 u_TwistTiling = vec4(1.0,1.0,0,0);

vec4 u_MainTiling = vec4(0.5,0.5,0,0);

vec4 u_MaskTiling = vec4(1.0,1.0,0,0);

//@Range(扰乱速度X,0.2,0,1,0.01) aod.u_TwistSpeedX
uniform float u_TwistSpeedX;// = 0.05;

//@Range(扰乱速度Y,0.2,0,1,0.01) aod.u_TwistSpeedY
uniform float u_TwistSpeedY;// = 0.1;
//@Range(遮罩大小,0.2,0,1,0.01) aod.u_MaskScale
uniform float u_MaskScale;// = 0.5;

out vec3 v_origin_position;
out vec3 v_position;
out vec3 v_normal;
out vec3 v_normal1;
out vec2 v_TexCoord0;
out vec2 v_TexCoord1;
out vec2 v_TexCoord2;
out vec4 v_GlPosition;
out vec3 finalNoise;
out float noiseValue;
out float noiseValue2;
out float MaskValue;
//out sampler2D Texture2D;

void main() {
    v_origin_position = a_position;
    v_position = noisePosition(a_position, 0.45, u_noise_amplitude);
    v_normal1 = calcNormal(a_position, 0.45, 1.0);
    //gl_Position = u_projectionViewMatrix * (u_modelMatrix * vec4(v_position * u_scale, 1.0) );

    //uv1
    v_TexCoord0 = a_texCoord0 * u_TwistTiling.xy + u_TwistTiling.zw;
    v_TexCoord0 = v_TexCoord0 + u_time * u_TwistSpeedX + u_time * u_TwistSpeedY;
    //v_TexCoord0 = v_TexCoord0 + fract(vec2(u_time * u_TwistSpeedX, u_time * u_TwistSpeedY));
    //v_TexCoord0.x = (v_TexCoord0.x - step(v_TexCoord0.x, 0.0001)) / (1.0 + step(1.0 - v_TexCoord0.x, 0.0001));
    //v_TexCoord0.y = (v_TexCoord0.y - step(v_TexCoord0.y, 0.0001)) / (1.0 + step(1.0 - v_TexCoord0.y, 0.0001));

    //uv2
    v_TexCoord1 = a_texCoord0 * u_MainTiling.xy + u_MainTiling.zw;

    //uv3
    v_TexCoord2 = a_texCoord0 * u_MaskTiling.xy + u_MaskTiling.zw;
    v_normal=a_normal;
    //v_position = sin(u_time)+a_position;


    //noiseValue = texture(u_Texture2D, fract(v_TexCoord0)).g;//0Tex
    noiseValue = texture(u_Texture2D, v_TexCoord0).g;//0Tex

    //1tex
    vec2 noiseUV = noiseValue*TwistScale+v_TexCoord1;
    noiseUV = vec2(noiseUV.x+OffsetSpeedX*u_time, noiseUV.y + OffsetSpeedY*u_time);
    //noiseValue2 =  texture(u_Texture2D, fract(noiseUV)).r;
    noiseValue2 =  texture(u_Texture2D, noiseUV).r;

    //2tex
    MaskValue = texture(u_Texture2D, v_TexCoord2).b;//2Tex
    MaskValue = mix(MaskValue, 1.0, u_MaskScale);

    finalNoise = (MaskValue*noiseValue2-0.5)*OffsetScale*a_normal;
    v_GlPosition = u_projectionViewMatrix * (u_modelMatrix *  vec4( a_position * 300.0 + finalNoise * 100.0, 1.0));

    //Texture2D = u_Texture2D;

    gl_Position = v_GlPosition;
    //gl_Position = u_projectionViewMatrix * (u_modelMatrix * vec4(a_position * u_scale, 1.0));

}
"####),
"shader/photo/aod/chromakey/chromakey_photo_frag.glsl" => Some(r####"#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}
vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}

out vec4 fragColor;
in vec2 v_video_texCoord;
in vec2 v_snapshot_texCoord;
in vec2 v_content_texCoord;

uniform samplerExternalOES u_green_screen;
uniform sampler2D u_photo;
uniform sampler2D u_launcher_effect;
uniform sampler2D u_snapshot;
uniform float u_photo_alpha;
uniform vec3 u_first_color;
uniform bool u_use_snapshot;

void main () {
    vec4 videoColor;
    if (u_use_snapshot) {
        videoColor = texture(u_snapshot, v_snapshot_texCoord);
    } else {
        videoColor = texture(u_green_screen, v_video_texCoord);
    }

    vec3 finalColor = vec3(0.0);
    finalColor = blendNormal(finalColor, u_first_color, videoColor.r);
    finalColor = blendNormal(finalColor, vec3(1.0), videoColor.b);

    vec3 contentColor = texture(u_photo, v_content_texCoord).rgb;
    if (u_photo_alpha < 1.0) { // aod-launcher动画, lock-launcher融合
        vec3 launcherTex = texture(u_launcher_effect, v_content_texCoord).rgb;
        contentColor = mix(launcherTex, contentColor, u_photo_alpha);
    }
    finalColor = blendNormal(finalColor, contentColor, videoColor.g);

    // A通道要作为标志位, 传递到chromakey_blend_frag中, 使得三界混合的图像能够约束在息屏特效的绿屏范围内
    // 0: 最终颜色使用混合色, 1: 最终颜色使用黑色, 之所以不让1定义为混合色,
    // 因为chromakey的网格是个方形网格, 屏幕上不是所有像素都能够走到片元着色器中渲染, 而没有走进片元着色器的像素使用的是清屏颜色,
    // a通道的值是1, 这会导致那些使用清屏颜色的像素, 最终渲染为混合色, 即没有约束在绿屏范围内
    fragColor = vec4(finalColor, 1.0 - videoColor.g);
}

"####),
"shader/photo/aod/chromakey/chromakey_photo_vertex.glsl" => Some(r####"#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_video_texCoord;
out vec2 v_snapshot_texCoord;
out vec2 v_content_texCoord;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_modelMatrix;
uniform mat4 u_contentMatrix;
uniform mat4 u_video_transform;
uniform float u_aspect_ratio;

void main() {
    gl_Position = u_projectionViewMatrix * u_modelMatrix * vec4(a_position, 1);
    v_video_texCoord = (u_video_transform * vec4(a_texCoord0, 0.0, 1.0)).xy;
    v_snapshot_texCoord = vec2(a_texCoord0.x, 1.0 - a_texCoord0.y);
    v_content_texCoord = (u_contentMatrix * vec4(a_texCoord0.x, 1.0 - a_texCoord0.y, 0.0, 1.0)).xy;
}"####),
"shader/photo/aod/fullAod/full_aod_frag.glsl" => Some(r####"#version 300 es

precision highp float;

in vec2 v_texCoord;

out vec4 fragColor;

uniform float u_alpha1;
uniform sampler2D u_lockTex;
uniform sampler2D u_grayTex1;

void main () {
        vec3 lock = texture(u_lockTex, v_texCoord).rgb;

        float gray1 = texture(u_grayTex1, v_texCoord).r;

        float rate1 = mix(1.0, gray1, u_alpha1);

        vec3 color = lock * rate1;

        fragColor = vec4(color, 1.0);
}

"####),
"shader/photo/aod/fullAod/full_aod_vertex.glsl" => Some(r####"#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_texCoord = a_texCoord0;
}"####),
"shader/photo/aod/lensPhoto/lens_photo_frag.glsl" => Some(r####"#version 300 es
precision highp float;
precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}
vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}

#define ENABLE_RIPPLE 0
#define ENABLE_DEBUG_GRID 0

in vec2 v_originTexCoord;
in vec2 v_decorato_texCoord;
uniform sampler2D u_photo;
uniform sampler2D u_lens_decorator;
uniform vec3 u_translation;
uniform mat4 u_texture_matrix;
uniform float u_lens_scale;
uniform float u_aspect_ratio;
uniform float u_aspect_ratio_reciprocal;
uniform float u_distortion;
uniform float u_zoom;
uniform float u_decorator_alpha;
uniform float u_ripple_radius;
uniform float u_ripple_boundary;
uniform float u_dark_strength;
out vec4 fragColor;

const vec3 saturationWeighting = vec3(0.2125, 0.7154, 0.0721);

vec2 lens_distortion(vec2 r, float alpha) {
    return r * (1.0 - alpha * dot(r, r));
}

vec2 zoom_point(vec2 uv, vec2 point, float zoom) {
    return (uv - point) * zoom + point;
}

vec3 increaseSaturation(vec3 base, float strength) {
    float luminance = dot(base, saturationWeighting);
    vec3 greyScaleColor = vec3(luminance);
    return mix(greyScaleColor, base, 1.0 + strength);
}

float grid(vec2 uv, float _scale, float thickness) {
    vec2 grid_uv = fract(_scale * uv);
    vec2 grid_vec = 1.0 - smoothstep(0.0, thickness, grid_uv);
    return 1.0 - dot(grid_vec, vec2(1.0));
}

void main () {
    vec2 uv = vec2(v_originTexCoord.x, v_originTexCoord.y * u_aspect_ratio);
    vec2 uvCenter = vec2(0.5 + u_translation.x, (0.5 + u_translation.y) * u_aspect_ratio);
    float dist = distance(uv, uvCenter);
    if (dist > u_lens_scale) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec2 distortionUV = zoom_point(uv + lens_distortion(uv - uvCenter, u_distortion), uvCenter, u_zoom);
    #if ENABLE_RIPPLE
    if ((u_ripple_radius - u_ripple_boundary) > 0.0 && (dist <= (u_ripple_radius + u_ripple_boundary)) && (dist >= (u_ripple_radius - u_ripple_boundary))) {
        float x = (dist - u_ripple_radius);
        float moveDis = 20.0 * x * (x - 0.1)*(x + 0.1);
        vec2 unitDirectionVec = normalize(uv - uvCenter);
        distortionUV += (unitDirectionVec * moveDis);
    }
    #endif

    vec2 transformUV = (u_texture_matrix * vec4((vec2(distortionUV.x, distortionUV.y * u_aspect_ratio_reciprocal) * 2.0) - 1.0, 0.0, 1.0)).xy;
    transformUV.y = 1.0 - transformUV.y;
    vec3 finalColor = texture(u_photo, transformUV).rgb;
    if (u_decorator_alpha > 0.0001) {
        finalColor = increaseSaturation(finalColor, u_decorator_alpha * 0.5f);
        vec4 decoratoColor = texture(u_lens_decorator, v_decorato_texCoord);
        finalColor = blendNormal(finalColor, decoratoColor.rgb, decoratoColor.a * u_decorator_alpha);
    }
    #if ENABLE_DEBUG_GRID
    float grid = grid(transformUV, 20.0, 0.05);
    finalColor = min(finalColor, vec3(grid, grid, grid));
    #endif
    vec4 darkColor = applyDarkIfNeed(vec4(finalColor, 1.0), u_dark_strength);
    fragColor = mix(darkColor, vec4(0.0, 0.0, 0.0, 1.0), smoothstep(u_lens_scale - 0.002, u_lens_scale, dist));
}"####),
"shader/photo/aod/lensPhoto/lens_photo_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_decorator_matrix;

out vec2 v_originTexCoord;
out vec2 v_decorato_texCoord;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position, 1.0);
    v_originTexCoord = a_texCoord0;
    v_decorato_texCoord = (u_decorator_matrix * vec4((a_texCoord0 * 2.0) - 1.0, 0.0, 1.0)).xy;
    v_decorato_texCoord.y = 1.0 - v_decorato_texCoord.y;
}"####),
"shader/photo/blend/chromakey/chromakey_blend_frag.glsl" => Some(r####"#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}
vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}

uniform sampler2D u_aod_effect; // aod特效纹理
uniform sampler2D u_lock_effect; // lock特效纹理
uniform sampler2D u_launcher_effect; // launcher特效纹理
uniform vec3 u_ratio; // aod、lock、launcher三界比率系数
uniform float u_dark_strength; // 深色强度
uniform bool u_support_lock_launcher_blend; // 是否支持lock和launcher融合, 解决融合造成的重影问题, 0: 不支持, 1: 支持

in vec2 v_texCoord0;

out vec4 o_fragColor;

void main() {
    vec3 contentColor;
    if (u_ratio.x > 0.999999) { // 只渲染aod特效
        contentColor = texture(u_aod_effect, v_texCoord0).rgb;
    } else if (u_ratio.y > 0.999999) { // 只渲染lock特效
        contentColor = texture(u_lock_effect, v_texCoord0).rgb;
    } else if (u_ratio.z > 0.999999) { // 只渲染launcher特效
        contentColor = texture(u_launcher_effect, v_texCoord0).rgb;
    } else if (u_ratio.x < 0.000001) { // lock-launcher融合
        vec3 lockTex = texture(u_lock_effect, v_texCoord0).rgb;
        vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
        contentColor = (lockTex * u_ratio.y + launcherTex * u_ratio.z) / (u_ratio.y + u_ratio.z);
    } else if (u_ratio.y < 0.000001) { // aod-launcher融合
        contentColor = texture(u_aod_effect, v_texCoord0).rgb;
    } else if (u_ratio.z < 0.000001) { // aod-lock融合
        contentColor = texture(u_aod_effect, v_texCoord0).rgb;
    } else { // aod-lock-launcher融合
        if (u_support_lock_launcher_blend) {
            vec4 aodTex = texture(u_aod_effect, v_texCoord0);
            vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
            contentColor = (aodTex.rgb * (u_ratio.x + u_ratio.y) + launcherTex * u_ratio.z) / (u_ratio.x + u_ratio.y + u_ratio.z);
            contentColor = mix(contentColor, vec3(0.0), aodTex.a);
        } else {
            contentColor = texture(u_aod_effect, v_texCoord0).rgb; // 解决重影问题(#1298205)
        }
    }
    //增加暗黑模式处理
    o_fragColor = applyDarkIfNeed(vec4(contentColor, 1.0), u_dark_strength);
}"####),
"shader/photo/blend/chromakey/chromakey_blend_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord0;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_texCoord0 = a_texCoord0;
}"####),
"shader/photo/blend/common/common_blend_frag.glsl" => Some(r####"#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}
vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}

uniform sampler2D u_aod_effect; // aod特效纹理
uniform sampler2D u_lock_effect; // lock特效纹理
uniform sampler2D u_launcher_effect; // launcher特效纹理
uniform vec3 u_ratio; // aod、lock、launcher三界比率系数
uniform float u_dark_strength; // 深色强度

in vec2 v_texCoord0;

out vec4 o_fragColor;

void main() {
    vec3 contentColor;
    if (u_ratio.x > 0.999999) { // 只渲染aod特效
        contentColor = texture(u_aod_effect, v_texCoord0).rgb;
    } else if (u_ratio.y > 0.999999) { // 只渲染lock特效
        contentColor = texture(u_lock_effect, v_texCoord0).rgb;
    } else if (u_ratio.z > 0.999999) { // 只渲染launcher特效
        contentColor = texture(u_launcher_effect, v_texCoord0).rgb;
    } else if (u_ratio.x < 0.000001) { // lock-launcher融合
        vec3 lockTex = texture(u_lock_effect, v_texCoord0).rgb;
        vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
        contentColor = (lockTex * u_ratio.y + launcherTex * u_ratio.z) / (u_ratio.y + u_ratio.z);
    } else if (u_ratio.y < 0.000001) { // aod-launcher融合
        vec3 aodTex = texture(u_aod_effect, v_texCoord0).rgb;
        vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
        contentColor = (aodTex * u_ratio.x + launcherTex * u_ratio.z) / (u_ratio.x + u_ratio.z);
    } else if (u_ratio.z < 0.000001) { // aod-lock融合
        vec3 aodTex = texture(u_aod_effect, v_texCoord0).rgb;
        vec3 lockTex = texture(u_lock_effect, v_texCoord0).rgb;
        contentColor = (aodTex * u_ratio.x + lockTex * u_ratio.y) / (u_ratio.x + u_ratio.y);
    } else { // aod-lock-launcher融合
        vec3 aodTex = texture(u_aod_effect, v_texCoord0).rgb;
        vec3 lockTex = texture(u_lock_effect, v_texCoord0).rgb;
        vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
        contentColor = (aodTex * u_ratio.x + lockTex * u_ratio.y + launcherTex * u_ratio.z) / (u_ratio.x + u_ratio.y + u_ratio.z);
    }
    //增加暗黑模式处理
    o_fragColor = applyDarkIfNeed(vec4(contentColor, 1.0), u_dark_strength);
}"####),
"shader/photo/blend/common/common_blend_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord0;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_texCoord0 = a_texCoord0;
}"####),
"shader/photo/blend/fullCommon/full_common_blend_frag.glsl" => Some(r####"#version 300 es

precision highp float;

precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}

uniform sampler2D u_aod_effect; // aod特效纹理
uniform sampler2D u_lock_effect; // lock特效纹理
uniform sampler2D u_launcher_effect; // launcher特效纹理
uniform vec3 u_ratio; // aod、lock、launcher三界比率系数
uniform float u_dark_strength; // 深色强度
uniform bool u_support_lock_launcher_blend; // 是否支持lock和launcher融合, 解决融合造成的重影问题, 0: 不支持, 1: 支持

in vec2 v_texCoord0;

out vec4 o_fragColor;

void main() {
    vec3 contentColor;
    if (u_ratio.x > 0.999999) { // 只渲染aod特效
        contentColor = texture(u_aod_effect, v_texCoord0).rgb;
    } else if (u_ratio.y > 0.999999) { // 只渲染lock特效
        contentColor = texture(u_lock_effect, v_texCoord0).rgb;
    } else if (u_ratio.z > 0.999999) { // 只渲染launcher特效
        contentColor = texture(u_launcher_effect, v_texCoord0).rgb;
    } else if (u_ratio.x < 0.000001) { // lock-launcher融合
        vec3 lockTex = texture(u_lock_effect, v_texCoord0).rgb;
        vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
        contentColor = (lockTex * u_ratio.y + launcherTex * u_ratio.z) / (u_ratio.y + u_ratio.z);
    } else if (u_ratio.y < 0.000001) { // aod-launcher融合
        vec3 aodTex = texture(u_aod_effect, v_texCoord0).rgb;
        vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
        contentColor = (aodTex * u_ratio.x + launcherTex * u_ratio.z) / (u_ratio.x + u_ratio.z);
    } else if (u_ratio.z < 0.000001) { // aod-lock融合
        contentColor = texture(u_aod_effect, v_texCoord0).rgb;
    } else { // aod-lock-launcher融合
        if (u_support_lock_launcher_blend) {
            vec3 aodTex = texture(u_aod_effect, v_texCoord0).rgb;
            vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
            contentColor = (aodTex * (u_ratio.x + u_ratio.y) + launcherTex * u_ratio.z) / (u_ratio.x + u_ratio.y + u_ratio.z);
        } else {
            contentColor = texture(u_aod_effect, v_texCoord0).rgb; // 解决重影问题(#1298205)
        }
    }
    //增加暗黑模式处理
    o_fragColor = applyDarkIfNeed(vec4(contentColor, 1.0), u_dark_strength);
}"####),
"shader/photo/blend/fullCommon/full_common_blend_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord0;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_texCoord0 = a_texCoord0;
}"####),
"shader/photo/blend/holdHand/holdhand_frag.glsl" => Some(r####"#version 300 es

// AlivePhoto引擎跟手片元着色器(#1293213)

precision highp float;

precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}

uniform sampler2D u_TextureUnit0; // 桌面纹理
uniform sampler2D u_TextureUnit1; // 锁屏纹理
uniform float u_fraction; // 状态栏下拉比例
uniform float u_dark_strength; // 深色强度

in vec2 v_texCoord0;
out vec4 o_FragColor;

void main() {
    vec3 lockTex = texture(u_TextureUnit1, v_texCoord0).rgb;
    vec3 contentColor = lockTex;
    if (u_fraction < 0.75) {
        vec3 launcherTex = texture(u_TextureUnit0, v_texCoord0).rgb;
        float factor = u_fraction * 1.333333;
        contentColor = mix(launcherTex, lockTex, factor);
    }
    //增加暗黑模式处理
    o_FragColor = applyDarkIfNeed(vec4(contentColor, 1.0), u_dark_strength);
}"####),
"shader/photo/blend/holdHand/holdhand_vertex.glsl" => Some(r####"#version 300 es

// AlivePhoto引擎跟手顶点着色器(#1293213)

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;

out vec2 v_texCoord0;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_texCoord0 = a_texCoord0;
}"####),
"shader/photo/blend/lens/lens_blend_frag.glsl" => Some(r####"#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}
vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}

uniform sampler2D u_aod_effect; // aod特效纹理
uniform sampler2D u_lock_effect; // lock特效纹理
uniform sampler2D u_launcher_effect; // launcher特效纹理
uniform vec3 u_ratio; // aod、lock、launcher三界比率系数
uniform float u_dark_strength; // 深色强度
uniform bool u_support_lock_launcher_blend; // 是否支持lock和launcher融合, 解决融合造成的重影问题, 0: 不支持, 1: 支持
uniform vec2 u_translation;
uniform float u_lens_scale;
uniform float u_aspect_ratio;

in vec2 v_texCoord0;

out vec4 o_fragColor;

float getDist() {
    vec2 aspectUv = vec2(v_texCoord0.x, v_texCoord0.y * u_aspect_ratio);
    vec2 distortionCenter = vec2(0.5 + u_translation.x, (0.5 - u_translation.y) * u_aspect_ratio);
    return distance(aspectUv, distortionCenter);
}

vec3 getLauncherColor() {
    float dist = getDist();
    if (dist > u_lens_scale) {
        return vec3(0.0, 0.0, 0.0);
    }
    vec2 uv = v_texCoord0 + vec2(-u_translation.x, u_translation.y);
    vec3 launcherTex = texture(u_launcher_effect, uv).rgb;
    float factor = smoothstep(u_lens_scale - 0.002, u_lens_scale, dist);
    return mix(launcherTex, vec3(0.0, 0.0, 0.0), factor);
}

void main() {
    vec3 contentColor;
    if (u_ratio.x > 0.999999) { // 只渲染aod特效
        contentColor = texture(u_aod_effect, v_texCoord0).rgb;
    } else if (u_ratio.y > 0.999999) { // 只渲染lock特效
        contentColor = texture(u_lock_effect, v_texCoord0).rgb;
    } else if (u_ratio.z > 0.999999) { // 只渲染launcher特效
        //contentColor = texture(u_launcher_effect, v_texCoord0).rgb;
        contentColor = getLauncherColor();
    } else if (u_ratio.x < 0.000001) { // lock-launcher融合
        vec3 lockTex = texture(u_lock_effect, v_texCoord0).rgb;
        vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
        contentColor = (lockTex * u_ratio.y + launcherTex * u_ratio.z) / (u_ratio.y + u_ratio.z);
    } else if (u_ratio.y < 0.000001) { // aod-launcher融合
        vec3 aodTex = texture(u_aod_effect, v_texCoord0).rgb;
        //vec3 launcherTex = texture(u_launcher_effect, v_texCoord0).rgb;
        vec3 launcherTex = getLauncherColor();
        contentColor = (aodTex * u_ratio.x + launcherTex * u_ratio.z) / (u_ratio.x + u_ratio.z);
    } else if (u_ratio.z < 0.000001) { // aod-lock融合
        contentColor = texture(u_aod_effect, v_texCoord0).rgb;
    } else { // aod-lock-launcher融合
        if (u_support_lock_launcher_blend) {
            vec3 aodTex = texture(u_aod_effect, v_texCoord0).rgb;
            vec3 launcherTex = getLauncherColor();
            contentColor = (aodTex * (u_ratio.x + u_ratio.y) + launcherTex * u_ratio.z) / (u_ratio.x + u_ratio.y + u_ratio.z);
        } else {
            contentColor = texture(u_aod_effect, v_texCoord0).rgb; // 解决重影问题(#1298205)
        }
    }
    //增加暗黑模式处理
    o_fragColor = applyDarkIfNeed(vec4(contentColor, 1.0), u_dark_strength);
}"####),
"shader/photo/blend/lens/lens_blend_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord0;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_texCoord0 = a_texCoord0;
}"####),
"shader/photo/launcher/blur/blur_frag.glsl" => Some(r####"#version 300 es

precision highp float;

uniform sampler2D u_texture; // 模糊后的纹理

in vec2 v_texCoord0;

out vec4 o_fragColor;

void main() {
    // 直接采样模糊后的纹理（已经是完整的 [0, 1] 范围）
    o_fragColor = texture(u_texture, v_texCoord0);
}

"####),
"shader/photo/launcher/blur/blur_vert.glsl" => Some(r####"#version 300 es

in vec4 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord0;

void main() {
    v_texCoord0 = a_texCoord0;
    gl_Position = a_position;
}

"####),
"shader/photo/launcher/color/color_effect_frag.glsl" => Some(r####"#version 300 es
precision highp float;
vec3 blendNormal(vec3 base, vec3 blend, float opacity) {
    return (blend * opacity + base * (1.0 - opacity));
}

float blendOverlay(float base, float blend) {
    return base < 0.5 ? (2.0 * base * blend) : (1.0 - 2.0 * (1.0 - base) * (1.0 - blend));
}

vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(blendOverlay(base.r, blend.r), blendOverlay(base.g, blend.g), blendOverlay(base.b, blend.b));
}

vec3 blendOverlay(vec3 base, vec3 blend, float opacity) {
    return (blendOverlay(base, blend) * opacity + base * (1.0 - opacity));
}
//
// Description : Array and textureless GLSL 2D simplex noise function.
//      Author : Ian McEwan, Ashima Arts.
//  Maintainer : stegu
//     Lastmod : 20110822 (ijm)
//     License : Copyright (C) 2011 Ashima Arts. All rights reserved.
//               Distributed under the MIT License. See LICENSE file.
//               https://github.com/ashima/webgl-noise
//               https://github.com/stegu/webgl-noise
//

vec3 mod289(vec3 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec2 mod289(vec2 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec3 permute(vec3 x) {
    return mod289(((x*34.0)+10.0)*x);
}

float snoise(vec2 v)
{
    const vec4 C = vec4(0.211324865405187,  // (3.0-sqrt(3.0))/6.0
    0.366025403784439,  // 0.5*(sqrt(3.0)-1.0)
    -0.577350269189626,  // -1.0 + 2.0 * C.x
    0.024390243902439); // 1.0 / 41.0
    // First corner
    vec2 i  = floor(v + dot(v, C.yy) );
    vec2 x0 = v -   i + dot(i, C.xx);

    // Other corners
    vec2 i1;
    //i1.x = step( x0.y, x0.x ); // x0.x > x0.y ? 1.0 : 0.0
    //i1.y = 1.0 - i1.x;
    i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    // x0 = x0 - 0.0 + 0.0 * C.xx ;
    // x1 = x0 - i1 + 1.0 * C.xx ;
    // x2 = x0 - 1.0 + 2.0 * C.xx ;
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;

    // Permutations
    i = mod289(i); // Avoid truncation effects in permutation
    vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 ))
    + i.x + vec3(0.0, i1.x, 1.0 ));

    vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
    m = m*m ;
    m = m*m ;

    // Gradients: 41 points uniformly over a line, mapped onto a diamond.
    // The ring size 17*17 = 289 is close to a multiple of 41 (41*7 = 287)

    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;

    // Normalise gradients implicitly by scaling m
    // Approximation of: m *= inversesqrt( a0*a0 + h*h );
    m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );

    // Compute final noise value at P
    vec3 g;
    g.x  = a0.x  * x0.x  + h.x  * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

#define ENABLE_DEBUG_GRID 0
#define ENABLE_DEBUG_COLOR 0

in vec2 v_texCoord;
in vec2 v_originTexCoord;
uniform sampler2D u_bg_sampler;
uniform float u_time;
uniform float u_speed;
uniform float u_page_offset;
uniform float u_noise_displacement;
uniform float u_noise_uvs_zoom;
uniform vec3 u_third_color;
uniform vec3 u_second_color;
uniform vec3 u_first_color;
out vec4 fragColor;

vec3 mixColor(vec3 color) {
    vec3 baseColor = u_first_color;
    vec3 blendColor = blendNormal(baseColor, u_second_color, color.g);
    return blendNormal(blendColor, u_third_color, color.r);
}

float grid(vec2 uv, float _scale, float thickness) {
    vec2 grid_uv = fract(_scale * uv);
    vec2 grid_vec = 1.0 - smoothstep(0.0, thickness, grid_uv);
    return 1.0 - dot(grid_vec, vec2(1.0));
}

void main() {
    float time = u_time * u_speed;
    vec2 uv = v_texCoord;
    float n = snoise(vec2(uv.x, uv.y * u_noise_uvs_zoom + time * 0.1));
    uv *= vec2(1.0 + n * u_noise_displacement);
    uv.x += (u_page_offset * 0.5);
    vec3 c = texture(u_bg_sampler, uv).rgb;
    c = mixColor(c);

    vec4 finalColor = vec4(c, 1.0);
    #if ENABLE_DEBUG_GRID
    if (uv.x > 0.0 && uv.x < 1.0 && uv.y > 0.0 && uv.y < 1.0) {
        float grid = grid(uv, 20.0, 0.05);
        finalColor = min(finalColor, vec4(grid, grid, grid, 1.0));
    }
    #endif
    #if ENABLE_DEBUG_COLOR
    if (v_originTexCoord.y < 0.02) {
        if (v_originTexCoord.x < 0.33) {
            finalColor = vec4(u_third_color, 1.0);
        } else if (v_originTexCoord.x < 0.66) {
            finalColor = vec4(u_second_color, 1.0);
        } else {
            finalColor = vec4(u_first_color, 1.0);
        }
    }
    #endif
    fragColor = finalColor;
}"####),
"shader/photo/launcher/color/color_effect_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
out vec2 v_texCoord;
out vec2 v_originTexCoord;

mat2 scale(vec2 _scale){
    return mat2(_scale.x, 0.0, 0.0, _scale.y);
}

void main() {
    gl_Position = vec4(a_position, 1);
    v_originTexCoord = a_texCoord0;
    v_texCoord = a_texCoord0;
    v_texCoord.y = 1.0 - v_texCoord.y;
    v_texCoord.x *= 0.5;
    v_texCoord -= vec2(0.5);
    v_texCoord = scale(vec2(1.1)) * v_texCoord;
    v_texCoord += vec2(0.5);
}"####),
"shader/photo/launcher/common/common_vertex.glsl" => Some(r####"#version 300 es

//通用顶点shader
//flyme9.3 新增所有桌面效果使用

in vec3 a_position;

uniform mat4 u_projectionViewMatrix;

void main() {
    //gl_Position  = u_projectionViewMatrix * vec4(a_position, 1.0);
    gl_Position  = vec4(a_position, 1.0);
}"####),
"shader/photo/launcher/ether/ether_frag.glsl" => Some(r####"#version 300 es

//以太

precision highp float;

uniform float u_Time;
uniform vec2 u_Resolution;

out vec4 o_FragColor;

//color1
uniform vec3 u_BackgroundColor1;
//color2
uniform vec3 u_BackgroundColor2;

//整体速度
float u_Speed = 0.15;
//大小
float u_Size = 1.0;
//X方向
float u_X = 0.5;
//y方向
float u_Y = 0.5;
//锐化
float u_Sharpening = 0.8;
//油腻度
float u_Greasiness = 4.5;
//融合度
float u_FusionDegree = 1.0;
//厚重感
float u_Massiness = 0.7;

mat2 mat(float a) {
    float c=cos(a), s=sin(a);
    return mat2(c,-s,s,c);
}

float map(vec3 p, float t, float s){
    p.xz*= mat(t*0.4);
    p.xy*= mat(t*0.3);
    vec3 q = p*3.+t;
    return smoothstep(-1.0, 1.0,length(p + vec3(sin(t*0.7))) * log(length(p)+0.) + sin(q.x+sin(q.z+sin(q.y)))*s - 1.5);
}

void main() {
    float iTime = u_Time * u_Speed;
    vec2 uv = gl_FragCoord.xy/u_Resolution.y - vec2(u_X, u_Y);
    vec3 color = vec3(0.);
    float d = 2.5;

    for(int i=0; i<=5; i++)	{
        vec3 p = vec3(0,0,5.0) + normalize(vec3(uv, -u_Size))*d;
        float rz = map(p, iTime, u_Sharpening);
        float f =  clamp((rz - map((p+0.1), iTime, u_Sharpening))*0.1, -0.1, 1.0 );
        vec3 l = vec3(0.0314, 0.549, 0.8078) + vec3(1.0, u_Greasiness, 3.0)*f;
        color = color*l + smoothstep(u_FusionDegree, 0.0, rz)*u_Massiness*l;
        d += min(rz, 1.);
    }

    color = mix(u_BackgroundColor2, mix (u_BackgroundColor1, color, color.x), color.y);

    o_FragColor = vec4(color, 1.);
}
"####),
"shader/photo/launcher/gradient/gradient_effect_frag.glsl" => Some(r####"#version 300 es
precision highp float;

//
// Description : Array and textureless GLSL 2D simplex noise function.
//      Author : Ian McEwan, Ashima Arts.
//  Maintainer : stegu
//     Lastmod : 20110822 (ijm)
//     License : Copyright (C) 2011 Ashima Arts. All rights reserved.
//               Distributed under the MIT License. See LICENSE file.
//               https://github.com/ashima/webgl-noise
//               https://github.com/stegu/webgl-noise
//

vec3 mod289(vec3 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec2 mod289(vec2 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec3 permute(vec3 x) {
    return mod289(((x*34.0)+10.0)*x);
}

float snoise(vec2 v)
{
    const vec4 C = vec4(0.211324865405187,  // (3.0-sqrt(3.0))/6.0
    0.366025403784439,  // 0.5*(sqrt(3.0)-1.0)
    -0.577350269189626,  // -1.0 + 2.0 * C.x
    0.024390243902439); // 1.0 / 41.0
    // First corner
    vec2 i  = floor(v + dot(v, C.yy) );
    vec2 x0 = v -   i + dot(i, C.xx);

    // Other corners
    vec2 i1;
    //i1.x = step( x0.y, x0.x ); // x0.x > x0.y ? 1.0 : 0.0
    //i1.y = 1.0 - i1.x;
    i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    // x0 = x0 - 0.0 + 0.0 * C.xx ;
    // x1 = x0 - i1 + 1.0 * C.xx ;
    // x2 = x0 - 1.0 + 2.0 * C.xx ;
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;

    // Permutations
    i = mod289(i); // Avoid truncation effects in permutation
    vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 ))
    + i.x + vec3(0.0, i1.x, 1.0 ));

    vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
    m = m*m ;
    m = m*m ;

    // Gradients: 41 points uniformly over a line, mapped onto a diamond.
    // The ring size 17*17 = 289 is close to a multiple of 41 (41*7 = 287)

    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;

    // Normalise gradients implicitly by scaling m
    // Approximation of: m *= inversesqrt( a0*a0 + h*h );
    m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );

    // Compute final noise value at P
    vec3 g;
    g.x  = a0.x  * x0.x  + h.x  * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

#define ENABLE_DEBUG_GRID 0

in vec2 v_texCoord;
out vec4 fragColor;

uniform float u_aspect_ratio;
uniform float u_time;
uniform vec3 u_color1;
uniform vec3 u_color2;
uniform vec3 u_color3;
uniform vec3 u_color4;

vec2 random2(vec2 p) {
    return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3))))*43758.5453);
}

vec2 getPoint(vec2 _floor, float seed) {
    vec2 point = random2(_floor);
    point = 0.5 + 0.5*sin(u_time * 0.5 + 6.2831*point + seed);
    return _floor + point;
}

float grid(vec2 uv, float scale, float thickness) {
    vec2 grid_uv = fract(scale * uv);
    vec2 grid_vec = 1.0 - smoothstep(0.0, thickness, grid_uv);
    return 1.0 - dot(grid_vec, vec2(1.0));
}

void main() {

    // parameters
    vec2 p[4];
    p[0] = getPoint(vec2(0.0, 0.0), 12.8) * 0.5;
    p[1] = getPoint(vec2(0.0, 1.0), 19.7) * 0.5;
    p[2] = getPoint(vec2(1.0, 0.0), 23.6) * 0.5;
    p[3] = getPoint(vec2(1.0, 1.0), 11.2) * 0.5;

    vec3 colors[4];
    colors[0] = u_color1;
    colors[1] = u_color2;
    colors[2] = u_color3;
    colors[3] = u_color4;

    float w[4];
    vec3 sum=vec3(0.);
    float valence=0.;

    float distortionX=(v_texCoord.x+sin(v_texCoord.y*2.+u_time * 0.5)*.1)*(1.+snoise(vec2(v_texCoord.x+u_time*.05,v_texCoord.y))*.3);
    //distortionX=v_texCoord.x;
    float distortionY=v_texCoord.y+sin(distortionX*3.14*2.+1.3434)*.1+sin(distortionX*3.14*10.+28.3434)*.025+abs(sin(distortionX*3.14*40.+58.3434))*.015;
    // distortionY=abs(snoise(vec2(v_texCoord.x*60.,0))-.5)*0.04+v_texCoord.y;
    //    distortionY *= (0.5 - abs(v_texCoord.y - 0.5)) * 2.0;
    vec2 distortionUV=vec2(distortionX,distortionY);

    // calc IDW (Inverse Distance Weight) interpolation
    for(int i=0;i<4;i++){
        float distance=length(distortionUV-p[i]);
        if(distance==0.){distance=1.;}
        float w=1./pow(distance,2.);
        sum+=w*colors[i];
        valence+=w;
    }
    sum/=valence;

    // apply gamma 2.2 (Approx. of linear => sRGB conversion. To make perceptually linear gradient)

    sum = pow(sum, vec3(1.0/2.2));

    // output
    fragColor=vec4(sum.xyz,1.);

    #if ENABLE_DEBUG_GRID
    float grid=grid(distortionUV,20.,.05);
    fragColor.rgb=min(fragColor.rgb,vec3(grid,grid,grid));
    #endif
}"####),
"shader/photo/launcher/gradient/gradient_effect_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
out vec2 v_texCoord;

void main() {
    gl_Position = vec4(a_position, 1);
    v_texCoord = vec2(a_texCoord0.x, 1.0 - a_texCoord0.y);
}"####),
"shader/photo/launcher/gradientFlow/gradient_flow_frag.glsl" => Some(r####"#version 300 es

//梯度流

precision highp float;

uniform float u_Time;
uniform vec2 u_Resolution;

out vec4 o_FragColor;

//color1
uniform vec3 u_BackgroundColor1;
//color2
uniform vec3 u_BackgroundColor2;
//color3
uniform vec3 u_BackgroundColor3;

//速度
float u_Speed = 2.0;
//亮度
float u_Brightness = 1.0;

//上下偏移
float u_UpDownOffset = 0.40;
//参数2
float u_Parameter2 = 1.0;
//频率
float u_Frequency = 5.0;
//振幅
float u_Amplitude = 30.0;
//参数5
float u_Parameter5 = 1.5;
//参数6
float u_Parameter6 = 0.5;
//参数7
float u_Parameter7 = -0.1;
//参数8
float u_Parameter8 = 0.5;
//参数9
float u_Parameter9 = -5.0;
//参数10
float u_Parameter10 = -1.1;
//参数11
float u_Parameter11 = 0.5;
//参数12
float u_Parameter12 = -5.0;
//参数13
float u_Parameter13 = 0.5;
//参数14
float u_Parameter14 = -0.3;

//扭转系数1
float u_TorsionCoefficient1 = 590.0;
//扭转系数2
float u_TorsionCoefficient2 = 145.0;

//噪音
float u_NoiseV = 2.0;
//噪音偏移
float u_NoiseOffset = -1.0;

mat2 Rot(float a)
{
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

vec2 hash( vec2 p )
{
    p = vec2( dot(p,vec2(2127.1,81.17)), dot(p,vec2(1269.5,283.37)) );
    return fract(sin(p)*43758.5453);
}

float noise( in vec2 p, float v, float offset)
{
    vec2 i = floor( p );
    vec2 f = fract( p );

    vec2 u = f * f * (3.0 - 2.0 * f);

    float n = mix( mix( dot( offset + v * hash( i + vec2(0.0,0.0) ), f - vec2(0.0,0.0) ),
                        dot( offset + v * hash( i + vec2(1.0,0.0) ), f - vec2(1.0,0.0) ), u.x),
                   mix( dot( offset + v * hash( i + vec2(0.0,1.0) ), f - vec2(0.0,1.0) ),
                        dot( offset + v * hash( i + vec2(1.0,1.0) ), f - vec2(1.0,1.0) ), u.x), u.y);
    return 0.5 + 0.5 * n;
}

void main() {
    float iTime = u_Time * u_Speed;

    vec2 uv = gl_FragCoord.xy / u_Resolution.xy;
    float ratio = u_Resolution.x / u_Resolution.y;

    vec2 tuv = uv;
    tuv -= u_UpDownOffset;

    // rotate with Noise
    float degree = noise(vec2(iTime*.1, tuv.x*tuv.y), u_NoiseV, u_NoiseOffset);

    tuv.y *= u_Parameter2 / ratio;
    tuv *= Rot(radians((degree-.5) * u_TorsionCoefficient1 + u_TorsionCoefficient2));
    tuv.y *= ratio;


    // Wave warp with sin
    float frequency = u_Frequency;
    float amplitude = u_Amplitude;

    tuv.x += sin(tuv.y * frequency+iTime) / amplitude;
    tuv.y += sin(tuv.x * frequency * u_Parameter5 + iTime) / (amplitude * u_Parameter6);


    // draw the image

    vec3 layer1 = mix(u_BackgroundColor3, u_BackgroundColor2, smoothstep(u_Parameter7, u_Parameter8, (tuv*Rot(radians(u_Parameter9))).y));

    vec3 layer2 = mix(layer1, u_BackgroundColor1, smoothstep(u_Parameter10, u_Parameter11, (tuv*Rot(radians(u_Parameter12))).x));

    vec3 finalComp = mix(layer1, layer2, smoothstep(u_Parameter13, u_Parameter14, tuv.y));

    vec3 col = finalComp * u_Brightness;

    o_FragColor = vec4(col,1.0);
}
"####),
"shader/photo/launcher/melt/melt_frag.glsl" => Some(r####"#version 300 es

//熔化

precision highp float;

uniform float u_Time;
uniform vec2 u_Resolution;

out vec4 o_FragColor;

#define RADIANS 0.017453292519943295
//color1
uniform vec3 u_BackgroundColor1;
//color2
uniform vec3 u_BackgroundColor2;
//color3
uniform vec3 u_BackgroundColor3;
//融化参数1
int u_Zoom = 20;
//速度
float u_Speed = 20.0;
//亮度
float u_Brightness = 0.9;
//融合参数2
float u_FusionImpact = 2.0;
//密度
float u_Density = 3.0;
//分布程度
float u_DistributionDegree = 1.0;
//油腻程度1
float u_GreasyDegree1 = 0.5;
//油腻程度2
float u_GreasyDegree2 = 0.5;

float fScale = 1.25;

float cosRange(float degrees, float range, float minimum) {
    return (((u_GreasyDegree2 + cos(degrees * RADIANS)) * u_GreasyDegree1) * range) + minimum;
}

void main()
{
    float time = u_Time * u_Speed;
    vec2 p  = (u_FusionImpact * gl_FragCoord.xy-u_Resolution.xy)/max(u_Resolution.x,u_Resolution.y);
    float ct = cosRange(5.0, 3.0, 1.1);
    float xBoost = cosRange(0.2, 5.0, 5.0);
    float yBoost = cosRange(0.1, 10.0, 5.0);

    fScale = cosRange(15.5, 1.25, 0.5);

    for(int i = 1; i < u_Zoom; i++) {
        float _i = float(i);
        vec2 newp=p;
        newp.x+=0.25/_i*sin(_i*p.y+time*cos(ct)*0.5/20.0+0.005*_i)*fScale+xBoost;
        newp.y+=0.25/_i*sin(_i*p.x+time*ct*0.3/40.0+0.03*float(i+15))*fScale+yBoost;
        p=newp;
    }

    //颜色偏移
    //vec3 colUV = vec3(sin(0.5 * p.x), u_DistributionDegree * sin(u_Density * p.y), cos(0.0 * (p.x + p.y)));
    vec3 colUV=vec3(0.5*sin(3.0*p.x)+0.5,u_DistributionDegree*sin(u_Density*p.y) + 3.5,sin(p.x+p.y));
    //    vec3 col = mix(
    //    u_BackgroundColor2,
    //    mix(u_BackgroundColor1, u_BackgroundColor3, colUV.y),
    //    colUV.x);
    vec3 col = u_BackgroundColor2 * 1.05;
    col = mix(u_BackgroundColor2, col, colUV.y);
    col = mix(u_BackgroundColor3 * 1.1, col, colUV.x);
    col = mix(u_BackgroundColor1 * 1.1, col, colUV.z);
    col *= u_Brightness;

    o_FragColor = vec4(col, 1.0);
}
"####),
"shader/photo/launcher/original/original_frag.glsl" => Some(r####"#version 300 es

precision highp float;

uniform sampler2D u_texture;
uniform vec4 u_display_uv_rect; // (left, top, right, bottom) 显示区域的 UV 坐标

in vec2 v_texCoord0;

out vec4 o_fragColor;

void main() {
    // 将 UV 坐标映射到显示区域
    float u_min = u_display_uv_rect.x;
    float v_min = u_display_uv_rect.y;
    float u_max = u_display_uv_rect.z;
    float v_max = u_display_uv_rect.w;
    
    // 重新映射 UV
    vec2 uv;
    uv.x = u_min + (u_max - u_min) * v_texCoord0.x;
    uv.y = v_min + (v_max - v_min) * v_texCoord0.y;
    
    // 使用调整后的 UV 采样纹理
    o_fragColor = texture(u_texture, uv);
}

"####),
"shader/photo/launcher/original/original_vert.glsl" => Some(r####"#version 300 es

in vec4 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord0;

void main() {
    v_texCoord0 = a_texCoord0;
    gl_Position = a_position;
}

"####),
"shader/photo/lock/curveGlass/curve_glass_frag.glsl" => Some(r####"#version 300 es

precision highp float;

uniform sampler2D u_lockTex; // 锁屏纹理
uniform vec2 u_offsetStrength; // 纹理偏移强度(水平和竖直两个方向)
uniform float u_lineFrequency; // 单条曲线频率
uniform float u_lineAmplitude; // 单条曲线振幅
uniform float u_lineWidth; // 单条曲线宽度
uniform float u_lineOffset; // 曲线偏移(两条曲线的左边间隔)
uniform float u_maskThreshold; // 遮罩阈值
uniform vec4 u_display_uv_rect; // 显示区域 UV 坐标 (left, top, right, bottom)

in vec2 v_lockUV; // 锁屏纹理的uv坐标
in vec2 v_lineUV; // 曲线纹理的uv坐标
in vec2 v_maskUV; // 遮罩纹理的uv坐标

out vec4 o_FragColor;

// 将 UV 映射到显示区域
vec2 mapUVToDisplayRect(vec2 uv) {
    vec2 mappedUV;
    mappedUV.x = u_display_uv_rect.x + uv.x * (u_display_uv_rect.z - u_display_uv_rect.x);
    mappedUV.y = u_display_uv_rect.y + uv.y * (u_display_uv_rect.w - u_display_uv_rect.y);
    return mappedUV;
}

float lineFun(float x) { // 灰度图曲线函数
    return u_lineAmplitude * sin(x * u_lineFrequency) + u_lineAmplitude;
}

float maskFun(float x) { // 遮罩图曲线函数
    return u_lineAmplitude * sin(x * u_lineFrequency);
}

float interpolation(float x) { // 灰度插值
    return pow(x, 3.0);
}

float getGray() { // 获取uv坐标对应的灰度
    float x = v_lineUV.x - (0.5 - u_lineAmplitude - u_lineWidth * 0.5);
    float f = lineFun(v_lineUV.y);
    float m = mod(x, u_lineOffset);
    float diff = m - f;
    for (int i = 0; i < 10; i++) {
        if (diff > u_lineWidth) {
            return -1.0;
        } else if (diff < 0.0) {
            diff += u_lineOffset;
        } else {
            return interpolation(diff / u_lineWidth);
        }
    }
    return interpolation(diff / u_lineWidth);
}

float getG(float gray, float maskOffset) { // 获取灰度的变异参数
    float gap = 0.01;
    if (maskOffset > gap) {
        return abs(gray - 0.5) * 2.0;
    }
    return abs(maskOffset - gap * 0.5);
}

vec2 getOffset(float gray, float maskOffset) { // 获取uv坐标对应的水平和竖直方向的偏移
    float g = getG(gray, maskOffset);
    float offsetX = 1.0 - pow(g, 0.5);
    offsetX = (offsetX - 0.5) * 2.0;
    return vec2(offsetX, -offsetX);
}

vec2 getLockUV(vec2 offset) { // 获取锁屏纹理坐标
    vec2 uv = v_lockUV + offset * u_offsetStrength;
    uv.x = clamp(uv.x, 0.0, 1.0);
    uv.y = clamp(uv.y, 0.0, 1.0);
    return mapUVToDisplayRect(uv);
}

float getMask() { // 获取uv坐标对应的灰度图
    float f = maskFun(v_maskUV.y);
    float x = v_maskUV.x - 0.5;
    float dist = abs(x - f);
    float maxDist = 0.5 + u_lineAmplitude;
    float mask = clamp(1.0 - dist / maxDist, 0.0, 1.0);
    return mask;
}

void main() {
    float mask = getMask();
    if (mask > u_maskThreshold) {
        float gray = getGray();
        float maskOffset = abs(mask - u_maskThreshold);
        if (gray < 0.0 && maskOffset > 0.01) {
            vec2 uv = mapUVToDisplayRect(v_lockUV);
            vec3 lockTex = texture(u_lockTex, uv).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        } else {
            vec2 offset = getOffset(gray, maskOffset);
            vec2 lockUV = getLockUV(offset);
            vec3 lockTex = texture(u_lockTex, lockUV).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        }
    } else {
        vec2 uv = mapUVToDisplayRect(v_lockUV);
        vec3 lockTex = texture(u_lockTex, uv).rgb;
        o_FragColor = vec4(lockTex, 1.0);
    }
}"####),
"shader/photo/lock/curveGlass/curve_glass_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform float u_tiling; // 锁屏纹理的缩放

out vec2 v_lockUV; // 锁屏纹理的uv坐标
out vec2 v_lineUV; // 曲线纹理的uv坐标
out vec2 v_maskUV; // 遮罩纹理的uv坐标

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_lockUV = a_texCoord0 * u_tiling + (1.0 - u_tiling) * 0.5;
    v_lineUV = a_texCoord0;
    v_maskUV = a_texCoord0;
}"####),
"shader/photo/lock/fillMask/fill_mask_frag.glsl" => Some(r####"#version 300 es

precision highp float;

uniform sampler2D u_texture; // 纹理
uniform vec3 u_color; // 背景颜色
uniform vec4 u_display_uv_rect; // 显示区域 UV 坐标 (left, top, right, bottom)

in vec2 v_texCood; // 纹理的uv坐标

out vec4 o_FragColor;

// 将 UV 映射到显示区域
vec2 mapUVToDisplayRect(vec2 uv) {
    vec2 mappedUV;
    mappedUV.x = u_display_uv_rect.x + uv.x * (u_display_uv_rect.z - u_display_uv_rect.x);
    mappedUV.y = u_display_uv_rect.y + uv.y * (u_display_uv_rect.w - u_display_uv_rect.y);
    return mappedUV;
}

void main() {
    vec2 uv = mapUVToDisplayRect(v_texCood);
    // 当 top 为负值时，如果 UV.y < 0，直接使用背景色，防止 mask 图向上延伸
    if (uv.y < 0.0) {
        o_FragColor = vec4(u_color, 1.0);
        return;
    }
    vec4 texColor = texture(u_texture, uv);
    vec3 color = mix(u_color, texColor.rgb, texColor.a);
    o_FragColor = vec4(color, 1.0);
}"####),
"shader/photo/lock/fillMask/fill_mask_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_texCood; // 纹理的uv坐标

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_texCood = a_texCoord0;
}"####),
"shader/photo/lock/groundGlass/ground_glass_frag.glsl" => Some(r####"#version 300 es

precision highp float;

uniform sampler2D u_lockTex; // 锁屏纹理
uniform sampler2D u_grayTex; // 灰度纹理
uniform sampler2D u_maskTex; // 遮罩纹理
uniform float u_minGray; // 最小灰度
uniform float u_maskThreshold; // 遮罩阈值
uniform vec2 u_dudv; // 灰度纹理的du、dv
uniform vec4 u_display_uv_rect; // 显示区域 UV 坐标 (left, top, right, bottom)

in vec2 v_lockUV; // 锁屏纹理的uv坐标
in vec2 v_grayUV; // 灰度纹理的uv坐标
in vec2 v_maskUV; // 遮罩纹理的uv坐标

out vec4 o_FragColor;

// 将 UV 映射到显示区域
vec2 mapUVToDisplayRect(vec2 uv) {
    vec2 mappedUV;
    mappedUV.x = u_display_uv_rect.x + uv.x * (u_display_uv_rect.z - u_display_uv_rect.x);
    mappedUV.y = u_display_uv_rect.y + uv.y * (u_display_uv_rect.w - u_display_uv_rect.y);
    return mappedUV;
}

float getGray(vec2 uv) {
    float gray = texture(u_grayTex, uv).r;
    return max(gray, u_minGray);
}

vec2 getDelta() {
    float gray1 = getGray(v_grayUV);
    float gray2 = getGray(vec2(v_grayUV.x - u_dudv.x, v_grayUV.y));
    float gray3 = getGray(vec2(v_grayUV.x, v_grayUV.y - u_dudv.y));
    float dx = gray2 - gray1;
    float dy = gray3 - gray1;
    return vec2(dx, dy);
}

vec2 getOffset() {
    vec2 delta = getDelta();
    return delta;
}

vec2 getLockUV(vec2 offset) { // 获取锁屏纹理坐标
    vec2 uv = v_lockUV + offset * 0.1;
    uv.x = clamp(uv.x, 0.0, 1.0);
    uv.y = clamp(uv.y, 0.0, 1.0);
    return mapUVToDisplayRect(uv);
}

float getMask() {
    return texture(u_maskTex, v_maskUV).r;
}

void main() {
    float mask = getMask();
    if (mask > u_maskThreshold) {
        float maskOffset = abs(mask - u_maskThreshold);
        if (maskOffset < 0.05) {
            vec2 uv = mapUVToDisplayRect(v_lockUV);
            vec3 lockTex = texture(u_lockTex, uv).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        } else {
            vec2 offset = getOffset();
            vec2 lockUV = getLockUV(offset);
            vec3 lockTex = texture(u_lockTex, lockUV).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        }
    } else {
        vec2 uv = mapUVToDisplayRect(v_lockUV);
        vec3 lockTex = texture(u_lockTex, uv).rgb;
        o_FragColor = vec4(lockTex, 1.0);
    }
}"####),
"shader/photo/lock/groundGlass/ground_glass_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform vec2 u_maskOffset; // 遮罩偏移

out vec2 v_lockUV; // 锁屏纹理的uv坐标
out vec2 v_grayUV; // 灰度纹理的uv坐标
out vec2 v_maskUV; // 遮罩纹理的uv坐标

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_lockUV = a_texCoord0;
    v_grayUV = a_texCoord0;
    v_maskUV = a_texCoord0 * vec2(0.5, 0.88) + u_maskOffset;
}"####),
"shader/photo/lock/original/original_frag.glsl" => Some(r####"#version 300 es

precision highp float;

uniform sampler2D u_texture;
uniform vec4 u_display_uv_rect; // (left, top, right, bottom) 显示区域的 UV 坐标

in vec2 v_texCoord0;

out vec4 o_fragColor;

void main() {
    // 将 UV 坐标映射到显示区域
    float u_min = u_display_uv_rect.x;
    float v_min = u_display_uv_rect.y;
    float u_max = u_display_uv_rect.z;
    float v_max = u_display_uv_rect.w;
    
    // 重新映射 UV
    vec2 uv;
    uv.x = u_min + (u_max - u_min) * v_texCoord0.x;
    uv.y = v_min + (v_max - v_min) * v_texCoord0.y;
    
    // 使用调整后的 UV 采样纹理
    o_fragColor = texture(u_texture, uv);
}

"####),
"shader/photo/lock/original/original_vert.glsl" => Some(r####"#version 300 es

in vec4 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord0;

void main() {
    v_texCoord0 = a_texCoord0;
    gl_Position = a_position;
}

"####),
"shader/photo/lock/straightGlass/straight_glass_frag.glsl" => Some(r####"#version 300 es

precision highp float;

uniform sampler2D u_lockTex; // 锁屏纹理
uniform vec2 u_offsetStrength; // 纹理偏移强度(水平和竖直两个方向)
uniform float u_lineWidth; // 单条曲线宽度
uniform float u_lineOffset; // 曲线偏移(两条曲线的左边间隔)
uniform float u_maskThreshold; // 遮罩阈值
uniform vec4 u_display_uv_rect; // 显示区域 UV 坐标 (left, top, right, bottom)

in vec2 v_lockUV; // 锁屏纹理的uv坐标
in vec2 v_lineUV; // 曲线纹理的uv坐标
in vec2 v_maskUV; // 遮罩纹理的uv坐标

out vec4 o_FragColor;

// 将 UV 映射到显示区域
vec2 mapUVToDisplayRect(vec2 uv) {
    vec2 mappedUV;
    mappedUV.x = u_display_uv_rect.x + uv.x * (u_display_uv_rect.z - u_display_uv_rect.x);
    mappedUV.y = u_display_uv_rect.y + uv.y * (u_display_uv_rect.w - u_display_uv_rect.y);
    return mappedUV;
}

float interpolation(float x) { // 灰度插值
    return pow(x, 3.0);
}

float getGray() { // 获取uv坐标对应的灰度
    float x = v_lineUV.x - (0.5 - u_lineWidth * 0.5);
    float diff = mod(x, u_lineOffset);
    for (int i = 0; i < 10; i++) {
        if (diff > u_lineWidth) {
            return -1.0;
        } else if (diff < 0.0) {
            diff += u_lineOffset;
        } else {
            return interpolation(diff / u_lineWidth);
        }
    }
    return interpolation(diff / u_lineWidth);
}

float getG(float gray, float maskOffset) { // 获取灰度的变异参数
    float gap = 0.01;
    if (maskOffset > gap) {
        float a = 0.7;
        if (abs(gray - a) < 0.07) {
            return abs(gray - a);
        }
        if (gray < a) {
            return abs(gray - a) / a;
        } else {
            return abs(gray - a) / (1.0 - a);
        }
    }
    return abs(maskOffset - gap * 0.5);
}

vec2 getOffset(float gray, float maskOffset) { // 获取uv坐标对应的水平和竖直方向的偏移
    float g = getG(gray, maskOffset);
    float offsetX = 1.0 - pow(g, 0.5);
    offsetX = (offsetX - 0.5) * 2.0;
    return vec2(offsetX, 0.0);
}

vec2 getLockUV(vec2 offset) { // 获取锁屏纹理坐标
    vec2 uv = v_lockUV + offset * u_offsetStrength;
    uv.x = clamp(uv.x, 0.0, 1.0);
    uv.y = clamp(uv.y, 0.0, 1.0);
    return mapUVToDisplayRect(uv);
}

float getMask() { // 获取uv坐标对应的灰度图
    float dist = abs(v_maskUV.x - 0.5);
    float mask = clamp(1.0 - dist * 2.0, 0.0, 1.0);
    return mask;
}

void main() {
    float mask = getMask();
    if (mask > u_maskThreshold) {
        float gray = getGray();
        float maskOffset = abs(mask - u_maskThreshold);
        if (gray < 0.0 && maskOffset > 0.01) {
            vec2 uv = mapUVToDisplayRect(v_lockUV);
            vec3 lockTex = texture(u_lockTex, uv).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        } else {
            vec2 offset = getOffset(gray, maskOffset);
            vec2 lockUV = getLockUV(offset);
            vec3 lockTex = texture(u_lockTex, lockUV).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        }
    } else {
        vec2 uv = mapUVToDisplayRect(v_lockUV);
        vec3 lockTex = texture(u_lockTex, uv).rgb;
        o_FragColor = vec4(lockTex, 1.0);
    }
}"####),
"shader/photo/lock/straightGlass/straight_glass_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform float u_tiling; // 锁屏纹理的缩放

out vec2 v_lockUV; // 锁屏纹理的uv坐标
out vec2 v_lineUV; // 曲线纹理的uv坐标
out vec2 v_maskUV; // 遮罩纹理的uv坐标

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_lockUV = a_texCoord0 * u_tiling + (1.0 - u_tiling) * 0.5;
    v_lineUV = a_texCoord0;
    v_maskUV = a_texCoord0;
}"####),
"shader/text/text_frag.glsl" => Some(r####"#version 300 es
precision highp float;

in vec2 v_uv;
uniform sampler2D u_text_bitmap;
out vec4 fragColor;

void main () {
    fragColor = texture(u_text_bitmap, v_uv);
}"####),
"shader/text/text_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform vec2 u_scale;
uniform vec3 u_translation;

out vec2 v_uv;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position * vec3(u_scale, 1.0) + u_translation, 1.0);
    v_uv = vec2(a_texCoord0.x, 1.0 - a_texCoord0.y);
}"####),
"shader/utils/dark_utils.glsl" => Some(r####"precision highp float;

#define DARK_BRIGHTNESS -0.3f // 亮度最大变化量
#define DARK_CONTRAST 0.15f // 对比度最大变化量

const vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}"####),
"shader/v1/music_frag.glsl" => Some(r####"#version 300 es

precision highp float;

#define DARK_BRIGHTNESS -0.3f // 亮度最大变化量
#define DARK_CONTRAST 0.15f // 对比度最大变化量

const vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}

precision highp float;

const int LINE_NUM = 4; // 曲线条数
const int COLOR_NUM = 6; // 颜色个数

in vec2 v_originTexCoord;
in vec2 v_texCoord;

uniform vec3 u_colorCenter; // 三个颜色的中心位置
uniform vec3 u_colors[COLOR_NUM]; // 6个渐变色

uniform vec4 u_bessel[LINE_NUM]; // 贝塞尔曲线的参数(1、4控制起点和终点位置, 2、3控制起点和终点斜率)
uniform float u_width[LINE_NUM]; // 曲线宽度
uniform float u_darkStrength; // 深色模式强度

out vec4 o_fragColor;

float fun(float t, vec4 bessel) { // 贝塞尔函数方程
    float t2 = t * t;
    float t3 = t2 * t;
    float at = 1.0 - t;
    float at2 = at * at;
    float at3 = at2 * at;
    return at3 * bessel.x + 3.0 * t * at2 * bessel.y + 3.0 * t2 * at * bessel.z + t3 * bessel.w;
}

vec3 getFactor(int i, float u) { // 计算三个颜色对应的权重因子, i: 曲线编号, u: 纹理坐标的u分量
    float step = 1.0 / float(LINE_NUM);
    float start = float(i) * step;
    float x = 0.0;
    float ratio = fun(u, vec4(0.0, 0.0, 1.0, 1.0));
    if (i % 2 == 0) {
        //x = start + u * step;
        x = start + ratio * step;
    } else {
        //x = start + (1.0 - u) * step;
        x = start + (1.0 - ratio) * step;
    }
    vec3 factor = abs(x - u_colorCenter);
    factor = min(factor, 1.0 - factor);
    vec3 temp = vec3(factor);
    if (factor.x > factor.y && factor.x > factor.z) {
        float sum = factor.y + factor.z;
        factor.x = 0.0;
        float ratio = fun(temp.z / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.y = ratio;
        factor.z = 1.0 - ratio;
    } else if (factor.y > factor.x && factor.y > factor.z) {
        float sum = factor.x + factor.z;
        factor.y = 0.0;
        float ratio = fun(temp.z / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.x = ratio;
        factor.z = 1.0 - ratio;
    } else if (factor.z > factor.x && factor.z > factor.y) {
        float sum = factor.x + factor.y;
        factor.z = 0.0;
        float ratio = fun(temp.y / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.x = ratio;
        factor.y = 1.0 - ratio;
    } else {
        float ratio = 1.0 / 3.0;
        factor.x = ratio;
        factor.y = ratio;
        factor.z = ratio;
    }
    return factor;
}

vec3 dithering(vec3 color) { // 解决色阶问题
    float noise = (fract(sin(dot(v_originTexCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    return color + noise;
}

vec3 getColor(float dist, float width, vec3 factor) {
    float ratio = 1.0 - dist / width;
    ratio = pow(ratio, 6.0);
    vec3 color1 = mix(u_colors[0], u_colors[1], ratio);
    vec3 color2 = mix(u_colors[2], u_colors[3], ratio);
    vec3 color3 = mix(u_colors[4], u_colors[5], ratio);

    //vec3 color1 = u_colors[1];
    //vec3 color2 = u_colors[3];
    //vec3 color3 = u_colors[5];

    vec3 color = factor.x * color1 + factor.y * color2 + factor.z * color3;
    color = dithering(color);
    color = applyDarkIfNeed(vec4(color, 1.0), u_darkStrength).rgb;
    return color;
}

vec3 getBackColor(vec2 uv) {
    float step = 1.0 / float(LINE_NUM);
    float halfStep = step * 0.5;
    vec4 bessel = vec4(-0.05, -0.05, 0.05, 0.05) + halfStep;
    float width = halfStep;
    for (int i = LINE_NUM - 1; i >= 0; i--) {
        float add = float(i) * step;
        float y = fun(uv.x, bessel + add);
        float dist = abs(y - uv.y);
        if (y < 0.0 + width) {
            float dist2 = abs(y + 1.0 - uv.y);
            if (dist2 < dist) {
                y = y + 1.0;
                dist = dist2;
            }
        } else if (y > 1.0 - width) {
            float dist2 = abs(y - 1.0 - uv.y);
            if (dist2 < dist) {
                y = y - 1.0;
                dist = dist2;
            }
        }
        if (dist < width) {
            vec3 factor = getFactor(i, uv.x);
            vec3 color = getColor(uv.y - y + width,  2.0 * width, factor);
            return color;
        }
    }
    return vec3(0.0, 0.0, 0.0);
}

void main() {
    float f[LINE_NUM]; // 贝塞尔函数值
    int flag[LINE_NUM]; // 是否初始化标记
    for (int i = 0; i < LINE_NUM; i++) {
        flag[i] = 0;
    }

    // 水平镜像翻转uv
    vec2 uv = v_texCoord;
    //uv.x = mod(uv.x + 10.0, 2.0);
    //uv.x = 1.0 - abs(uv.x - 1.0);
    uv.y = mod(uv.y + 10.0, 1.0);

    // 解决顶部曲线向上越界渲染异常问题(顶部曲线本应该最后渲染, 但越界后, 变成底部了, 所以应该最先渲染)
    for (int i = 1; i >= 0; i--) {
        f[i] = fun(uv.x, u_bessel[i]);
        flag[i] = 1;
        if (f[i] < 0.0 + u_width[i]) {
            float v = uv.y - 1.0;
            float dist = abs(f[i] - v);
            if (dist < u_width[i]) {
                vec3 factor = getFactor(i, uv.x);
                vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
                o_fragColor = vec4(color, 1.0);
                return;
            }
        }
    }

    for (int i = LINE_NUM - 1; i >= 0; i--) {
        if (flag[i] == 0) {
            f[i] = fun(uv.x, u_bessel[i]);
            flag[i] = 1;
        }
        float v = uv.y;
        float dist = abs(f[i] - v);
        if (dist < u_width[i]) {
            vec3 factor = getFactor(i, uv.x);
            vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
            o_fragColor = vec4(color, 1.0);
            return;
        }
    }

    // 解决底部曲线向下越界渲染异常问题(底部曲线本应该最先渲染, 但越界后, 变成顶部了, 所以应该最后渲染)
    for (int i = LINE_NUM - 1; i >= LINE_NUM - 2; i--) {
        if (flag[i] == 0) {
            f[i] = fun(uv.x, u_bessel[i]);
            flag[i] = 1;
        }
        if (f[i] > 1.0 - u_width[i]) {
            float v = uv.y + 1.0;
            float dist = abs(f[i] - v);
            if (dist < u_width[i]) {
                vec3 factor = getFactor(i, uv.x);
                vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
                o_fragColor = vec4(color, 1.0);
                return;
            }
        }
    }

    o_fragColor = vec4(getBackColor(uv), 1.0);
}"####),
"shader/v1/music_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform float u_scaleX; // 水平缩放
uniform float u_scale; // 缩放
uniform vec2 u_translate; // 平移

out vec2 v_originTexCoord;
out vec2 v_texCoord;

vec2 uvTransform(vec2 uv) {
    float minScale = min(u_scale, 1.0 / u_scaleX);
    vec2 scale = vec2(minScale * u_scaleX, minScale);
    //float translateEdgeX = (1.0 - scale.x) * 0.5;
    //float translateX = clamp(u_translate.x, -translateEdgeX, translateEdgeX);
    float translateX = mod(u_translate.x, 2.0);
    float translateY = mod(u_translate.y, 1.0);
    vec2 uv1 = uv * scale + (1.0 - scale) * 0.5 + vec2(translateX, translateY);
    return uv1;
}

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_originTexCoord = a_texCoord0;
    v_texCoord = uvTransform(a_texCoord0);
}"####),
"shader/v2/music_frag.glsl" => Some(r####"#version 300 es

precision highp float;

#define DARK_BRIGHTNESS -0.3f // 亮度最大变化量
#define DARK_CONTRAST 0.15f // 对比度最大变化量

const vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}

precision highp float;

const int LINE_NUM = 5; // 曲线条数
const int COLOR_NUM = 3; // 颜色个数

in vec2 v_originTexCoord;
in vec2 v_texCoord;

uniform vec3 u_colorCenter; // 三个颜色的中心位置
uniform vec3 u_colors[COLOR_NUM]; // 6个渐变色

uniform vec4 u_bessel[LINE_NUM]; // 贝塞尔曲线的参数(1、4控制起点和终点位置, 2、3控制起点和终点斜率)
uniform float u_width[LINE_NUM]; // 曲线宽度
uniform float u_darkStrength; // 深色模式强度

out vec4 o_fragColor;

float fun(float t, vec4 bessel) { // 贝塞尔函数方程
    float t2 = t * t;
    float t3 = t2 * t;
    float at = 1.0 - t;
    float at2 = at * at;
    float at3 = at2 * at;
    return at3 * bessel.x + 3.0 * t * at2 * bessel.y + 3.0 * t2 * at * bessel.z + t3 * bessel.w;
}

vec3 getFactor(int i, float u) { // 计算三个颜色对应的权重因子, i: 曲线编号, u: 纹理坐标的u分量
    float step = 1.0 / float(LINE_NUM);
    float start = float(i) * step;
    float x = 0.0;
    float ratio = fun(u, vec4(0.0, 0.0, 1.0, 1.0));
    if (i % 2 == 0) {
        //x = start + u * step;
        x = start + ratio * step;
    } else {
        //x = start + (1.0 - u) * step;
        x = start + (1.0 - ratio) * step;
    }
    vec3 factor = abs(x - u_colorCenter);
    factor = min(factor, 1.0 - factor);
    vec3 temp = vec3(factor);
    if (factor.x > factor.y && factor.x > factor.z) {
        float sum = factor.y + factor.z;
        factor.x = 0.0;
        float ratio = fun(temp.z / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.y = ratio;
        factor.z = 1.0 - ratio;
    } else if (factor.y > factor.x && factor.y > factor.z) {
        float sum = factor.x + factor.z;
        factor.y = 0.0;
        float ratio = fun(temp.z / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.x = ratio;
        factor.z = 1.0 - ratio;
    } else if (factor.z > factor.x && factor.z > factor.y) {
        float sum = factor.x + factor.y;
        factor.z = 0.0;
        float ratio = fun(temp.y / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.x = ratio;
        factor.y = 1.0 - ratio;
    } else {
        float ratio = 1.0 / 3.0;
        factor.x = ratio;
        factor.y = ratio;
        factor.z = ratio;
    }
    return factor;
}

vec3 dithering(vec3 color) { // 解决色阶问题
    float noise = (fract(sin(dot(v_originTexCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    return color + noise;
}

vec3 getColor(float dist, float width, vec3 factor) {
    vec3 color = factor.x * u_colors[0] + factor.y * u_colors[1] + factor.z * u_colors[2];
    color = dithering(color);
    color = applyDarkIfNeed(vec4(color, 1.0), u_darkStrength).rgb;
    return color;
}

vec3 getBackColor(vec2 uv) {
    float step = 1.0 / float(LINE_NUM);
    float halfStep = step * 0.5;
    vec4 bessel = vec4(-0.05, -0.05, 0.05, 0.05) + halfStep;
    float width = halfStep;
    for (int i = LINE_NUM - 1; i >= 0; i--) {
        float add = float(i) * step;
        float y = fun(uv.x, bessel + add);
        float dist = abs(y - uv.y);
        if (y < 0.0 + width) {
            float dist2 = abs(y + 1.0 - uv.y);
            if (dist2 < dist) {
                y = y + 1.0;
                dist = dist2;
            }
        } else if (y > 1.0 - width) {
            float dist2 = abs(y - 1.0 - uv.y);
            if (dist2 < dist) {
                y = y - 1.0;
                dist = dist2;
            }
        }
        if (dist < width) {
            vec3 factor = getFactor(i, uv.x);
            vec3 color = getColor(uv.y - y + width,  2.0 * width, factor);
            return color;
        }
    }
    return vec3(0.0, 0.0, 0.0);
}

void main() {
    float f[LINE_NUM]; // 贝塞尔函数值
    int flag[LINE_NUM]; // 是否初始化标记
    for (int i = 0; i < LINE_NUM; i++) {
        flag[i] = 0;
    }

    // 水平镜像翻转uv
    vec2 uv = v_texCoord;
    //uv.x = mod(uv.x + 10.0, 2.0);
    //uv.x = 1.0 - abs(uv.x - 1.0);
    uv.y = mod(uv.y + 10.0, 1.0);

    // 解决顶部曲线向上越界渲染异常问题(顶部曲线本应该最后渲染, 但越界后, 变成底部了, 所以应该最先渲染)
    for (int i = 1; i >= 0; i--) {
        f[i] = fun(uv.x, u_bessel[i]);
        flag[i] = 1;
        if (f[i] < 0.0 + u_width[i]) {
            float v = uv.y - 1.0;
            float dist = abs(f[i] - v);
            if (dist < u_width[i]) {
                vec3 factor = getFactor(i, uv.x);
                vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
                o_fragColor = vec4(color, 1.0);
                return;
            }
        }
    }

    for (int i = LINE_NUM - 1; i >= 0; i--) {
        if (flag[i] == 0) {
            f[i] = fun(uv.x, u_bessel[i]);
            flag[i] = 1;
        }
        float v = uv.y;
        float dist = abs(f[i] - v);
        if (dist < u_width[i]) {
            vec3 factor = getFactor(i, uv.x);
            vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
            o_fragColor = vec4(color, 1.0);
            return;
        }
    }

    // 解决底部曲线向下越界渲染异常问题(底部曲线本应该最先渲染, 但越界后, 变成顶部了, 所以应该最后渲染)
    for (int i = LINE_NUM - 1; i >= LINE_NUM - 2; i--) {
        if (flag[i] == 0) {
            f[i] = fun(uv.x, u_bessel[i]);
            flag[i] = 1;
        }
        if (f[i] > 1.0 - u_width[i]) {
            float v = uv.y + 1.0;
            float dist = abs(f[i] - v);
            if (dist < u_width[i]) {
                vec3 factor = getFactor(i, uv.x);
                vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
                o_fragColor = vec4(color, 1.0);
                return;
            }
        }
    }

    o_fragColor = vec4(getBackColor(uv), 1.0);
}"####),
"shader/v2/music_vertex.glsl" => Some(r####"#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform float u_scaleX; // 水平缩放
uniform float u_scale; // 缩放
uniform vec2 u_translate; // 平移

out vec2 v_originTexCoord;
out vec2 v_texCoord;

vec2 uvTransform(vec2 uv) {
    float minScale = min(u_scale, 1.0 / u_scaleX);
    vec2 scale = vec2(minScale * u_scaleX, minScale);
    //float translateEdgeX = (1.0 - scale.x) * 0.5;
    //float translateX = clamp(u_translate.x, -translateEdgeX, translateEdgeX);
    float translateX = mod(u_translate.x, 2.0);
    float translateY = mod(u_translate.y, 1.0);
    vec2 uv1 = uv * scale + (1.0 - scale) * 0.5 + vec2(translateX, translateY);
    return uv1;
}

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_originTexCoord = a_texCoord0;
    v_texCoord = uvTransform(a_texCoord0);
}"####),
"video/video_frag.glsl" => Some(r####"#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}

out vec4 fragColor;
in vec2 v_texCoord;

uniform samplerExternalOES u_texture;
uniform float u_dark_strength;

void main () {
    vec4 color = texture(u_texture, v_texCoord);
    fragColor = applyDarkIfNeed(color, u_dark_strength);
}

"####),
"video/video_vertex.glsl" => Some(r####"#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_transform_matrix;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position, 1);
    vec4 transformTexCoord = (u_transform_matrix * vec4(a_texCoord0, 0, 1));
    v_texCoord = vec2(transformTexCoord.x, transformTexCoord.y);
}"####),
_ => None,
} }