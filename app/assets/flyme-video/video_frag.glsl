#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

#include <shader/base/dark_utlis.glsl>

out vec4 fragColor;
in vec2 v_texCoord;

uniform samplerExternalOES u_texture;
uniform float u_dark_strength;

void main () {
    vec4 color = texture(u_texture, v_texCoord);
    fragColor = applyDarkIfNeed(color, u_dark_strength);
}

