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
}