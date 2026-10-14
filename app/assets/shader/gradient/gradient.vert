#version 300 es
#define PI 3.1415926535897932384626433832795
#define TWO_PI 2. * PI
precision highp float;

in vec3 a_position;
in vec2 a_texCoord0;

uniform mat4 u_projection_view_matrix;
uniform float u_time;

uniform vec4 u_color_a_outer;
uniform vec4 u_color_a_cp1;
uniform vec4 u_color_a_cp2;
uniform vec4 u_color_a_cp3;
uniform vec4 u_color_a_cp4;
uniform vec4 u_color_a_cp5;
uniform vec4 u_color_a_cp6;
uniform vec4 u_color_a_inner;

uniform vec4 u_color_b_outer;
uniform vec4 u_color_b_cp1;
uniform vec4 u_color_b_cp2;
uniform vec4 u_color_b_cp3;
uniform vec4 u_color_b_cp4;
uniform vec4 u_color_b_cp5;
uniform vec4 u_color_b_cp6;
uniform vec4 u_color_b_inner;

struct PolynomialFunction {
    float from;
    float to;
    vec4 coefficients;
};

struct PolynomialSpline {
    PolynomialFunction func1;
    PolynomialFunction func2;
    PolynomialFunction func3;
    PolynomialFunction func4;
};

uniform PolynomialSpline u_spline1;
uniform PolynomialSpline u_spline2;
uniform PolynomialSpline u_spline3;
uniform PolynomialSpline u_spline4;
uniform PolynomialSpline u_spline5;
uniform PolynomialSpline u_spline6;

out vec4 v_color_outer;
out vec4 v_color_1;
out vec4 v_color_2;
out vec4 v_color_3;
out vec4 v_color_4;
out vec4 v_color_5;
out vec4 v_color_6;
out vec4 v_color_inner;

out highp vec2 v_uv;
out highp float v_curve_1;
out highp float v_curve_2;
out highp float v_curve_3;
out highp float v_curve_4;
out highp float v_curve_5;
out highp float v_curve_6;

float calc_polynomial(float x, PolynomialFunction func) {
    x = clamp(x, func.from, func.to);
    x -= func.from;
    return func.coefficients.x + func.coefficients.y * x + func.coefficients.z * x*x + func.coefficients.w * x*x*x;
}

float my_spline_curve(float x, PolynomialSpline spline) {
    if (x < spline.func1.to) {
        return calc_polynomial(x, spline.func1);
    } else if (x >= spline.func2.from && x < spline.func2.to) {
        return calc_polynomial(x, spline.func2);
    } else if (x >= spline.func3.from && x < spline.func3.to) {
        return calc_polynomial(x, spline.func3);
    } else {
        return calc_polynomial(x, spline.func4);
    }
}

void updateGradientPoints(in vec2 uv, in float time) {
    v_curve_1 = my_spline_curve(uv.x, u_spline1);
    v_curve_2 = my_spline_curve(uv.x, u_spline2);
    v_curve_3 = my_spline_curve(uv.x, u_spline3);
    v_curve_4 = my_spline_curve(uv.x, u_spline4);
    v_curve_5 = my_spline_curve(uv.x, u_spline5);
    v_curve_6 = my_spline_curve(uv.x, u_spline6);
    //    v_curve_1 = 0.1;
    //    v_curve_2 = 0.2;
    //    v_curve_3 = 0.3;
    //    v_curve_4 = 0.4;
    //    v_curve_5 = 0.5;
    //    v_curve_6 = 0.6;
}

float sin_normalized(float rad, float size) {
    float halfSize = size * 0.5;
    return (halfSize + halfSize * sin(rad * TWO_PI));
}

void updateGradientColors(in vec2 uv, in float time) {

    float scaledTime = time * -0.08f + 53.89;
    float colorVariation = sin_normalized(uv.x * 0.25f + scaledTime, 1.);

    v_color_inner = mix(u_color_a_inner, u_color_b_inner, sin_normalized(uv.x* 0.25f + scaledTime + 334.34, 1.));
    v_color_1 = mix(u_color_a_cp1, u_color_b_cp1, sin_normalized(uv.x* 0.15f + scaledTime + 2344.3, 1.));
    v_color_2 = mix(u_color_a_cp2, u_color_b_cp2, sin_normalized(uv.x* 0.20f + scaledTime + 242.3, 1.));
    v_color_3 = mix(u_color_a_cp3, u_color_b_cp3, sin_normalized(uv.x* 0.12f + scaledTime + 64.3, 1.));
    v_color_4 = mix(u_color_a_cp4, u_color_b_cp4, sin_normalized(uv.x* 0.20f + scaledTime + 234.3, 1.));
    v_color_5 = mix(u_color_a_cp5, u_color_b_cp5, sin_normalized(uv.x* 0.17f + scaledTime + 3634.3, 1.));
    v_color_6 = mix(u_color_a_cp6, u_color_b_cp6, sin_normalized(uv.x* 0.05f + scaledTime + 2.3, 1.));
    v_color_outer = mix(u_color_a_outer, u_color_b_outer, sin_normalized(uv.x * 0.15f + scaledTime + 334.3, 1.));
}


/**
 *  (0, 0)-------(1, 0)
 *     |            |
 *     |            |
 *     |            |
 *  (0, 1)-------(1, 1)
 */
void main()  {
    v_uv = a_texCoord0;
    updateGradientPoints(v_uv, u_time);
    updateGradientColors(v_uv, u_time);
    gl_Position = u_projection_view_matrix * vec4(a_position, 1.0);
}