#version 320 es

in vec3 a_position;
in vec2 a_texCoord0;
uniform mat4 u_projectionViewMatrix;
out vec2 v_uv;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position, 1.0);
    v_uv = vec2(a_texCoord0.x, 1.0 - a_texCoord0.y);
}