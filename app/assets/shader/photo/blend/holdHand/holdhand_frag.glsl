#version 300 es

// AlivePhoto引擎跟手片元着色器(#1293213)

precision highp float;

#include <shader/base/dark_utlis.glsl>

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
}