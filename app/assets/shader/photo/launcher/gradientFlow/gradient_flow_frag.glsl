#version 300 es

//梯度流

precision highp float;

uniform float u_Time;
uniform vec2 u_Resolution;

out vec4 o_FragColor;

//color1
uniform vec3 u_BackgroundColor1;
//color2
uniform vec3 u_BackgroundColor2;
//color3
uniform vec3 u_BackgroundColor3;

//速度
float u_Speed = 2.0;
//亮度
float u_Brightness = 1.0;

//上下偏移
float u_UpDownOffset = 0.40;
//参数2
float u_Parameter2 = 1.0;
//频率
float u_Frequency = 5.0;
//振幅
float u_Amplitude = 30.0;
//参数5
float u_Parameter5 = 1.5;
//参数6
float u_Parameter6 = 0.5;
//参数7
float u_Parameter7 = -0.1;
//参数8
float u_Parameter8 = 0.5;
//参数9
float u_Parameter9 = -5.0;
//参数10
float u_Parameter10 = -1.1;
//参数11
float u_Parameter11 = 0.5;
//参数12
float u_Parameter12 = -5.0;
//参数13
float u_Parameter13 = 0.5;
//参数14
float u_Parameter14 = -0.3;

//扭转系数1
float u_TorsionCoefficient1 = 590.0;
//扭转系数2
float u_TorsionCoefficient2 = 145.0;

//噪音
float u_NoiseV = 2.0;
//噪音偏移
float u_NoiseOffset = -1.0;

mat2 Rot(float a)
{
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

vec2 hash( vec2 p )
{
    p = vec2( dot(p,vec2(2127.1,81.17)), dot(p,vec2(1269.5,283.37)) );
    return fract(sin(p)*43758.5453);
}

float noise( in vec2 p, float v, float offset)
{
    vec2 i = floor( p );
    vec2 f = fract( p );

    vec2 u = f * f * (3.0 - 2.0 * f);

    float n = mix( mix( dot( offset + v * hash( i + vec2(0.0,0.0) ), f - vec2(0.0,0.0) ),
                        dot( offset + v * hash( i + vec2(1.0,0.0) ), f - vec2(1.0,0.0) ), u.x),
                   mix( dot( offset + v * hash( i + vec2(0.0,1.0) ), f - vec2(0.0,1.0) ),
                        dot( offset + v * hash( i + vec2(1.0,1.0) ), f - vec2(1.0,1.0) ), u.x), u.y);
    return 0.5 + 0.5 * n;
}

void main() {
    float iTime = u_Time * u_Speed;

    vec2 uv = gl_FragCoord.xy / u_Resolution.xy;
    float ratio = u_Resolution.x / u_Resolution.y;

    vec2 tuv = uv;
    tuv -= u_UpDownOffset;

    // rotate with Noise
    float degree = noise(vec2(iTime*.1, tuv.x*tuv.y), u_NoiseV, u_NoiseOffset);

    tuv.y *= u_Parameter2 / ratio;
    tuv *= Rot(radians((degree-.5) * u_TorsionCoefficient1 + u_TorsionCoefficient2));
    tuv.y *= ratio;


    // Wave warp with sin
    float frequency = u_Frequency;
    float amplitude = u_Amplitude;

    tuv.x += sin(tuv.y * frequency+iTime) / amplitude;
    tuv.y += sin(tuv.x * frequency * u_Parameter5 + iTime) / (amplitude * u_Parameter6);


    // draw the image

    vec3 layer1 = mix(u_BackgroundColor3, u_BackgroundColor2, smoothstep(u_Parameter7, u_Parameter8, (tuv*Rot(radians(u_Parameter9))).y));

    vec3 layer2 = mix(layer1, u_BackgroundColor1, smoothstep(u_Parameter10, u_Parameter11, (tuv*Rot(radians(u_Parameter12))).x));

    vec3 finalComp = mix(layer1, layer2, smoothstep(u_Parameter13, u_Parameter14, tuv.y));

    vec3 col = finalComp * u_Brightness;

    o_FragColor = vec4(col,1.0);
}
