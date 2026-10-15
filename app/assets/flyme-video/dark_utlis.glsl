#define DARK_BRIGHTNESS -0.3f
#define DARK_CONTRAST -0.1f
#define DARK_TEMPERATURE -0.063f
#define DARK_SATURATION -0.18f

const vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);

vec4 applyDarkIfNeed(vec4 color, float darkStrength) {
    if (darkStrength <= 0.01) {
        return color;
    }
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * darkStrength);
    color.rgb=  (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * darkStrength) + vec3(0.5);
    color.rb = vec2(color.r * (1.0 + DARK_TEMPERATURE * darkStrength), color.b * (1.0 - DARK_TEMPERATURE * darkStrength));

    float luminance = dot(color.rgb, luminanceWeighting);
    vec3 greyScaleColor = vec3(luminance);
    color.rgb = mix(greyScaleColor, color.rgb, (1.0 + DARK_SATURATION * darkStrength));

    return color;
}