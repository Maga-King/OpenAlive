#version 300 es

//以太

precision highp float;

uniform float u_Time;
uniform vec2 u_Resolution;

out vec4 o_FragColor;

//color1
uniform vec3 u_BackgroundColor1;
//color2
uniform vec3 u_BackgroundColor2;

//整体速度
float u_Speed = 0.15;
//大小
float u_Size = 1.0;
//X方向
float u_X = 0.5;
//y方向
float u_Y = 0.5;
//锐化
float u_Sharpening = 0.8;
//油腻度
float u_Greasiness = 4.5;
//融合度
float u_FusionDegree = 1.0;
//厚重感
float u_Massiness = 0.7;

mat2 mat(float a) {
    float c=cos(a), s=sin(a);
    return mat2(c,-s,s,c);
}

float map(vec3 p, float t, float s){
    p.xz*= mat(t*0.4);
    p.xy*= mat(t*0.3);
    vec3 q = p*3.+t;
    return smoothstep(-1.0, 1.0,length(p + vec3(sin(t*0.7))) * log(length(p)+0.) + sin(q.x+sin(q.z+sin(q.y)))*s - 1.5);
}

void main() {
    float iTime = u_Time * u_Speed;
    vec2 uv = gl_FragCoord.xy/u_Resolution.y - vec2(u_X, u_Y);
    vec3 color = vec3(0.);
    float d = 2.5;

    for(int i=0; i<=5; i++)	{
        vec3 p = vec3(0,0,5.0) + normalize(vec3(uv, -u_Size))*d;
        float rz = map(p, iTime, u_Sharpening);
        float f =  clamp((rz - map((p+0.1), iTime, u_Sharpening))*0.1, -0.1, 1.0 );
        vec3 l = vec3(0.0314, 0.549, 0.8078) + vec3(1.0, u_Greasiness, 3.0)*f;
        color = color*l + smoothstep(u_FusionDegree, 0.0, rz)*u_Massiness*l;
        d += min(rz, 1.);
    }

    color = mix(u_BackgroundColor2, mix (u_BackgroundColor1, color, color.x), color.y);

    o_FragColor = vec4(color, 1.);
}
