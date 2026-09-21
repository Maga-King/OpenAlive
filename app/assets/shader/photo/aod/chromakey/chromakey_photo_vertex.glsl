#version 300 es
in vec3 a_position;
in vec2 a_texCoord0;

out vec2 v_video_texCoord;
out vec2 v_snapshot_texCoord;
out vec2 v_content_texCoord;

uniform mat4 u_projectionViewMatrix;
uniform mat4 u_modelMatrix;
uniform mat4 u_contentMatrix;
uniform mat4 u_video_transform;
uniform float u_aspect_ratio;

void main() {
    gl_Position = u_projectionViewMatrix * u_modelMatrix * vec4(a_position, 1);
    v_video_texCoord = (u_video_transform * vec4(a_texCoord0, 0.0, 1.0)).xy;
    v_snapshot_texCoord = vec2(a_texCoord0.x, 1.0 - a_texCoord0.y);
    v_content_texCoord = (u_contentMatrix * vec4(a_texCoord0.x, 1.0 - a_texCoord0.y, 0.0, 1.0)).xy;
}