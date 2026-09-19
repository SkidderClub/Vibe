#version 120
varying vec2 uv;
uniform sampler2D Tex0;
uniform vec2 Origin;
uniform vec2 Size;
uniform vec2 Texel;
uniform float Radius;
uniform float Time;
uniform float Blur;
uniform float Refraction;
uniform float Opacity;
uniform vec3 Tint;

float roundedMask(vec2 point, vec2 size, float radius) {
    vec2 q = abs(point - size * .5) - (size * .5 - vec2(radius));
    return length(max(q, 0.0)) - radius;
}

void main() {
    float signedDistance = roundedMask(uv - Origin, Size, Radius);
    if (signedDistance > 0.0) discard;
    float edge = smoothstep(0.0, -max(Texel.x, Texel.y) * 1.8, signedDistance);
    vec2 local = (uv - Origin) / Size;
    vec2 flow = vec2(sin(local.y * 37.0 + Time * 1.7), cos(local.x * 31.0 - Time * 1.3));
    vec2 point = uv + flow * Texel * Refraction;
    vec3 refracted = texture2D(Tex0, point).rgb;
    if (Blur > .001) {
        vec2 soft = Texel * Blur;
        vec3 around = texture2D(Tex0, point + vec2(soft.x, 0.0)).rgb
                + texture2D(Tex0, point - vec2(soft.x, 0.0)).rgb
                + texture2D(Tex0, point + vec2(0.0, soft.y)).rgb
                + texture2D(Tex0, point - vec2(0.0, soft.y)).rgb;
        refracted = mix(refracted, around * .25, min(.78, Blur * .13));
    }
    float sheen = pow(clamp(1.0 - local.y, 0.0, 1.0), 2.4) * .20;
    refracted = mix(refracted, Tint, .10 + sheen);
    gl_FragColor = vec4(refracted, Opacity * (.90 + sheen * .10) * edge);
}
