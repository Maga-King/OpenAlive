precision highp float;

#define DARK_BRIGHTNESS -0.3f // 亮度最大变化量
#define DARK_CONTRAST 0.15f // 对比度最大变化量

const vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    // 亮度调节
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    // 对比度调节
    color.rgb = (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);

    return color;
}