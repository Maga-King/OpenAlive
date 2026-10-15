attribute vec4 aPosition;
attribute vec2 aTextureCoordinate;
varying vec2 v_TexCoordinate;
uniform mat4 uMVPMatrix;

void main()
{
  gl_Position = uMVPMatrix * aPosition;
  v_TexCoordinate = aTextureCoordinate;
}