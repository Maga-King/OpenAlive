#version 300 es

#include <shader/utils/dark_utils.glsl>

precision highp float;

const int LINE_NUM = 4; // 曲线条数
const int COLOR_NUM = 6; // 颜色个数

in vec2 v_originTexCoord;
in vec2 v_texCoord;

uniform vec3 u_colorCenter; // 三个颜色的中心位置
uniform vec3 u_colors[COLOR_NUM]; // 6个渐变色

uniform vec4 u_bessel[LINE_NUM]; // 贝塞尔曲线的参数(1、4控制起点和终点位置, 2、3控制起点和终点斜率)
uniform float u_width[LINE_NUM]; // 曲线宽度
uniform float u_darkStrength; // 深色模式强度

out vec4 o_fragColor;

float fun(float t, vec4 bessel) { // 贝塞尔函数方程
    float t2 = t * t;
    float t3 = t2 * t;
    float at = 1.0 - t;
    float at2 = at * at;
    float at3 = at2 * at;
    return at3 * bessel.x + 3.0 * t * at2 * bessel.y + 3.0 * t2 * at * bessel.z + t3 * bessel.w;
}

vec3 getFactor(int i, float u) { // 计算三个颜色对应的权重因子, i: 曲线编号, u: 纹理坐标的u分量
    float step = 1.0 / float(LINE_NUM);
    float start = float(i) * step;
    float x = 0.0;
    float ratio = fun(u, vec4(0.0, 0.0, 1.0, 1.0));
    if (i % 2 == 0) {
        //x = start + u * step;
        x = start + ratio * step;
    } else {
        //x = start + (1.0 - u) * step;
        x = start + (1.0 - ratio) * step;
    }
    vec3 factor = abs(x - u_colorCenter);
    factor = min(factor, 1.0 - factor);
    vec3 temp = vec3(factor);
    if (factor.x > factor.y && factor.x > factor.z) {
        float sum = factor.y + factor.z;
        factor.x = 0.0;
        float ratio = fun(temp.z / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.y = ratio;
        factor.z = 1.0 - ratio;
    } else if (factor.y > factor.x && factor.y > factor.z) {
        float sum = factor.x + factor.z;
        factor.y = 0.0;
        float ratio = fun(temp.z / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.x = ratio;
        factor.z = 1.0 - ratio;
    } else if (factor.z > factor.x && factor.z > factor.y) {
        float sum = factor.x + factor.y;
        factor.z = 0.0;
        float ratio = fun(temp.y / sum, vec4(0.0, 0.0, 1.0, 1.0));
        factor.x = ratio;
        factor.y = 1.0 - ratio;
    } else {
        float ratio = 1.0 / 3.0;
        factor.x = ratio;
        factor.y = ratio;
        factor.z = ratio;
    }
    return factor;
}

vec3 dithering(vec3 color) { // 解决色阶问题
    float noise = (fract(sin(dot(v_originTexCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    return color + noise;
}

vec3 getColor(float dist, float width, vec3 factor) {
    float ratio = 1.0 - dist / width;
    ratio = pow(ratio, 6.0);
    vec3 color1 = mix(u_colors[0], u_colors[1], ratio);
    vec3 color2 = mix(u_colors[2], u_colors[3], ratio);
    vec3 color3 = mix(u_colors[4], u_colors[5], ratio);

    //vec3 color1 = u_colors[1];
    //vec3 color2 = u_colors[3];
    //vec3 color3 = u_colors[5];

    vec3 color = factor.x * color1 + factor.y * color2 + factor.z * color3;
    color = dithering(color);
    color = applyDarkIfNeed(vec4(color, 1.0), u_darkStrength).rgb;
    return color;
}

vec3 getBackColor(vec2 uv) {
    float step = 1.0 / float(LINE_NUM);
    float halfStep = step * 0.5;
    vec4 bessel = vec4(-0.05, -0.05, 0.05, 0.05) + halfStep;
    float width = halfStep;
    for (int i = LINE_NUM - 1; i >= 0; i--) {
        float add = float(i) * step;
        float y = fun(uv.x, bessel + add);
        float dist = abs(y - uv.y);
        if (y < 0.0 + width) {
            float dist2 = abs(y + 1.0 - uv.y);
            if (dist2 < dist) {
                y = y + 1.0;
                dist = dist2;
            }
        } else if (y > 1.0 - width) {
            float dist2 = abs(y - 1.0 - uv.y);
            if (dist2 < dist) {
                y = y - 1.0;
                dist = dist2;
            }
        }
        if (dist < width) {
            vec3 factor = getFactor(i, uv.x);
            vec3 color = getColor(uv.y - y + width,  2.0 * width, factor);
            return color;
        }
    }
    return vec3(0.0, 0.0, 0.0);
}

void main() {
    float f[LINE_NUM]; // 贝塞尔函数值
    int flag[LINE_NUM]; // 是否初始化标记
    for (int i = 0; i < LINE_NUM; i++) {
        flag[i] = 0;
    }

    // 水平镜像翻转uv
    vec2 uv = v_texCoord;
    //uv.x = mod(uv.x + 10.0, 2.0);
    //uv.x = 1.0 - abs(uv.x - 1.0);
    uv.y = mod(uv.y + 10.0, 1.0);

    // 解决顶部曲线向上越界渲染异常问题(顶部曲线本应该最后渲染, 但越界后, 变成底部了, 所以应该最先渲染)
    for (int i = 1; i >= 0; i--) {
        f[i] = fun(uv.x, u_bessel[i]);
        flag[i] = 1;
        if (f[i] < 0.0 + u_width[i]) {
            float v = uv.y - 1.0;
            float dist = abs(f[i] - v);
            if (dist < u_width[i]) {
                vec3 factor = getFactor(i, uv.x);
                vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
                o_fragColor = vec4(color, 1.0);
                return;
            }
        }
    }

    for (int i = LINE_NUM - 1; i >= 0; i--) {
        if (flag[i] == 0) {
            f[i] = fun(uv.x, u_bessel[i]);
            flag[i] = 1;
        }
        float v = uv.y;
        float dist = abs(f[i] - v);
        if (dist < u_width[i]) {
            vec3 factor = getFactor(i, uv.x);
            vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
            o_fragColor = vec4(color, 1.0);
            return;
        }
    }

    // 解决底部曲线向下越界渲染异常问题(底部曲线本应该最先渲染, 但越界后, 变成顶部了, 所以应该最后渲染)
    for (int i = LINE_NUM - 1; i >= LINE_NUM - 2; i--) {
        if (flag[i] == 0) {
            f[i] = fun(uv.x, u_bessel[i]);
            flag[i] = 1;
        }
        if (f[i] > 1.0 - u_width[i]) {
            float v = uv.y + 1.0;
            float dist = abs(f[i] - v);
            if (dist < u_width[i]) {
                vec3 factor = getFactor(i, uv.x);
                vec3 color = getColor(v - f[i] + u_width[i],  2.0 * u_width[i], factor);
                o_fragColor = vec4(color, 1.0);
                return;
            }
        }
    }

    o_fragColor = vec4(getBackColor(uv), 1.0);
}