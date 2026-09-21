#version 300 es

precision highp float;

uniform sampler2D u_texture; // 纹理
uniform vec3 u_color; // 背景颜色
uniform vec4 u_display_uv_rect; // 显示区域 UV 坐标 (left, top, right, bottom)

in vec2 v_texCood; // 纹理的uv坐标

out vec4 o_FragColor;

// 将 UV 映射到显示区域
vec2 mapUVToDisplayRect(vec2 uv) {
    vec2 mappedUV;
    mappedUV.x = u_display_uv_rect.x + uv.x * (u_display_uv_rect.z - u_display_uv_rect.x);
    mappedUV.y = u_display_uv_rect.y + uv.y * (u_display_uv_rect.w - u_display_uv_rect.y);
    return mappedUV;
}

void main() {
    vec2 uv = mapUVToDisplayRect(v_texCood);
    // 当 top 为负值时，如果 UV.y < 0，直接使用背景色，防止 mask 图向上延伸
    if (uv.y < 0.0) {
        o_FragColor = vec4(u_color, 1.0);
        return;
    }
    vec4 texColor = texture(u_texture, uv);
    vec3 color = mix(u_color, texColor.rgb, texColor.a);
    o_FragColor = vec4(color, 1.0);
}