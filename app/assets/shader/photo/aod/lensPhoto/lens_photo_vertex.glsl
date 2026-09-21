#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_decorator_matrix;

out vec2 v_originTexCoord;
out vec2 v_decorato_texCoord;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position, 1.0);
    v_originTexCoord = a_texCoord0;
    v_decorato_texCoord = (u_decorator_matrix * vec4((a_texCoord0 * 2.0) - 1.0, 0.0, 1.0)).xy;
    v_decorato_texCoord.y = 1.0 - v_decorato_texCoord.y;
}