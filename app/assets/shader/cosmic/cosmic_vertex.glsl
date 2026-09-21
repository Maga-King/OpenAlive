#version 320 es
precision highp float;

#include <shader/cosmic/cosmic_utils.glsl>

in vec3 a_position;
in vec3 a_normal;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_modelMatrix;
uniform float u_time;
uniform vec3 u_translation;
uniform float u_scale;
uniform float u_noise_amplitude;

out vec3 v_origin_position;
out vec3 v_position;
out vec3 v_normal;

void main() {
    v_origin_position = a_position;
    v_position = noisePosition(a_position, u_time * 0.45, u_noise_amplitude);
    v_normal = calcNormal(a_position, u_time * 0.45, 1.0);
    gl_Position = u_projectionViewMatrix * (u_modelMatrix * vec4(v_position * u_scale, 1.0) + vec4(u_translation.z, u_translation.y, -u_translation.x, 0.0));
}