highp float basicDithering (in highp vec2 st, highp float intensity) {
    return mix(-intensity/ 255., intensity/255., fract(sin(dot(st.xy, vec2(12.9898, 78.233)))* 43758.5453123));
}