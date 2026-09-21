#version 300 es
precision highp float;

in vec2 v_texCoord;
uniform sampler2D u_photo;
out vec4 fragColor;

void main () {
    fragColor = vec4(texture(u_photo, vec2(v_texCoord)).xyz, 1.0);
}