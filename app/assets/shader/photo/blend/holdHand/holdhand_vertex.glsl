#version 300 es

// AlivePhoto引擎跟手顶点着色器(#1293213)

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;

out vec2 v_texCoord0;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_texCoord0 = a_texCoord0;
}