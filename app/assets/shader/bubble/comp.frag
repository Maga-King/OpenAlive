#version 310 es

precision lowp float;

in vec2 uv;

uniform sampler2D scene;


out vec4 fragColor;



void main() {
    vec4 color = texture(scene, uv);
    fragColor = color;
}
