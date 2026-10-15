#version 300 es


precision highp float;

#include <flyme-video/dark_utlis.glsl>

out vec4 fragColor;
in vec2 v_texCoord;

uniform sampler2D u_texture;
uniform float u_dark_strength;

void main () {
    vec4 color = texture(u_texture, v_texCoord);
    fragColor = applyDarkIfNeed(color, u_dark_strength);
}

