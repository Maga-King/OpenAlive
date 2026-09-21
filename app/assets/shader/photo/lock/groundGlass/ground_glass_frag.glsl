#version 300 es

precision highp float;

uniform sampler2D u_lockTex; // 锁屏纹理
uniform sampler2D u_grayTex; // 灰度纹理
uniform sampler2D u_maskTex; // 遮罩纹理
uniform float u_minGray; // 最小灰度
uniform float u_maskThreshold; // 遮罩阈值
uniform vec2 u_dudv; // 灰度纹理的du、dv
uniform vec4 u_display_uv_rect; // 显示区域 UV 坐标 (left, top, right, bottom)

in vec2 v_lockUV; // 锁屏纹理的uv坐标
in vec2 v_grayUV; // 灰度纹理的uv坐标
in vec2 v_maskUV; // 遮罩纹理的uv坐标

out vec4 o_FragColor;

// 将 UV 映射到显示区域
vec2 mapUVToDisplayRect(vec2 uv) {
    vec2 mappedUV;
    mappedUV.x = u_display_uv_rect.x + uv.x * (u_display_uv_rect.z - u_display_uv_rect.x);
    mappedUV.y = u_display_uv_rect.y + uv.y * (u_display_uv_rect.w - u_display_uv_rect.y);
    return mappedUV;
}

float getGray(vec2 uv) {
    float gray = texture(u_grayTex, uv).r;
    return max(gray, u_minGray);
}

vec2 getDelta() {
    float gray1 = getGray(v_grayUV);
    float gray2 = getGray(vec2(v_grayUV.x - u_dudv.x, v_grayUV.y));
    float gray3 = getGray(vec2(v_grayUV.x, v_grayUV.y - u_dudv.y));
    float dx = gray2 - gray1;
    float dy = gray3 - gray1;
    return vec2(dx, dy);
}

vec2 getOffset() {
    vec2 delta = getDelta();
    return delta;
}

vec2 getLockUV(vec2 offset) { // 获取锁屏纹理坐标
    vec2 uv = v_lockUV + offset * 0.1;
    uv.x = clamp(uv.x, 0.0, 1.0);
    uv.y = clamp(uv.y, 0.0, 1.0);
    return mapUVToDisplayRect(uv);
}

float getMask() {
    return texture(u_maskTex, v_maskUV).r;
}

void main() {
    float mask = getMask();
    if (mask > u_maskThreshold) {
        float maskOffset = abs(mask - u_maskThreshold);
        if (maskOffset < 0.05) {
            vec2 uv = mapUVToDisplayRect(v_lockUV);
            vec3 lockTex = texture(u_lockTex, uv).rgb;
            o_FragColor = vec4(lockTex, 1.0);
        } else {
            vec2 offset = getOffset();
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