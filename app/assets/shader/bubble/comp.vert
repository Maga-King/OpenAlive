#version 310 es

precision lowp float;

in vec3 a_position;
in vec2 a_texCoord0;

out vec2 uv;

void main() {
    uv = a_texCoord0;
    gl_Position = vec4(a_position, 1.0);
}
