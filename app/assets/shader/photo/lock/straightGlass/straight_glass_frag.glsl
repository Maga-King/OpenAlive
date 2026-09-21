#version 300 es

precision highp float;

uniform sampler2D u_lockTex; // 锁屏纹理
uniform vec2 u_offsetStrength; // 纹理偏移强度(水平和竖直两个方向)
uniform float u_lineWidth; // 单条曲线宽度
uniform float u_lineOffset; // 曲线偏移(两条曲线的左边间隔)
uniform float u_maskThreshold; // 遮罩阈值
uniform vec4 u_display_uv_rect; // 显示区域 UV 坐标 (left, top, right, bottom)

in vec2 v_lockUV; // 锁屏纹理的uv坐标
in vec2 v_lineUV; // 曲线纹理的uv坐标
in vec2 v_maskUV; // 遮罩纹理的uv坐标

out vec4 o_FragColor;

// 将 UV 映射到显示区域
vec2 mapUVToDisplayRect(vec2 uv) {
    vec2 mappedUV;
    mappedUV.x = u_display_uv_rect.x + uv.x * (u_display_uv_rect.z - u_display_uv_rect.x);
    mappedUV.y = u_display_uv_rect.y + uv.y * (u_display_uv_rect.w - u_display_uv_rect.y);
    return mappedUV;
}

float interpolation(float x) { // 灰度插值
    return pow(x, 3.0);
}

float getGray() { // 获取uv坐标对应的灰度
    float x = v_lineUV.x - (0.5 - u_lineWidth * 0.5);
    float diff = mod(x, u_lineOffset);
    for (int i = 0; i < 10; i++) {
        if (diff > u_lineWidth) {
            return -1.0;
        } else if (diff < 0.0) {
            diff += u_lineOffset;
        } else {
            return interpolation(diff / u_lineWidth);
        }
    }
    return interpolation(diff / u_lineWidth);
}

float getG(float gray, float maskOffset) { // 获取灰度的变异参数
    float gap = 0.01;
    if (maskOffset > gap) {
        float a = 0.7;
        if (abs(gray - a) < 0.07) {
            return abs(gray - a);
        }
        if (gray < a) {
            return abs(gray - a) / a;
        } else {
            return abs(gray - a) / (1.0 - a);
        }
    }
    return abs(maskOffset - gap * 0.5);
}

vec2 getOffset(float gray, float maskOffset) { // 获取uv坐标对应的水平和竖直方向的偏移
    float g = getG(gray, maskOffset);
    float offsetX = 1.0 - pow(g, 0.5);
    offsetX = (offsetX - 0.5) * 2.0;
    return vec2(offsetX, 0.0);
}

vec2 getLockUV(vec2 offset) { // 获取锁屏纹理坐标
    vec2 uv = v_lockUV + offset * u_offsetStrength;
    uv.x = clamp(uv.x, 0.0, 1.0);
    uv.y = clamp(uv.y, 0.0, 1.0);
    return mapUVToDisplayRect(uv);
}

float getMask() { // 获取uv坐标对应的灰度图
    float dist = abs(v_maskUV.x - 0.5);
    float mask = clamp(1.0 - dist * 2.0, 0.0, 1.0);
    return mask;
}

void main() {
    float mask = getMask();
    if (mask > u_maskThreshold) {
        float gray = getGray();
        float maskOffset = abs(mask - u_maskThreshold);
        if (gray < 0.0 && maskOffset > 0.01) {
            vec2 uv = mapUVToDisplayRect(v_lockUV);
            vec3 lockTex = texture(u_lockTex, uv).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        } else {
            vec2 offset = getOffset(gray, maskOffset);
            vec2 lockUV = getLockUV(offset);
            vec3 lockTex = texture(u_lockTex, lockUV).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        }
    } else {
        vec2 uv = mapUVToDisplayRect(v_lockUV);
        vec3 lockTex = texture(u_lockTex, uv).rgb;
        o_FragColor = vec4(lockTex, 1.0);
    }
}