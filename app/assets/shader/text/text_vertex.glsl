#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform vec2 u_scale;
uniform vec3 u_translation;

out vec2 v_uv;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position * vec3(u_scale, 1.0) + u_translation, 1.0);
    v_uv = vec2(a_texCoord0.x, 1.0 - a_texCoord0.y);
}