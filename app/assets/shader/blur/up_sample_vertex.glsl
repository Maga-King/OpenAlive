#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform mat3 u_texture_matrix;

out vec2 v_texCoord;
out vec2 v_uv;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position, 1);
    v_texCoord = (u_texture_matrix * vec3(a_texCoord0, 1.0)).xy;
}