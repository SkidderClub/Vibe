#include "common.glsl"
varying vec2 vUV;
varying vec4 vColor;
varying vec4 vMat;
varying vec3 vWorld;

void main() {
    float type = floor(vMat.z + .5);
    float r = length(vUV);
    vec3 c = vColor.rgb;
    float a = vColor.a;
    if (type > 9.5) {
        // Surface decals: skids, bullet holes, scorch marks and blood.
        float kind = type - 10.0, alpha;
        float n = vnoise(vWorld.xz * 3.0 + vWorld.y);
        if (kind < .5) alpha = (1.0 - vUV.x * vUV.x) * smoothstep(1.0, .8, abs(vUV.y)) * .5 * a * (.7 + .3 * n);
        else if (kind < 1.5) { alpha = smoothstep(1.0, .55, r) * .95 * a; c = mix(vec3(.02), c, smoothstep(.2, .8, r)); }
        else if (kind < 2.5) alpha = smoothstep(1.0, .1, r + (n - .5) * .5) * .88 * a;
        else alpha = smoothstep(1.0, .2, r + (n - .5) * .6) * .85 * a;
        gl_FragColor = vec4(applyFog(c, vWorld) * alpha * .6, alpha);
        return;
    }
    if (r > 1.0) discard;
    // Output is premultiplied: alpha 0 means purely additive light.
    if (type < .5 || type == 6.0 || type == 4.0 || type == 5.0) {
        // Smoke, dust, blood mist and spray: soft billowing discs lit by the sky and sun.
        float n = vnoise(vUV * 3.0 + vWorld.xz * .7 + vMat.y * 3.0) * .6 + vnoise(vUV * 7.0 - vMat.y * 5.0) * .4;
        float alpha = saturate((1.0 - r * r) * (.55 + n * .8) - .15) * a;
        vec3 lit = c * (uSkyAmbient * 1.4 + uSunColor * (.45 + .55 * saturate(dot(normalize(vec3(vUV, .6)), uSunDir))) + uLightning);
        lit += c * uNight * .05;
        vec3 color = applyFog(lit, vWorld);
        gl_FragColor = vec4(color * alpha, alpha);
    } else if (type == 1.0) {
        // Fire: hot core fading to orange, emissive.
        float n = vnoise(vUV * 4.0 + vec2(0.0, -uTime * 3.0) + vMat.y);
        float heat = saturate((1.0 - r) * (.6 + n)) * a;
        vec3 color = mix(vec3(1.0, .25, .03), vec3(1.0, .75, .35), heat) * heat * 14.0;
        gl_FragColor = vec4(color, heat * .2);
    } else if (type == 2.0 || type == 7.0 || type == 8.0 || type == 9.0) {
        // Sparks, muzzle flashes and light coronas.
        float core = type == 8.0 ? pow(saturate(1.0 - r), 2.2) : saturate(1.0 - r * r);
        if (type == 7.0) core *= .6 + .4 * abs(sin(atan(vUV.y, vUV.x) * 3.0 + vMat.y));
        gl_FragColor = vec4(c * core * a, 0.0);
    } else {
        // Debris chips and glass shards: small solid flecks.
        float alpha = step(r, .8) * a;
        vec3 lit = c * (uSkyAmbient + uSunColor * .8);
        gl_FragColor = vec4(applyFog(lit, vWorld) * alpha, alpha);
    }
}
