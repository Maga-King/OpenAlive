#version 300 es
precision highp float;

in vec2 v_uv;
uniform sampler2D u_text_bitmap;
out vec4 fragColor;

void main () {
    fragColor = texture(u_text_bitmap, v_uv);
}