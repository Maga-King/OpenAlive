#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

#include <shader/base/dark_utlis.glsl>
#include <shader/base/blend_utils.glsl>

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
}