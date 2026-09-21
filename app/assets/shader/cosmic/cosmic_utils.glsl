float hash(float n) {
    return fract(sin(n) * 753.5453123);
}

float noise(in vec3 floor, in vec3 fract) {
    vec3 p = floor;
    vec3 f = fract;
    f = f*f*(3.0-2.0*f);

    float n = p.x + p.y * 157.0 + 113.0 * p.z;
    return mix(mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),
    mix(hash(n + 157.0), hash(n + 158.0), f.x), f.y),
    mix(mix(hash(n + 113.0), hash(n + 114.0), f.x),
    mix(hash(n + 270.0), hash(n + 271.0), f.x), f.y), f.z);
}

float precision_noise(in vec3 base, in vec3 time) {
    vec3 _fract = fract(base) + fract(time);
    vec3 _floor = floor(base) + floor(time) + floor(_fract);
    _fract = fract(_fract);
    return noise(_floor, _fract);
}

vec3 noisePosition(in vec3 position, float time, float amplitude) {
    return position * (1.0 + precision_noise(position, vec3(time)) * 0.35 * amplitude);
}

vec3 orthogonal(in vec3 v) {
    return normalize(abs(v.x) > abs(v.z) ? vec3(-v.y, v.x, 0.0) : vec3(0.0, -v.z, v.y));
}

vec3 calcNormal(in vec3 position, float time, float amplitude) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition(position, time, amplitude);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition(nearby1, time, amplitude);
    vec3 distorted2 = noisePosition(nearby2, time, amplitude);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}

vec3 noisePosition1(in vec3 position, float amplitude, float noise) {
    return position * (1.0 + noise * 0.35 * amplitude);
}

vec3 calcNormal1(in vec3 position, float amplitude, float noise) {
    float tangentFactor = 0.0001;
    vec3 distortedPosition = noisePosition1(position, amplitude, noise);
    vec3 tangent1 = orthogonal(position);
    vec3 nearby1 = position + tangent1 * tangentFactor;
    vec3 nearby2 = position + normalize(cross(position, tangent1)) * tangentFactor;
    vec3 distorted1 = noisePosition1(nearby1, amplitude, noise);
    vec3 distorted2 = noisePosition1(nearby2, amplitude, noise);

    return normalize(cross(distorted1 - distortedPosition, distorted2 - distortedPosition));
}