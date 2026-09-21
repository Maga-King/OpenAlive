#version 320 es
precision highp float;

#include <shader/cosmic/cosmic_utils.glsl>

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
