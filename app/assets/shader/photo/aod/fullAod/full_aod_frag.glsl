#version 300 es

precision highp float;

in vec2 v_texCoord;

out vec4 fragColor;

uniform float u_alpha1;
uniform sampler2D u_lockTex;
uniform sampler2D u_grayTex1;

void main () {
        vec3 lock = texture(u_lockTex, v_texCoord).rgb;

        float gray1 = texture(u_grayTex1, v_texCoord).r;

        float rate1 = mix(1.0, gray1, u_alpha1);

        vec3 color = lock * rate1;

        fragColor = vec4(color, 1.0);
}

