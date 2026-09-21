#version 300 es
in vec3 a_position;
in vec3 a_normal;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
uniform vec4 u_location;

uniform vec4 u_lightLocation1;
uniform vec4 u_lightLocation2;
uniform vec4 u_lightLocation3;
//uniform vec4 u_lightLocation4;
//uniform vec4 u_lightLocation5;

uniform float u_lightRadius1;
uniform float u_lightRadius2;
uniform float u_lightRadius3;
//uniform float u_lightRadius4;
//uniform float u_lightRadius5;

out vec2 v_uv;
out float v_alpha;
out float v_light1;
out float v_light2;
out float v_light3;
//out float v_light4;
//out float v_light5;

float calcAttenuation(vec3 pos, vec4 light, float R) {
    float distance = distance(light.xyz, pos);
    float w = 1.0 - clamp(distance / R, 0.0, 1.0);
    return w * light.w;
}

void main() {
    vec3 center = vec3(u_location.xy - vec2(512.0, 1080.0), u_location.z);
    vec3 wordPosition = a_position * u_location.w + center;
    gl_Position = u_projectionViewMatrix * vec4(wordPosition, 1.0);
    v_uv = a_texCoord0;

    vec4 light1 = vec4(u_lightLocation1.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation1.w);
    vec4 light2 = vec4(u_lightLocation2.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation2.w);
    vec4 light3 = vec4(u_lightLocation3.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation3.w);
//    vec4 light4 = vec4(u_lightLocation4.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation4.w);
//    vec4 light5 = vec4(u_lightLocation5.xyz - vec3(512.0, 1080.0, 0.0), u_lightLocation5.w);
    v_light1 = calcAttenuation(wordPosition, light1, u_lightRadius1) * 2.0;
    v_light2 = calcAttenuation(wordPosition, light2, u_lightRadius2) * 2.0;
    v_light3 = calcAttenuation(wordPosition, light3, u_lightRadius3) * 2.0;
//    v_light4 = calcAttenuation(wordPosition, light4, u_lightRadius4) * 2.0;
//    v_light5 = calcAttenuation(wordPosition, light5, u_lightRadius5) * 2.0;


    v_alpha = dot(normalize(a_normal), normalize(light1.xyz - wordPosition)) + 1.0f;
}