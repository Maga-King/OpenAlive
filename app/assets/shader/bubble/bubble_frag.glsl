#version 300 es
precision lowp float;

uniform vec4 u_materialColor;
uniform vec4 u_lightColor1;
uniform vec4 u_lightColor2;
uniform vec4 u_lightColor3;
//uniform vec4 u_lightColor4;
//uniform vec4 u_lightColor5;
uniform float u_alpha;

in float v_light1;
in float v_light2;
in float v_light3;
//in float v_light4;
//in float v_light5;
in float v_alpha;

out vec4 fragColor;
void main () {
    vec4 color = mix(u_materialColor, u_lightColor1, v_light1);
    color = mix(color, u_lightColor2, v_light2);
    color = mix(color, u_lightColor3, v_light3);
//    color = mix(color, u_lightColor4, v_light4);
//    color = mix(color, u_lightColor5, v_light5);
    fragColor = vec4(color.rgb, color.a * v_alpha * u_alpha);
}