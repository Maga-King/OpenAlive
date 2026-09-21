#version 300 es
precision highp float;
#include <shader/base/blend_utils.glsl>
#include <shader/base/noise_2d.glsl>

#define ENABLE_DEBUG_GRID 0
#define ENABLE_DEBUG_COLOR 0

in vec2 v_texCoord;
in vec2 v_originTexCoord;
uniform sampler2D u_bg_sampler;
uniform float u_time;
uniform float u_speed;
uniform float u_page_offset;
uniform float u_noise_displacement;
uniform float u_noise_uvs_zoom;
uniform vec3 u_third_color;
uniform vec3 u_second_color;
uniform vec3 u_first_color;
out vec4 fragColor;

vec3 mixColor(vec3 color) {
    vec3 baseColor = u_first_color;
    vec3 blendColor = blendNormal(baseColor, u_second_color, color.g);
    return blendNormal(blendColor, u_third_color, color.r);
}

float grid(vec2 uv, float _scale, float thickness) {
    vec2 grid_uv = fract(_scale * uv);
    vec2 grid_vec = 1.0 - smoothstep(0.0, thickness, grid_uv);
    return 1.0 - dot(grid_vec, vec2(1.0));
}

void main() {
    float time = u_time * u_speed;
    vec2 uv = v_texCoord;
    float n = snoise(vec2(uv.x, uv.y * u_noise_uvs_zoom + time * 0.1));
    uv *= vec2(1.0 + n * u_noise_displacement);
    uv.x += (u_page_offset * 0.5);
    vec3 c = texture(u_bg_sampler, uv).rgb;
    c = mixColor(c);

    vec4 finalColor = vec4(c, 1.0);
    #if ENABLE_DEBUG_GRID
    if (uv.x > 0.0 && uv.x < 1.0 && uv.y > 0.0 && uv.y < 1.0) {
        float grid = grid(uv, 20.0, 0.05);
        finalColor = min(finalColor, vec4(grid, grid, grid, 1.0));
    }
    #endif
    #if ENABLE_DEBUG_COLOR
    if (v_originTexCoord.y < 0.02) {
        if (v_originTexCoord.x < 0.33) {
            finalColor = vec4(u_third_color, 1.0);
        } else if (v_originTexCoord.x < 0.66) {
            finalColor = vec4(u_second_color, 1.0);
        } else {
            finalColor = vec4(u_first_color, 1.0);
        }
    }
    #endif
    fragColor = finalColor;
}