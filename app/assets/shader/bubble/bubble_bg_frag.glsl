#version 300 es
precision lowp float;

#include <shader/base/dithering_utils.glsl>

in vec3 v_color;
in vec2 v_uv;

out vec4 fragColor;

void main () {
    fragColor = vec4(v_color, 1.0);
    fragColor.rgb += basicDithering(v_uv, 2.);
}