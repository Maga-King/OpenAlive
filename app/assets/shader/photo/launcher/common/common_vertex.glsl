#version 300 es

//通用顶点shader
//flyme9.3 新增所有桌面效果使用

in vec3 a_position;

uniform mat4 u_projectionViewMatrix;

void main() {
    //gl_Position  = u_projectionViewMatrix * vec4(a_position, 1.0);
    gl_Position  = vec4(a_position, 1.0);
}