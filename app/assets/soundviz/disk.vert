#version 300 es
#define PI 3.1415926535897932384626433832795
#define TWO_PI 2. * PI

precision highp float;

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projTrans;
uniform mat4 u_transform;
uniform float u_time;
uniform float u_seed;
uniform float u_phase;

uniform vec4 u_color_a_outer;
uniform vec4 u_color_a_cp1;
uniform vec4 u_color_a_cp2;
uniform vec4 u_color_a_cp3;
uniform vec4 u_color_a_cp4;
uniform vec4 u_color_a_inner;

uniform vec4 u_color_b_outer;
uniform vec4 u_color_b_cp1;
uniform vec4 u_color_b_cp2;
uniform vec4 u_color_b_cp3;
uniform vec4 u_color_b_cp4;
uniform vec4 u_color_b_inner;

out vec4 v_color_outer;
out vec4 v_color_1;
out vec4 v_color_2;
out vec4 v_color_3;
out vec4 v_color_4;
out vec4 v_color_inner;
out highp vec2 v_uv;
out highp vec4 v_pos_grad;

vec3 permute(vec3 x) {
    return mod(((x*34.0)+1.0)*x, 289.0);
}

vec2 random(vec2 p){
    return -1.0 + 2.0 * fract(sin(vec2(dot(p, vec2(1527.1, 3711.7)), dot(p, vec2(2629.5, 1853.3)))) * 437658.5453);
}

float noise_perlin (vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = dot(random(i) + sin(u_time * 0.3 + 4.565), f);
    float b = dot(random(i + vec2(1.0, 0.0)) + cos(u_time * 0.5 + 9.335), f - vec2(1.0, 0.0));
    float c = dot(random(i + vec2(0.0, 1.0)) + sin(u_time * 0.2 + 4.86535), f - vec2(0.0, 1.0));
    float d = dot(random(i + vec2(1.0, 1.0)) + cos(u_time * 0.3 + 1.45466), f - vec2(1.0, 1.0));
    vec2 u = smoothstep(0.0, 1.0, f);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865405187, 0.366025403784439,
    -0.577350269189626, 0.024390243902439);
    vec2 i  = floor(v + dot(v, C.yy));
    vec2 x0 = v -   i + dot(i, C.xx);
    vec2 i1;
    i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod(i, 289.0);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0))
    + i.x + vec3(0.0, i1.x, 1.0));
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy),
    dot(x12.zw, x12.zw)), 0.0);
    m = m*m;
    m = m*m;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0*a0 + h*h);
    vec3 g;
    g.x  = a0.x  * x0.x  + h.x  * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

float sinNormalized(float rad, float size) {
    float halfSize = size * 0.5;
    return (halfSize + halfSize * sin(rad * TWO_PI));
}

void updateGradientPoints(in vec2 uv, in float time, out vec4 posGrad) {
    float time_1 = time * 0.15f;
    float time_2 = time * 0.057f + 352.3;
    float time_3 = time * 0.08f + 35.8;

    float noise_1 = snoise(vec2(uv.x * 1.0f + time_1, 3.4 + u_seed));
    float noise_2 = snoise(vec2(uv.x * 2.743f, time_2 + u_seed));
    float noise_3 = snoise(vec2(uv.x * 2.8f + time_3, 74.8 + u_seed));

    float centerNoise = 0.1 * noise_1;
    float variation = centerNoise + 0.1 * sin((uv.x + centerNoise) * 1.0 * 1.45f * TWO_PI + u_phase);

    float centerGrad = 0.5 + variation;
    float innerAmpGrad = 0.2 * (1.6 + 1.5 * (sin((uv.x * 1.0 + time * 0.03 + 0.15 * 0.15f * noise_3) * 1.45f * 1.1 * TWO_PI)));
    float outerAmpGrad = 0.1 * (noise_2 * 0.5 + 0.5);

    innerAmpGrad = abs(innerAmpGrad);
    outerAmpGrad = abs(outerAmpGrad);

    posGrad.y = centerGrad * (1. - innerAmpGrad) * 0.8;
    posGrad.z = centerGrad + (1. - centerGrad) * innerAmpGrad;

    posGrad.x = max(posGrad.y - outerAmpGrad * 5.0, 0.01);
    posGrad.w = min(posGrad.z + outerAmpGrad * 5.0, 0.99);
}

void updateGradientColors(in vec2 uv, in vec4 posGrad, in float time, out vec4 colorInner, out vec4 color1, out vec4 color2, out vec4 color3, out vec4 color4, out vec4 colorOuter) {

    float scaledTime = time * 0.08f + 53.89;
    float colorVariation = sinNormalized(uv.x * 0.25f + scaledTime, 1.);

    float diff = abs(posGrad.z - posGrad.y);
    float opacity = mix(1.0, 0.3, smoothstep(0.0, 0.5, diff));

    colorInner = mix(u_color_a_inner, u_color_b_inner, colorVariation);
    color1 = mix(u_color_a_cp1, u_color_b_cp1, colorVariation);
    color2 = mix(u_color_a_cp2, u_color_b_cp2, colorVariation);
    color3 = mix(u_color_a_cp3, u_color_b_cp3, colorVariation);
    color4 = mix(u_color_a_cp4, u_color_b_cp4, colorVariation);
    colorOuter = mix(u_color_a_outer, u_color_b_outer, colorVariation);

    color3 = mix(colorOuter, color3, opacity);
    color4 = mix(colorOuter, color4, opacity);
}


void main()  {
    v_uv = a_texCoord0;
    v_uv.y = 1.0 - v_uv.y;
    updateGradientPoints(v_uv, u_time, v_pos_grad);
    updateGradientColors(v_uv, v_pos_grad, u_time, v_color_inner, v_color_1, v_color_2, v_color_3, v_color_4, v_color_outer);
    gl_Position = u_projTrans * u_transform * vec4(a_position, 1.0);
}