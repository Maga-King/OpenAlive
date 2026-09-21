#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

uniform vec3 u_startColor;
uniform vec3 u_endColor;

out vec3 v_color;
out vec2 v_uv;

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_uv = a_texCoord0;

    float f = a_texCoord0.x * (1.0 - a_texCoord0.y);
    f = (a_texCoord0.x + a_texCoord0.y) * 0.5;
    v_color = mix(u_startColor, u_endColor, f);
}