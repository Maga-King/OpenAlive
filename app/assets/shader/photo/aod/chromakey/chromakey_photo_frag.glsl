#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

#include <shader/base/dark_utlis.glsl>
#include <shader/base/blend_utils.glsl>

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

