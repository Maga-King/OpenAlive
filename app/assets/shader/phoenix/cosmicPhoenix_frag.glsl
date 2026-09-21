#version 320 es
precision highp float;

#include <shader/cosmic/cosmic_utils.glsl>
#include <shader/base/blend_utils.glsl>

out vec4 fragColor;
in vec3 v_origin_position;
in vec3 v_position;
in vec3 v_normal;
in vec3 v_normal1;
in vec4 v_GlPosition;
in vec3 finalNoise;
in float noiseValue;
in float noiseValue2;
in float MaskValue;

uniform vec3 u_eye_position;
uniform float u_time;

//@Color(边缘高光),#00FF00) aod.baseColor
uniform vec4 u_base_color;
//@Range(高光厚度,2,0,10,0.1) aod.stripeThickness
uniform float u_stripe_thickness;

//@Color(中心,#00FF00) aod.stripeColor2
uniform vec4 u_stripe_color_2;
//@Color(中心外,#00FF00) aod.u_base_color_flow
uniform vec4 u_base_color_flow;
//@Color(中横-变亮,#FFFFFF) aod.highlightColor
uniform vec4 u_highlight_color;
//@Range(中横强度,0.6,0,10,0.1) aod.highlightStrength
uniform float u_highlight_strength;
//uniform float u_highlight_strength1;

//@Color(上条纹凹槽,#00FF00) aod.stripeColor3
uniform vec4 u_stripe_color_3;
//@Color(上球-变亮,#00FF00) aod.u_stripe_color_flow_2
uniform vec4 u_stripe_color_flow_2;
//@Color(下外圈,#00FF00) aod.u_stripe_color_flow_1
uniform vec4 u_stripe_color_flow_1;
//@Color(下最外圈),#00FF00) aod.stripeColor1
uniform vec4 u_stripe_color_1;


//@Color(流动条纹3,#00FF00) aod.u_stripe_color_flow_3
uniform vec4 u_stripe_color_flow_3;

//@Range(条纹范围,0.4,-10,10,0.1) aod.stripeDivergent
uniform float u_stripe_divergent;
//@Range(颜色条纹范围,0.4,-10,10,0.1) aod.u_stripe_divergent1
uniform float u_stripe_divergent1;
//@Range(颜色条纹厚度,2,0,10,0.1) aod.u_stripe_thickness1
uniform float u_stripe_thickness1;
//@Range(高亮厚度,6,0,10,0.1) aod.highlightThickness
uniform float u_highlight_thickness;
//@Range(颜色高亮厚度,6,0,10,0.1) aod.u_highlight_thickness1
uniform float u_highlight_thickness1;

//@Range(透明度,1,0,1,0.1) aod.alpha
uniform float u_alpha;
//@Range(黑线抽搐,6,0,10,0.1) aod.u_positionTime
uniform float u_positionTime;
//@Range(黑线变化,6,0,10,0.1) aod.u_normalTime
uniform float u_normalTime;


vec3 mapColor(vec3 mapR, vec3 mapG, vec3 mapB, vec3 inputColor) {
    vec3 baseColor = mapR;
    vec3 blendColor = blendNormal(baseColor, mapG, clamp(inputColor.g, 0.0, 1.0));
    return blendNormal(blendColor, mapB, clamp(inputColor.b, 0.0, 1.0));
}

void main () {

    vec3 rd = normalize(u_eye_position - v_position);


    vec3 position_distorted1 = v_origin_position +  precision_noise(v_origin_position * 1.5, vec3(0.0, 0.0, sin(u_time * u_positionTime))) * u_stripe_divergent1;
    vec3 normal_distorted1 = calcNormal(position_distorted1, u_time * u_normalTime, 1.0 + (19.0 * u_stripe_divergent1));
    float ndotl_distorted1 = abs(dot(-rd, normal_distorted1));
    // 计算条纹颜色
    vec3 stripe_color1 = mapColor(u_stripe_color_flow_1.rgb, u_stripe_color_flow_2.rgb, u_stripe_color_flow_3.rgb, v_normal1);
    // 计算条纹纹理
    float rim_distorted1 = pow(1.0 - ndotl_distorted1, u_stripe_thickness1/*条纹粗细*/);
    // 将基础色和条纹进行混合
    vec3 finalColor1 = mix(u_base_color_flow.rgb, stripe_color1.rgb, rim_distorted1);

    //float ndotl1 = abs(dot(-rd, v_normal1));
    // 计算高光轮廓
    //float rim1 = pow(1.0 - ndotl1, u_highlight_thickness1/*条纹粗细*/);
    // 叠加高光
    //finalColor1 = blendNormal(finalColor1, u_highlight_color.rgb, rim1 * u_highlight_strength1);



    vec3 position_distorted = v_origin_position +  finalNoise;
    vec3 normal_distorted = calcNormal1(position_distorted, 1.0 + (19.0 * u_stripe_divergent), dot(finalNoise, finalNoise));
    float ndotV_distorted = dot(-rd, normal_distorted)*0.5+0.5;

    // 计算条纹颜色
    vec3 stripe_color = mapColor(u_stripe_color_1.rgb, u_stripe_color_2.rgb, u_stripe_color_3.rgb, v_normal);
    // 计算条纹纹理
    float rim_distorted = pow(1.0 - ndotV_distorted, u_stripe_thickness/*条纹粗细*/);
    // 将基础色和条纹进行混合
    vec3 finalColor = mix(u_base_color.rgb, stripe_color.rgb, rim_distorted);
    //vec3 finalColor = vec3(0.0);

    float ndotl = abs(dot(-rd, v_normal));
    // 计算高光轮廓
    float rim = pow(1.0 - ndotl, u_highlight_thickness/*条纹粗细*/);
    // 叠加高光
    finalColor += blendNormal(finalColor, u_highlight_color.rgb, rim * u_highlight_strength) * finalColor1;

    fragColor = vec4(finalColor, u_alpha);
}
