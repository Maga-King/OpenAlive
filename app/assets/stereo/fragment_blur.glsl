
precision mediump float ;
uniform sampler2D texture;
uniform sampler2D oritexture;
uniform vec2 shift;
uniform float focus;
varying vec2 v_TexCoordinate;

uniform float u_dark_strength;

uniform bool needrepeat;

#define DARK_BRIGHTNESS -0.3
#define DARK_CONTRAST -0.1
#define DARK_TEMPERATURE -0.063
#define DARK_SATURATION -0.18

const vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);

vec4 applyDark(vec4 color) {
    color.rgb = color.rgb * (1.0 + DARK_BRIGHTNESS * u_dark_strength);
    color.rgb=  (color.rgb - vec3(0.5)) * (1.0 + DARK_CONTRAST * u_dark_strength) + vec3(0.5);
    color.rb = vec2(color.r * (1.0 + DARK_TEMPERATURE * u_dark_strength), color.b * (1.0 - DARK_TEMPERATURE * u_dark_strength));

    float luminance = dot(color.rgb, luminanceWeighting);
    vec3 greyScaleColor = vec3(luminance);
    color.rgb = mix(greyScaleColor, color.rgb, (1.0 + DARK_SATURATION * u_dark_strength));

    return color;
}

vec4 blur(sampler2D m_texture,vec2 uv){

vec4 color=vec4(0.0,0.0,0.0,0.0);
vec4 currentColor=vec4(0.0,0.0,0.0,0.0);
float coreSize=3.0;
float texelOffset=1.0/256.0;
float kernel[9];

kernel[6]=1.0;kernel[7]=2.0;kernel[8]=1.0;
kernel[3]=2.0;kernel[4]=4.0;kernel[5]=2.0;
kernel[0]=1.0;kernel[1]=2.0;kernel[2]=1.0;

                        //移动高斯核,对原图做卷积计算
int index=0;
float startx=0.0;
float starty=0.0;
if(shift.x <=0.0)
{
startx = -2.0;
}
else {
  startx = 0.0;
}

if(shift.y <=0.0)
{
starty = -2.0;
}
else {
  starty = 0.0;
}

for(float y=0.0;y<coreSize;y++)
{
for(float x=0.0;x<coreSize;x++)
{

                        //原图像素点
                        //"currentColor=texture2D(m_texture,uv+vec2((-1.0+x)*texelOffset,(-1.0+y)*texelOffset));
currentColor=texture2D(m_texture,uv+vec2((startx+x)*texelOffset,(starty+y)*texelOffset));
                        //卷积计算
                        //卷积计算
color+=currentColor*kernel[index++];
}
}
                        //根据邻域内像素的加权平均灰度值去替代模板中心像素点的值
color/=16.0;
return color;
}


vec4 nearly(sampler2D m_texture,vec2 preuv,vec2 uv,float uT){
vec4 color1 = texture2D(m_texture, preuv);    //从纹理中采样出颜色值1
vec4 color2 = texture2D(m_texture, uv);     //从纹理中采样出颜色值2
vec4 color = color1*(1.0-uT) + color2*uT;// 混合两个颜色值
return color;
}

float needblur(sampler2D texture,vec2 uv,float fstepx,float fstepy){
	float alpha =texture2D(texture,vec2(uv.x,uv.y)).a;
if (alpha == 0.0) {
 return 1.0;
}
	alpha=texture2D(texture,vec2(uv.x-fstepx,uv.y-fstepy)).a;
if (alpha == 0.0) {
 return 1.0;
}
	alpha=texture2D(texture,vec2(uv.x+fstepx,uv.y-fstepy)).a;
if (alpha == 0.0) {
 return 1.0;
}
	alpha=texture2D(texture,vec2(uv.x+fstepx,uv.y+fstepy)).a;
if (alpha == 0.0) {
 return 1.0;
}
	alpha=texture2D(texture,vec2(uv.x-fstepx,uv.y+fstepy)).a;
if (alpha == 0.0) {
 return 1.0;
}
	return 0.0;
}

void main()
{
    vec4 color;
    if (needblur(texture,v_TexCoordinate,0.001,0.001) ==1.0) {
        if (needrepeat) {
            color = texture2D(oritexture, v_TexCoordinate);
        } else {
            color = blur(oritexture,v_TexCoordinate);
        }
    }else{
        color = texture2D(texture, v_TexCoordinate);
    }
    if (u_dark_strength != 0.0) {
        gl_FragColor = applyDark(color);
    } else {
        gl_FragColor = color;
    }
}