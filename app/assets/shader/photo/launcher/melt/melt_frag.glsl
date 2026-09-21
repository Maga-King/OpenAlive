#version 300 es

//熔化

precision highp float;

uniform float u_Time;
uniform vec2 u_Resolution;

out vec4 o_FragColor;

#define RADIANS 0.017453292519943295
//color1
uniform vec3 u_BackgroundColor1;
//color2
uniform vec3 u_BackgroundColor2;
//color3
uniform vec3 u_BackgroundColor3;
//融化参数1
int u_Zoom = 20;
//速度
float u_Speed = 20.0;
//亮度
float u_Brightness = 0.9;
//融合参数2
float u_FusionImpact = 2.0;
//密度
float u_Density = 3.0;
//分布程度
float u_DistributionDegree = 1.0;
//油腻程度1
float u_GreasyDegree1 = 0.5;
//油腻程度2
float u_GreasyDegree2 = 0.5;

float fScale = 1.25;

float cosRange(float degrees, float range, float minimum) {
    return (((u_GreasyDegree2 + cos(degrees * RADIANS)) * u_GreasyDegree1) * range) + minimum;
}

void main()
{
    float time = u_Time * u_Speed;
    vec2 p  = (u_FusionImpact * gl_FragCoord.xy-u_Resolution.xy)/max(u_Resolution.x,u_Resolution.y);
    float ct = cosRange(5.0, 3.0, 1.1);
    float xBoost = cosRange(0.2, 5.0, 5.0);
    float yBoost = cosRange(0.1, 10.0, 5.0);

    fScale = cosRange(15.5, 1.25, 0.5);

    for(int i = 1; i < u_Zoom; i++) {
        float _i = float(i);
        vec2 newp=p;
        newp.x+=0.25/_i*sin(_i*p.y+time*cos(ct)*0.5/20.0+0.005*_i)*fScale+xBoost;
        newp.y+=0.25/_i*sin(_i*p.x+time*ct*0.3/40.0+0.03*float(i+15))*fScale+yBoost;
        p=newp;
    }

    //颜色偏移
    //vec3 colUV = vec3(sin(0.5 * p.x), u_DistributionDegree * sin(u_Density * p.y), cos(0.0 * (p.x + p.y)));
    vec3 colUV=vec3(0.5*sin(3.0*p.x)+0.5,u_DistributionDegree*sin(u_Density*p.y) + 3.5,sin(p.x+p.y));
    //    vec3 col = mix(
    //    u_BackgroundColor2,
    //    mix(u_BackgroundColor1, u_BackgroundColor3, colUV.y),
    //    colUV.x);
    vec3 col = u_BackgroundColor2 * 1.05;
    col = mix(u_BackgroundColor2, col, colUV.y);
    col = mix(u_BackgroundColor3 * 1.1, col, colUV.x);
    col = mix(u_BackgroundColor1 * 1.1, col, colUV.z);
    col *= u_Brightness;

    o_FragColor = vec4(col, 1.0);
}
