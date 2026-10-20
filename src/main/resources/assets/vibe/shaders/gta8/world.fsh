#include "common.glsl"
varying vec3 vWorld;
varying vec3 vNormal;
varying vec4 vColor;
varying vec2 vUV;
varying vec4 vMat;
#include "lighting.glsl"
#include "materials.glsl"
uniform float uTransparent;
uniform vec4 uOccA[16]; // centre xyz, half length
uniform vec4 uOccB[16]; // forward xz, half width, height
uniform int uOccCount;

// Sky light blocked by the cars and people standing on a surface: a soft box falloff around each footprint.
float actorOcclusion(vec3 P, vec3 N) {
    float occ = 1.0;
    for (int i = 0; i < 16; i++) {
        if (i >= uOccCount) break;
        vec3 d = P - uOccA[i].xyz;
        float h = d.y;
        if (h < -.4 || h > .9) continue;
        vec2 f = uOccB[i].xy;
        vec2 local = vec2(dot(d.xz, f), dot(d.xz, vec2(-f.y, f.x)));
        vec2 q = abs(local) - vec2(uOccA[i].w, uOccB[i].z);
        float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
        float reach = uOccA[i].w > .5 ? .9 : .45;
        float k = smoothstep(reach, -.5, sd) * smoothstep(.9, .05, h) * saturate(N.y * 1.2);
        occ *= 1.0 - .82 * k;
    }
    return occ;
}

vec2 ripple(vec2 p, float t) {
    vec2 sum = vec2(0.0);
    for (int i = 0; i < 2; i++) {
        vec2 q = p * 2.4 + float(i) * 7.31;
        vec2 cell = floor(q), f = fract(q) - .5;
        vec2 c = vec2(hash12(cell), hash12(cell + 3.7)) - .5;
        float ph = fract(t * 1.4 + hash12(cell + 9.1));
        vec2 d = f - c * .5;
        float r = length(d);
        float ring = sin((r - ph * .55) * 42.0) * exp(-abs(r - ph * .5) * 16.0) * (1.0 - ph);
        sum += d / max(r, 1e-3) * ring;
    }
    return sum * .45;
}

