#version 300 es
precision highp float;
#include <shader/base/dark_utlis.glsl>
#include <shader/base/blend_utils.glsl>

#define ENABLE_RIPPLE 0
#define ENABLE_DEBUG_GRID 0

in vec2 v_originTexCoord;
in vec2 v_decorato_texCoord;
uniform sampler2D u_photo;
uniform sampler2D u_lens_decorator;
uniform vec3 u_translation;
uniform mat4 u_texture_matrix;
uniform float u_lens_scale;
uniform float u_aspect_ratio;
uniform float u_aspect_ratio_reciprocal;
uniform float u_distortion;
uniform float u_zoom;
uniform float u_decorator_alpha;
uniform float u_ripple_radius;
uniform float u_ripple_boundary;
uniform float u_dark_strength;
out vec4 fragColor;

const vec3 saturationWeighting = vec3(0.2125, 0.7154, 0.0721);

vec2 lens_distortion(vec2 r, float alpha) {
    return r * (1.0 - alpha * dot(r, r));
}

vec2 zoom_point(vec2 uv, vec2 point, float zoom) {
    return (uv - point) * zoom + point;
}

vec3 increaseSaturation(vec3 base, float strength) {
    float luminance = dot(base, saturationWeighting);
    vec3 greyScaleColor = vec3(luminance);
    return mix(greyScaleColor, base, 1.0 + strength);
}

float grid(vec2 uv, float _scale, float thickness) {
    vec2 grid_uv = fract(_scale * uv);
    vec2 grid_vec = 1.0 - smoothstep(0.0, thickness, grid_uv);
    return 1.0 - dot(grid_vec, vec2(1.0));
}

void main () {
    vec2 uv = vec2(v_originTexCoord.x, v_originTexCoord.y * u_aspect_ratio);
    vec2 uvCenter = vec2(0.5 + u_translation.x, (0.5 + u_translation.y) * u_aspect_ratio);
    float dist = distance(uv, uvCenter);
    if (dist > u_lens_scale) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec2 distortionUV = zoom_point(uv + lens_distortion(uv - uvCenter, u_distortion), uvCenter, u_zoom);
    #if ENABLE_RIPPLE
    if ((u_ripple_radius - u_ripple_boundary) > 0.0 && (dist <= (u_ripple_radius + u_ripple_boundary)) && (dist >= (u_ripple_radius - u_ripple_boundary))) {
        float x = (dist - u_ripple_radius);
        float moveDis = 20.0 * x * (x - 0.1)*(x + 0.1);
        vec2 unitDirectionVec = normalize(uv - uvCenter);
        distortionUV += (unitDirectionVec * moveDis);
    }
    #endif

    vec2 transformUV = (u_texture_matrix * vec4((vec2(distortionUV.x, distortionUV.y * u_aspect_ratio_reciprocal) * 2.0) - 1.0, 0.0, 1.0)).xy;
    transformUV.y = 1.0 - transformUV.y;
    vec3 finalColor = texture(u_photo, transformUV).rgb;
    if (u_decorator_alpha > 0.0001) {
        finalColor = increaseSaturation(finalColor, u_decorator_alpha * 0.5f);
        vec4 decoratoColor = texture(u_lens_decorator, v_decorato_texCoord);
        finalColor = blendNormal(finalColor, decoratoColor.rgb, decoratoColor.a * u_decorator_alpha);
    }
    #if ENABLE_DEBUG_GRID
    float grid = grid(transformUV, 20.0, 0.05);
    finalColor = min(finalColor, vec3(grid, grid, grid));
    #endif
    vec4 darkColor = applyDarkIfNeed(vec4(finalColor, 1.0), u_dark_strength);
    fragColor = mix(darkColor, vec4(0.0, 0.0, 0.0, 1.0), smoothstep(u_lens_scale - 0.002, u_lens_scale, dist));
}