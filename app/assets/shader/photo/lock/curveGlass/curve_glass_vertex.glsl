#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform float u_tiling; // 锁屏纹理的缩放

out vec2 v_lockUV; // 锁屏纹理的uv坐标
out vec2 v_lineUV; // 曲线纹理的uv坐标
out vec2 v_maskUV; // 遮罩纹理的uv坐标

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_lockUV = a_texCoord0 * u_tiling + (1.0 - u_tiling) * 0.5;
    v_lineUV = a_texCoord0;
    v_maskUV = a_texCoord0;
}