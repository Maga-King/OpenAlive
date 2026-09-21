#version 300 es

precision highp float;

uniform sampler2D u_texture; // 模糊后的纹理

in vec2 v_texCoord0;

out vec4 o_fragColor;

void main() {
    // 直接采样模糊后的纹理（已经是完整的 [0, 1] 范围）
    o_fragColor = texture(u_texture, v_texCoord0);
}

