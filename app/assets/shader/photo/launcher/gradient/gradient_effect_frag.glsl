#version 300 es
precision highp float;

#include <shader/base/noise_2d.glsl>

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
}