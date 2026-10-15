
precision mediump float ;

uniform sampler2D texture;
uniform sampler2D depthtexture;

uniform vec2 shift;
uniform float focus;
varying vec2 v_TexCoordinate;

void main()
{
vec4 color1;
vec4 color2;
vec4 color4;
vec4 color5;
float depth = texture2D(depthtexture, v_TexCoordinate+ shift * (-0.2 + focus)).r;
if((depth <= 1.0) && (depth > 0.8)){
   color1 = texture2D(texture, v_TexCoordinate+ shift * (-0.2 + focus));
} else {
   color1 = texture2D(texture, v_TexCoordinate+ shift * (-0.2 + focus));
   color1.a = 0.0;
}

depth = texture2D(depthtexture, v_TexCoordinate+ shift * (-0.4 + focus)).r;
if((depth <=0.8 ) && (depth > 0.4)){
   color2 = texture2D(texture, v_TexCoordinate+ shift * (-0.4 + focus));
} else {
  color2 = texture2D(texture, v_TexCoordinate+ shift * (-0.4 + focus));
  color2.a =0.0;
}

depth = texture2D(depthtexture, v_TexCoordinate+ shift * (-0.8 + focus)).r;
if((depth <= 0.4 ) && (depth > 0.2)){
   color4 = texture2D(texture, v_TexCoordinate+ shift * (-0.8 + focus));
} else {
  color4 = texture2D(texture, v_TexCoordinate+ shift * (-0.8 + focus));
  color4.a =0.0;

}

depth = texture2D(depthtexture, v_TexCoordinate+ shift * (-1.0 + focus)).r;
if((depth <= 0.2 ) && (depth >= 0.0)){
   color5 = texture2D(texture, v_TexCoordinate+ shift * (-1.0 + focus));
} else {
   color5 = texture2D(texture, v_TexCoordinate+ shift * (-1.0 + focus));
   color5.a =0.0;
}
vec4 mixcolor1 = mix(color2, color1, color1.a);
vec4 mixcolor3 = mix(color4, mixcolor1, mixcolor1.a);
vec4 mixcolor4 = mix(color5, mixcolor3, mixcolor3.a);
mixcolor4.r = clamp(mixcolor4.r, 0.0, 1.0);
mixcolor4.g = clamp(mixcolor4.g, 0.0, 1.0);
mixcolor4.b= clamp(mixcolor4.b, 0.0, 1.0);
gl_FragColor =mixcolor4;
//gl_FragColor =color5;
}