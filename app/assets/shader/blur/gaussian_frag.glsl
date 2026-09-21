#version 300 es
precision highp float;

in vec2 v_texCoord;

uniform sampler2D u_blur_texture;
uniform float u_weights[50];
uniform float u_offsets[50];
uniform int u_radius;
uniform bool u_horizontal;
uniform vec2 u_step;
uniform float u_texture_min;
uniform float u_texture_max;

out vec4 fragColor;

vec2 getTextureCoord(float x, float y) {
    return clamp(vec2(x, y), u_texture_min, u_texture_max);
}

vec4 gassian() {
    vec3 sum;
    for (int i = 0; i < u_radius; i++) {
        if (i == 0) {
            sum = texture(u_blur_texture, v_texCoord).rgb * u_weights[0];
        } else {
            if (u_horizontal) {
                sum += texture(u_blur_texture, getTextureCoord(v_texCoord.x + u_offsets[i] * u_step.x, v_texCoord.y)).rgb * u_weights[i];
                sum += texture(u_blur_texture, getTextureCoord(v_texCoord.x - u_offsets[i] * u_step.x, v_texCoord.y)).rgb * u_weights[i];
            } else {
                sum += texture(u_blur_texture, getTextureCoord(v_texCoord.x, v_texCoord.y + u_offsets[i] * u_step.y)).rgb * u_weights[i];
                sum += texture(u_blur_texture, getTextureCoord(v_texCoord.x, v_texCoord.y - u_offsets[i] * u_step.y)).rgb * u_weights[i];
            }
        }
    }
    return vec4(sum, 1.0);
}

void main () {
    fragColor = gassian();
}