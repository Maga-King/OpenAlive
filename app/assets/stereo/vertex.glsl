attribute vec4 aPosition;
attribute vec2 aTextureCoordinate;
varying vec2 v_TexCoordinate;
uniform mat4 uTexMatrix;
void main()
{
  gl_Position = aPosition;
  v_TexCoordinate = (uTexMatrix * vec4(aTextureCoordinate, 0.0, 1.0)).st;
}