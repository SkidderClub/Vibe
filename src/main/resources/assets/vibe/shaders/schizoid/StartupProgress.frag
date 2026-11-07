#version 120
uniform vec2 Origin, Size, Fill;
uniform float Scale, Time, Floor, Pool;
uniform vec3 Accent, Wax, Light;
uniform vec4 Drops[12], Forms[12], Necks[12];

float roundBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.)) + min(max(q.x, q.y), 0.) - radius;
}
float join(float a, float b, float radius) {
    float h = clamp(.5 + .5 * (b - a) / radius, 0., 1.);
    return mix(b, a, h) - radius * h * (1. - h);
}
float capsule(vec2 p, vec2 a, vec2 b, float radius) {
    vec2 delta = b - a;
    return length(p - a - delta * clamp(dot(p - a, delta) / max(.001, dot(delta, delta)), 0., 1.)) - radius;
}
vec4 over(vec4 behind, vec3 color, float opacity) {
    return vec4(color * opacity + behind.rgb * (1. - opacity), opacity + behind.a * (1. - opacity));
}
void main() {
    vec2 p = vec2(gl_FragCoord.x - Origin.x, Origin.y - gl_FragCoord.y) / Scale;
    float aa = .65 / Scale;
    float track = roundBox(p - Size * .5, Size * .5 + 1., Size.y * .5 + 1.);
    float liquid = 10000.;
    if (Fill.y > .01) {
        float ripple = .42 * sin(p.x * .048 - Time * 1.1) + .22 * sin(p.x * .105 + Time * .7);
        liquid = roundBox(p - vec2(Fill.x + Fill.y * .5, Size.y * .5 + ripple),
                vec2(Fill.y, Size.y) * .5, min(Size.y, Fill.y) * .5);
    }

    // Individual emissions have independent roots, sizes, tilt, pear shape and
    // lobes. Released wax retains its volume until it enters the lower pool.
    for (int i = 0; i < 12; i++) {
        vec4 drop = Drops[i], form = Forms[i], neck = Necks[i];
        if (drop.z < .01 || drop.w < .01) continue;
        vec2 delta = p - drop.xy;
        vec2 rotated = vec2(form.x * delta.x + form.y * delta.y, -form.y * delta.x + form.x * delta.y);
        vec2 shape = rotated / drop.zw;
        shape.x /= max(.50, 1. + form.z * clamp(shape.y, -1., 1.));
        float angle = atan(shape.y, shape.x);
        float outline = 1. + form.w * (.65 * sin(angle * 3. + neck.z) + .35 * cos(angle * 2. - neck.z));
        float distance = (length(shape) - outline) * min(drop.z, drop.w);
        liquid = join(liquid, distance, max(1.4, neck.w * .35));
        if (neck.y > .01) liquid = join(liquid,
                capsule(p, vec2(neck.x, Size.y - 1.), drop.xy, neck.y), max(1.8, neck.w * .45));
    }

    float outside = max(liquid, 0.);
    float glow = (.17 * exp(-outside / 6.5) + .24 * exp(-outside / 2.4)) * (1. - smoothstep(10., 16., outside));
    vec4 result = vec4(mix(Wax, Light, .35) * glow, glow);
    float trackMask = 1. - smoothstep(-aa, aa, track);
    float rim = exp(-abs(track) / .65);
    result = over(result, mix(vec3(.025, .04, .065), Accent, .055), trackMask * .90);
    result = over(result, mix(Accent, vec3(1.), .18), rim * .23);

    float coverage = 1. - smoothstep(-aa, aa, liquid);
    // A curved lit surface, rather than a flat neon line or a stack of blur passes.
    vec2 gradient = vec2(dFdx(liquid), -dFdy(liquid));
    gradient /= max(length(gradient), .001);
    float core = clamp(-liquid / (Size.y * .5), 0., 1.);
    float volume = sqrt(max(0., 1. - (1. - core) * (1. - core)));
    vec3 normal = normalize(vec3(gradient * (1. - core), max(.02, volume)));
    float diffuse = max(dot(normal, normalize(vec3(-.4, -.7, .8))), 0.);
    float flow = .5 + .5 * sin(p.x * .055 - Time * 1.7 + sin(p.y * .55) * .65);
    vec3 wax = mix(Wax * .68, Light, .18 + core * .37 + flow * .10) * (.68 + diffuse * .40);
    float specular = pow(max(dot(normal, normalize(vec3(-.25, -.5, 1.))), 0.), 20.);
    wax += mix(Light, vec3(1.), .3) * specular * .24;
    // Enter the existing luminous reservoir at its brightness, then merge below
    // the surface instead of drawing a dark bead over the pool.
    wax = mix(wax, Light, smoothstep(Pool - 12., Pool + 6., p.y) * .70);
    result = over(result, wax, coverage);
    float gloss = exp(-abs(p.y - 2.) / .65) * .22 * coverage * trackMask;
    result = over(result, mix(Light, vec3(1.), .25), gloss);
    float pool = (1. - smoothstep(Pool - 4., Pool + 10., p.y)) * (1. - smoothstep(Floor - 6., Floor + 4., p.y));
    gl_FragColor = vec4(result.rgb / max(result.a, .00001), clamp(result.a * pool, 0., 1.));
}