void main() {
    float id = floor(vMat.x + .5);
    float indoor = step(127.5, id);
    id -= indoor * 128.0;
    float seed = floor(vMat.y + .5), param = floor(vMat.z + .5), extra = floor(vMat.w + .5);
    vec3 P = vWorld;
    vec3 toCam = uCameraPos - P;
    float dist = length(toCam);
    vec3 V = toCam / max(dist, 1e-4);
    Surface s;
    s.albedo = vColor.rgb;
    s.normal = normalize(vNormal);
    if (!gl_FrontFacing) s.normal = -s.normal;
    vec3 geometric = s.normal;
    s.rough = .8; s.metal = 0.0; s.emissive = vec3(0.0); s.ao = vColor.a; s.alpha = 1.0;
    s.f0 = .04; s.clearcoat = 0.0; s.sss = 0.0; s.indoor = indoor;

    if (id == 0.0) { s.rough = .72; s.albedo *= .95 + .08 * vnoise(P.xz * 1.3 + P.y * 1.7); }
    else if (id == 1.0) asphalt(s, P, vUV, param, extra, true);
    else if (id == 33.0) asphalt(s, P, vUV, param, extra, false);
    else if (id == 2.0) concreteSlabs(s, P, 1.5);
    else if (id == 3.0) brick(s, vUV);
    else if (id == 4.0) plaster(s, vUV);
    else if (id == 5.0) windowFacade(s, P, V, vUV, param, seed);
    else if (id == 6.0) curtainWall(s, P, V, vUV, param, seed);
    else if (id == 7.0) roof(s, P);
    else if (id == 8.0) grass(s, P);
    else if (id == 9.0) { s.metal = .55; s.rough = .25 + param / 340.0; s.albedo *= .95 + .06 * vnoise(vUV * 6.0); }
    else if (id == 10.0) wood(s, vUV, .18);
    else if (id == 11.0) emissive(s, param, seed, extra);
    else if (id == 12.0) sand(s, P);
    else if (id == 13.0) terrain(s, P);
    else if (id == 14.0) {
        vec2 cellUv = (vec2(mod(param, 2.0), floor(param / 2.0)) + clamp(vUV, .004, .996)) * .5;
        vec4 t = texture2D(uFoliage, cellUv);
        s.alpha = t.a;
        s.albedo = vColor.rgb * t.rgb * (.85 + .3 * vnoise(P.xz * .7 + seed));
        s.rough = .75; s.sss = .6;
        s.normal = normalize(s.normal + vec3(0.0, .6, 0.0));
    }
    else if (id == 15.0) carPaint(s, param, P);
    else if (id == 16.0) { s.albedo = vec3(.9, .9, .92); s.metal = 1.0; s.rough = .08 + uDamage * .5; }
    else if (id == 17.0) { s.albedo = vec3(.035); s.rough = .88; }
    else if (id == 18.0) { s.albedo = vec3(.012, .015, .018); s.rough = .03 + uDamage * .4; s.f0 = .08; }
    else if (id == 19.0) character(s, param, P);
    else if (id == 20.0) corrugated(s, vUV);
    else if (id == 21.0) shopfront(s, P, V, vUV, param, seed, extra * .25);
    else if (id == 22.0) signMaterial(s, step(.5, param));
    else if (id == 23.0) tileRoof(s, vUV);
    else if (id == 24.0) siding(s, vUV);
    else if (id == 25.0) floorTile(s, P);
    else if (id == 26.0) pavers(s, P);
    else if (id == 27.0) { s.albedo *= .85 + .3 * fbm(P.xz * .7); s.rough = .95; }
    else if (id == 28.0) { s.albedo *= .75 + .35 * vnoise(vUV * vec2(6.0, 1.2)); s.rough = .95; s.normal = bump(s.normal, vec2(sin(vUV.x * 40.0) * .2, 0.0)); }
    else if (id == 29.0) pool(s, P, V);
    else if (id == 30.0) chainlink(s, vUV);
    else if (id == 31.0) wood(s, vec2(P.x, P.z), .24);
    else if (id == 32.0) { s.albedo = vec3(.6); s.rough = .2; s.emissive += vColor.rgb * uNight * 14.0; }
    else if (id == 34.0) { s.rough = .3 + param / 400.0; s.albedo *= .97; }
    else if (id == 35.0) { s.albedo = vec3(0.0); s.rough = .03; s.f0 = .05; }
    else if (id == 36.0) concreteWall(s, vUV, 2.5);
    else if (id == 37.0) { s.albedo *= .9 + .12 * fbm(P.xz * 1.5 + P.y); s.rough = .12; }
    else if (id == 38.0) {
        float stripe = step(.5, fract(vUV.x / .45));
        s.albedo = mix(vColor.rgb, vec3(.92, .9, .86), stripe * step(.5, param));
        s.rough = .85; s.sss = .3;
    }
    else if (id == 39.0) stone(s, vUV);

    if (s.alpha < .5 && uTransparent < .5) discard;
    // Contact shadow where walls and street furniture meet the ground.
    if (abs(geometric.y) < .5 && id != 19.0 && id != 11.0) s.ao *= mix(.5, 1.0, saturate((P.y - .1) / 1.3));
    if (uOccCount > 0 && geometric.y > .3) s.ao *= actorOcclusion(P, geometric);

    // Rain: darker porous materials, glossy films, puddles with raindrop rings.
    if (uWet > .001 && indoor < .5) {
        float up = saturate(geometric.y * 1.25 - .2);
        float porous = (id == 1.0 || id == 33.0 || id == 2.0 || id == 26.0 || id == 7.0 || id == 12.0 || id == 13.0 || id == 27.0) ? 1.0 : .45;
        float puddle = porous > .9 && id != 12.0 && id != 13.0 ? smoothstep(.64 - .28 * uWet, .67 - .28 * uWet, fbm(P.xz * .16)) * up : 0.0;
        s.albedo *= mix(1.0, .6, uWet * porous);
        s.rough = mix(s.rough, s.rough * .4 + .05, uWet * max(up, .3));
        // Specular anti-aliasing: glossy films get rougher with distance so they never sparkle.
        float footprint = saturate(max(fwidth(P.x), fwidth(P.z)) * 6.0);
        s.rough = mix(s.rough, max(.035, .06 + footprint * .25), puddle * uWet);
        s.albedo *= 1.0 - .35 * puddle;
        if (uRain > .05) s.normal = bump(s.normal, ripple(P.xz, uTime) * puddle * uRain * saturate(1.0 - dist / 22.0) * .5);
        s.normal = normalize(mix(s.normal, geometric, puddle * .8));
    }

    vec3 color = shade(s, P, V, dist);
    if (uTransparent > .5) {
        float fres = .04 + .96 * pow(1.0 - saturate(dot(s.normal, V)), 5.0);
        color = applyFog(color, P);
        gl_FragColor = vec4(color + vec3(.002, .003, .003), mix(uTransparent > 1.5 ? .74 : .1, 1.0, fres));
        return;
    }
    color = applyFog(color, P);
    gl_FragColor = vec4(color, 1.0);
}
