precision highp float;
varying vec2 uv;
uniform sampler2D photo;
uniform vec2 resolution;
uniform vec2 offset;
uniform float photoAspect;
uniform float hasPhoto;
uniform float ambient;
uniform float home;
uniform float blurStrength;
uniform float shape;
uniform float dim;

vec3 aurora(vec2 p) {
    p-=0.5;
    float a=sin(p.y*4.5+p.x*2.0+home*0.35)*0.12;
    float ribbon=exp(-pow((p.x+a-0.04)*5.2,2.0));
    float light=exp(-length((p-vec2(-0.17,0.16))*vec2(2.8,1.6))*2.0);
    vec3 c=mix(vec3(0.025,0.045,0.10),vec3(0.12,0.46,0.46),light);
    c+=ribbon*vec3(0.20,0.39,0.31)*(0.65+p.y*0.45);
    float silk=exp(-pow((p.x+a+0.12)*17.0,2.0));
    c+=silk*vec3(0.50,0.59,0.38)*0.33;
    return c;
}
vec3 source(vec2 p) {
    if(hasPhoto<0.5) return aurora(p);
    float screenAspect=resolution.x/resolution.y;
    vec2 crop=vec2(min(1.0,screenAspect/photoAspect),min(1.0,photoAspect/screenAspect));
    vec2 q=(p-0.5)*crop+0.5;
    return texture2D(photo,clamp(vec2(q.x,1.0-q.y),0.001,0.999)).rgb;
}
void main() {
    float aspect=resolution.x/resolution.y;
    vec2 point=(uv-0.5)*vec2(aspect,1.0)-offset*ambient;
    float radius=mix(1.5,aspect*0.27,ambient);
    float circle=length(point)-radius;
    vec2 bounds=vec2(radius*0.83,radius*1.13);
    vec2 box=abs(point)-bounds+radius*0.16;
    float rounded=length(max(box,0.0))+min(max(box.x,box.y),0.0)-radius*0.16;
    float distance=mix(circle,rounded,shape);
    float edge=1.0-smoothstep(-1.5/resolution.y,1.5/resolution.y,distance);
    if(edge<0.001) { gl_FragColor=vec4(0.0,0.0,0.0,1.0); return; }
    float zoom=1.0+home*0.12+ambient*0.04;
    vec2 p=(uv-0.5-offset*ambient)/zoom+0.5;
    vec3 c=source(p);
    float b=home*blurStrength*0.016;
    if(b>0.00001) {
        c*=0.2;
        c+=source(p+vec2(b,0.0))*0.1; c+=source(p-vec2(b,0.0))*0.1;
        c+=source(p+vec2(0.0,b))*0.1; c+=source(p-vec2(0.0,b))*0.1;
        c+=source(p+vec2(b,b))*0.1; c+=source(p-vec2(b,b))*0.1;
        c+=source(p+vec2(b,-b))*0.1; c+=source(p+vec2(-b,b))*0.1;
    }
    c*=mix(1.0,dim,ambient)*(1.0-home*0.12);
    gl_FragColor=vec4(c*edge,1.0);
}
