#version 300 es

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projectionViewMatrix;
out vec2 v_texCoord;
out vec2 v_originTexCoord;

mat2 scale(vec2 _scale){
    return mat2(_scale.x, 0.0, 0.0, _scale.y);
}

void main() {
    gl_Position = vec4(a_position, 1);
    v_originTexCoord = a_texCoord0;
    v_texCoord = a_texCoord0;
    v_texCoord.y = 1.0 - v_texCoord.y;
    v_texCoord.x *= 0.5;
    v_texCoord -= vec2(0.5);
    v_texCoord = scale(vec2(1.1)) * v_texCoord;
    v_texCoord += vec2(0.5);
}