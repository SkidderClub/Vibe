#include "common.glsl"
varying vec3 vWorld;
#include "lighting.glsl"
uniform sampler2D uHeight;
uniform vec4 uHeightRect;   // x0, z0, width, depth of the height map
uniform vec2 uHeightRange;  // min, span

vec2 waveGrad(vec2 p) {
    float e = .5;
    float h = vnoise(p), hx = vnoise(p + vec2(e, 0.0)), hz = vnoise(p + vec2(0.0, e));
    return vec2(hx - h, hz - h) / e;
}

void main() {
    vec3 P = vWorld;
    vec3 toCam = uCameraPos - P;
    float dist = length(toCam);
    vec3 V = toCam / dist;
    float t = uTime;
    float detail = saturate(1.0 - dist / 600.0);
    vec2 g = waveGrad(P.xz * .045 + vec2(t * .018, t * .011)) * 2.2;
    g += waveGrad(P.xz * .13 - vec2(t * .045, -t * .03)) * 1.1 * (.4 + .6 * detail);
    g += waveGrad(P.xz * .55 + vec2(t * .09, t * .07)) * .35 * detail;
    g *= 1.0 + uRain * .8;
    vec3 N = normalize(vec3(-g.x * .6, 1.0, -g.y * .6));
    // Depth from the terrain height map: turquoise shallows, deep blue offshore, foam at the shore.
    vec2 huv = (P.xz - uHeightRect.xy) / uHeightRect.zw;
    float ground = texture2D(uHeight, huv).r * uHeightRange.y + uHeightRange.x;
    float depth = max(-1.2 - ground, 0.0);
    vec3 deep = vec3(.004, .022, .036), shallow = vec3(.06, .17, .15), sandy = vec3(.2, .22, .16);
    vec3 body = mix(mix(sandy, shallow, saturate(depth * 1.4)), deep, 1.0 - exp(-depth * .22));
    vec3 light = uSkyAmbient * 1.1 + uSunColor * saturate(uSunDir.y) * .6;
    body *= light;
    float NdotV = saturate(dot(N, V));
    float fres = .02 + .98 * pow(1.0 - NdotV, 5.0);
    vec3 R = reflect(-V, N);
    vec3 refl = environment(R, .03, 0.0);
    Surface s;
    s.albedo = vec3(0.0); s.normal = N; s.rough = .05; s.metal = 0.0; s.emissive = vec3(0.0);
    s.ao = 1.0; s.alpha = 1.0; s.f0 = .02; s.clearcoat = 0.0; s.sss = 0.0; s.indoor = 0.0;
    vec3 glint = brdf(N, V, uSunDir, s, vec3(.02), vec3(0.0), 0.0) * uSunColor * shadowFactor(P, vec3(0.0, 1.0, 0.0), dist);
    vec3 lamps = pointLights(P, N, V, s, vec3(.02), vec3(0.0));
    float foamNoise = vnoise(P.xz * .9 + vec2(t * .3, 0.0)) * vnoise(P.xz * 2.3 - t * .2);
    // Breaking lines of surf wash up and fade on the sand.
    float surf = sin(depth * 11.0 - t * 1.6 + vnoise(P.xz * .08) * 5.0) * .5 + .5;
    float shore = smoothstep(.45, .02, depth);
    float foam = saturate(shore * (surf * .8 + foamNoise * .9) - .35) * 1.4;
    vec3 color = body * (1.0 - fres) + refl * fres + glint + lamps;
    color = mix(color, light * .8, saturate(foam) * .75);
    color = applyFog(color, P);
    gl_FragColor = vec4(color, 1.0);
}
