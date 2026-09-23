#version 300 es

precision lowp float;

uniform vec4 u_vignette_color;
uniform highp vec2 u_resolution;

in vec4 v_color_outer;
in vec4 v_color_1;
in vec4 v_color_2;
in vec4 v_color_3;
in vec4 v_color_4;
in vec4 v_color_inner;
in vec2 v_uv;
in vec4 v_pos_grad;

out highp vec4 fragColor;

/**
 * ---------------------------- 1.0      v_color_outer
 * gradient
 * ---------------------------- v_uv.w   v_color_4
 * gradient
 * ---------------------------- v_uv.z   v_color_3
 * gradient
 * ---------------------------- v_uv.y   v_color_2
 * gradient
 * ---------------------------- v_uv.x   v_color_1
 * gradient
 * ---------------------------- 0.0      v_color_inner
 */
void main () {

    highp vec2 st = gl_FragCoord.xy / u_resolution.yy;
    vec2 st2 = gl_FragCoord.xy / u_resolution.xy;

    float val1 = smoothstep(0., v_pos_grad.x, v_uv.y);
    float val2 = smoothstep(v_pos_grad.x, v_pos_grad.y, v_uv.y);
    float val3 = smoothstep(v_pos_grad.y, v_pos_grad.z, v_uv.y);
    float val4 = smoothstep(v_pos_grad.z, v_pos_grad.w, v_uv.y);
    float val5 = smoothstep(v_pos_grad.w, 1.0, v_uv.y);

    vec4 comb5 = mix(v_color_4, vec4(v_color_outer.rgb, 1.), val5);
    vec4 comb4 = mix(v_color_3, comb5, val4);
    vec4 comb3 = mix(v_color_2, comb4, val3);
    vec4 comb2 = mix(v_color_1, comb3, val2);
    fragColor = mix(vec4(v_color_inner.rgb, 1.), comb2, val1);

    float vignetteRadius = smoothstep(0.3, 1.1, length(vec2(1.01, 1.01) * (st2 - vec2(0.5, 0.5))));
    fragColor = mix(fragColor, u_vignette_color, vignetteRadius * u_vignette_color.a);
}