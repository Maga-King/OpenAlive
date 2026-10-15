#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_texCoord;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_transform_matrix;

void main() {
    gl_Position = u_projectionViewMatrix * vec4(a_position, 1);
    vec4 transformTexCoord = (u_transform_matrix * vec4(a_texCoord0, 0, 1));
    v_texCoord = vec2(transformTexCoord.x, transformTexCoord.y);
}