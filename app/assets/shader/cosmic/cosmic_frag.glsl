#version 320 es
precision highp float;

#include <shader/cosmic/cosmic_utils.glsl>
#include <shader/base/blend_utils.glsl>

out vec4 fragColor;
in vec3 v_origin_position;
in vec3 v_position;
in vec3 v_normal;

uniform vec3 u_eye_position;
uniform float u_time;
uniform vec4 u_base_color;
uniform vec4 u_stripe_color_1;
uniform vec4 u_stripe_color_2;
uniform vec4 u_stripe_color_3;
uniform float u_stripe_divergent;
uniform float u_stripe_thickness;
uniform float u_highlight_thickness;
uniform float u_highlight_strength;
uniform vec4 u_highlight_color;
uniform float u_alpha;

vec3 mapColor(vec3 mapR, vec3 mapG, vec3 mapB, vec3 inputColor) {
    vec3 baseColor = mapR;
    vec3 blendColor = blendNormal(baseColor, mapG, clamp(inputColor.g, 0.0, 1.0));
    return blendNormal(blendColor, mapB, clamp(inputColor.b, 0.0, 1.0));
}

void main () {

    vec3 rd = normalize(u_eye_position - v_position);

    vec3 position_distorted = v_origin_position +  precision_noise(v_origin_position * 1.5, vec3(0.0, 0.0, sin(u_time * 0.15))) * u_stripe_divergent;
    vec3 normal_distorted = calcNormal(position_distorted, u_time * 0.45, 1.0 + (19.0 * u_stripe_divergent));
    float ndotl_distorted = abs(dot(-rd, normal_distorted));
    // 计算条纹颜色
    vec3 stripe_color = mapColor(u_stripe_color_1.rgb, u_stripe_color_2.rgb, u_stripe_color_3.rgb, v_normal);
    // 计算条纹纹理
    float rim_distorted = pow(1.0 - ndotl_distorted, u_stripe_thickness/*条纹粗细*/);
    // 将基础色和条纹进行混合
    vec3 finalColor = mix(u_base_color.rgb, stripe_color.rgb, rim_distorted);

    float ndotl = abs(dot(-rd, v_normal));
    // 计算高光轮廓
    float rim = pow(1.0 - ndotl, u_highlight_thickness/*条纹粗细*/);
    // 叠加高光
    finalColor = blendNormal(finalColor, u_highlight_color.rgb, rim * u_highlight_strength);

    fragColor = vec4(finalColor, u_alpha);
}