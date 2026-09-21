#version 300 es

precision highp float;

uniform sampler2D u_texture;
uniform vec4 u_display_uv_rect; // (left, top, right, bottom) 显示区域的 UV 坐标

in vec2 v_texCoord0;

out vec4 o_fragColor;

void main() {
    // 将 UV 坐标映射到显示区域
    float u_min = u_display_uv_rect.x;
    float v_min = u_display_uv_rect.y;
    float u_max = u_display_uv_rect.z;
    float v_max = u_display_uv_rect.w;
    
    // 重新映射 UV
    vec2 uv;
    uv.x = u_min + (u_max - u_min) * v_texCoord0.x;
    uv.y = v_min + (v_max - v_min) * v_texCoord0.y;
    
    // 使用调整后的 UV 采样纹理
    o_fragColor = texture(u_texture, uv);
}

