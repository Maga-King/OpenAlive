#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform vec2 u_maskOffset; // 遮罩偏移

out vec2 v_lockUV; // 锁屏纹理的uv坐标
out vec2 v_grayUV; // 灰度纹理的uv坐标
out vec2 v_maskUV; // 遮罩纹理的uv坐标

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_lockUV = a_texCoord0;
    v_grayUV = a_texCoord0;
    v_maskUV = a_texCoord0 * vec2(0.5, 0.88) + u_maskOffset;
}