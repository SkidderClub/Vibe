#version 120
uniform sampler2D Tex0;
uniform vec2 Origin;
uniform vec2 Size;
uniform vec2 Texel;
uniform float Radius;
uniform float Scale;
uniform float Blur;
uniform float Refraction;
uniform float Opacity;
uniform vec3 Tint;

vec3 sceneAt(vec2 point) {
    if (Blur < .001) return texture2D(Tex0, point).rgb;
    vec2 stepSize = Texel * Blur * Scale;
    // Normalized tent kernel; keep the center clear at the default low blur.
    vec3 color = texture2D(Tex0, point).rgb * 4.0;
    color += (texture2D(Tex0, point + vec2(stepSize.x, 0.0)).rgb
            + texture2D(Tex0, point - vec2(stepSize.x, 0.0)).rgb
            + texture2D(Tex0, point + vec2(0.0, stepSize.y)).rgb
            + texture2D(Tex0, point - vec2(0.0, stepSize.y)).rgb) * 2.0;
    color += texture2D(Tex0, point + stepSize).rgb
            + texture2D(Tex0, point - stepSize).rgb
            + texture2D(Tex0, point + vec2(stepSize.x, -stepSize.y)).rgb
            + texture2D(Tex0, point + vec2(-stepSize.x, stepSize.y)).rgb;
    return color / 16.0;
}

void main() {
    // Pixel-space distance keeps circles round on widescreen and at GUI scale 2+.
    vec2 p = gl_FragCoord.xy - Origin - Size * .5;
    vec2 q = abs(p) - (Size * .5 - vec2(Radius));
    vec2 corner = max(q, 0.0);
    float distance = length(corner) + min(max(q.x, q.y), 0.0) - Radius;
    float coverage = 1.0 - smoothstep(-.75, .75, distance);
    if (coverage <= 0.0) discard;

    vec2 normal;
    if (dot(corner, corner) > .0001) normal = normalize(corner) * sign(p);
    else normal = q.x > q.y ? vec2(sign(p.x), 0.0) : vec2(0.0, sign(p.y));
    float depth = max(-distance, 0.0);
    float bevel = min(max(Radius, 6.0 * Scale), min(Size.x, Size.y) * .35);
    float curved = 1.0 - smoothstep(0.0, max(bevel, .001), depth);

    // A flat center and a curved rim: the scene bends with the surface normal,
    // rather than swimming under a time-dependent sine-wave displacement.
    vec2 offset = -normal * pow(curved, 1.45) * Refraction * Scale * 2.8;
    vec2 point = (gl_FragCoord.xy + offset) * Texel;
    point = clamp(point, Texel * .5, vec2(1.0) - Texel * .5);
    vec3 color = sceneAt(point);
    color *= mix(vec3(1.0), Tint, .22);

    // Directional highlights on the bevel, with a narrow polished outer rim
    // and a darker inner band. The middle stays transparent and scene-colored.
    float lighting = dot(normal, normalize(vec2(-.45, .85)));
    float reflection = pow(curved, 3.0);
    float rim = 1.0 - smoothstep(.35 * Scale, 1.35 * Scale, depth);
    float innerBand = exp(-pow((depth - bevel * .65) / max(Scale * 1.1, bevel * .18), 2.0));
    color *= 1.0 - innerBand * .16 - reflection * max(-lighting, 0.0) * .12;
    float shine = reflection * (.035 + .20 * pow(max(lighting, 0.0), 2.0))
            + rim * (.10 + .32 * abs(lighting));
    color = mix(color, vec3(1.0), clamp(shine, 0.0, .65));
    gl_FragColor = vec4(color, Opacity * coverage);
}
