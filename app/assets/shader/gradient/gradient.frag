#version 300 es

precision lowp float;

#include <shader/base/dark_utlis.glsl>
#include <shader/base/dithering_utils.glsl>

in vec4 v_color_outer;
in vec4 v_color_1;
in vec4 v_color_2;
in vec4 v_color_3;
in vec4 v_color_4;
in vec4 v_color_5;
in vec4 v_color_6;
in vec4 v_color_inner;
in vec2 v_uv;
in float v_curve_1;
in float v_curve_2;
in float v_curve_3;
in float v_curve_4;
in float v_curve_5;
in float v_curve_6;

out highp vec4 fragColor;

uniform float u_dark_strength;

/**
 * ---------------------------- 0.0         v_color_inner
 * gradient
 * ---------------------------- v_curve_1   v_color_1
 * gradient
 * ---------------------------- v_curve_2   v_color_2
 * gradient
 * ---------------------------- v_curve_3   v_color_3
 * gradient
 * ---------------------------- v_curve_4   v_color_4
 * gradient
 * ---------------------------- v_curve_5   v_color_5
 * gradient
 * ---------------------------- v_curve_6   v_color_6
 * gradient
 * ---------------------------- 1.0         v_color_outer
 */
void main () {
    float val1 = smoothstep(0., v_curve_1, v_uv.y);
    float val2 = smoothstep(v_curve_1, v_curve_2, v_uv.y);
    float val3 = smoothstep(v_curve_2, v_curve_3, v_uv.y);
    float val4 = smoothstep(v_curve_3, v_curve_4, v_uv.y);
    float val5 = smoothstep(v_curve_4, v_curve_5, v_uv.y);
    float val6 = smoothstep(v_curve_5, v_curve_6, v_uv.y);
    float val7 = smoothstep(v_curve_6, 1.0, v_uv.y);

    vec4 comb7 = mix(v_color_6, vec4(v_color_outer.rgb, 1.), val7);
    vec4 comb6 = mix(v_color_5, comb7, val6);
    vec4 comb5 = mix(v_color_4, comb6, val5);
    vec4 comb4 = mix(v_color_3, comb5, val4);
    vec4 comb3 = mix(v_color_2, comb4, val3);
    vec4 comb2 = mix(v_color_1, comb3, val2);
    fragColor = applyDarkIfNeed(mix(vec4(v_color_inner.rgb, 1.), comb2, val1), u_dark_strength);
    fragColor.rgb += basicDithering(v_uv, 1.0);

//    // debug
//    if (abs(v_uv.y - v_curve_1) < 0.001 ||
//        abs(v_uv.y - v_curve_2) < 0.001 ||
//        abs(v_uv.y - v_curve_3) < 0.001 ||
//        abs(v_uv.y - v_curve_4) < 0.001 ||
//        abs(v_uv.y - v_curve_5) < 0.001 ||
//        abs(v_uv.y - v_curve_6) < 0.001) {
//        fragColor.rgb += vec3(1.0, 1.0, 1.0);
//    }
}

