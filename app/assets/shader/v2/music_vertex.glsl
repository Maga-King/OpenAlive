#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform float u_scaleX; // 水平缩放
uniform float u_scale; // 缩放
uniform vec2 u_translate; // 平移

out vec2 v_originTexCoord;
out vec2 v_texCoord;

vec2 uvTransform(vec2 uv) {
    float minScale = min(u_scale, 1.0 / u_scaleX);
    vec2 scale = vec2(minScale * u_scaleX, minScale);
    //float translateEdgeX = (1.0 - scale.x) * 0.5;
    //float translateX = clamp(u_translate.x, -translateEdgeX, translateEdgeX);
    float translateX = mod(u_translate.x, 2.0);
    float translateY = mod(u_translate.y, 1.0);
    vec2 uv1 = uv * scale + (1.0 - scale) * 0.5 + vec2(translateX, translateY);
    return uv1;
}

void main() {
    gl_Position = vec4(a_position, 1.0);
    v_originTexCoord = a_texCoord0;
    v_texCoord = uvTransform(a_texCoord0);
}