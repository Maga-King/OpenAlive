#version 320 es
precision highp float;

#include <shader/base/dithering_utils.glsl>

in vec2 v_uv;
uniform vec2 u_start_pos;
uniform vec2 u_end_pos;
uniform vec4 u_start_color;
uniform vec4 u_end_color;
out vec4 fragColor;

void main() {
    vec2 startToPosition = normalize(v_uv - u_start_pos);
    vec2 startToEnd = normalize(u_end_pos - u_start_pos);

    float angle = dot(startToPosition, startToEnd);
    if (angle < 0.0) {
        fragColor = vec4(u_start_color.rgb, 1.0);
    } else {
        float distance = length(v_uv - u_start_pos);
        float percentage = angle * distance / length(u_end_pos - u_start_pos);
        fragColor = vec4(mix(u_start_color.rgb, u_end_color.rgb, percentage), 1.0);
    }
    fragColor.rgb += basicDithering(v_uv, 1.0);
}