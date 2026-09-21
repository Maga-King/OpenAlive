precision highp float;

#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f

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