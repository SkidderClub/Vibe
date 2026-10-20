#include "common.glsl"
uniform mat4 uInvViewProj;
uniform vec2 uWind;
varying vec2 vNdc;

float cloudNoise(vec2 p) {
    vec4 a = texture2D(uNoise, p), b = texture2D(uNoise, p * 2.7 + .13), c = texture2D(uNoise, p * 6.9 + .47);
    return a.b * .5 + a.a * .22 + b.b * .16 + b.a * .06 + c.b * .06;
}

void main() {
    vec4 far = uInvViewProj * vec4(vNdc, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w - uCameraPos);
    vec3 col = skyRadiance(dir);
    vec3 ext;
    scatter(dir, uSkySunDir, uSunE, ext);
    float cosSun = dot(dir, uSkySunDir);
    // Stars and the moon above the horizon at night.
    float starsVisible = uNight * uNight * (1.0 - uCloud) * saturate(dir.y * 4.0);
    if (starsVisible > .01) {
        vec2 sph = vec2(atan(dir.z, dir.x), asin(clamp(dir.y, -1.0, 1.0))) * 160.0;
        vec2 cell = floor(sph);
        float h = hash12(cell);
        float star = step(.9965, h) * smoothstep(.5, .0, length(fract(sph) - .5));
        float twinkle = .65 + .35 * sin(uTime * (2.0 + h * 9.0) + h * 40.0);
        col += star * twinkle * starsVisible * mix(vec3(.8, .85, 1.0), vec3(1.0, .85, .7), fract(h * 91.0)) * 2.5;
    }
    float moon = smoothstep(.99955, .99972, dot(dir, uMoonDir));
    col += moon * vec3(.9, .92, 1.0) * (.9 + .2 * vnoise(dir.xy * 900.0)) * 2.2 * saturate(uMoonDir.y * 6.0 + .2) * (1.0 - uCloud * .85);
    // Clouds on a curved layer 2 km up.
    float cloudAlpha = 0.0;
    vec3 cloudColor = vec3(0.0);
    if (dir.y > .005) {
        float t = 2000.0 / (dir.y + .06);
        vec2 p = (uCameraPos.xz + dir.xz * t) * .000045 + uWind * uTime;
        float n = cloudNoise(p);
        float cover = mix(.38, .92, uCloud);
        float density = smoothstep(1.0 - cover, 1.0 - cover + .32, n);
        float toward = cloudNoise(p + uSunDir.xz * .012);
        float lit = saturate(1.0 - (toward - n) * 5.0) * .7 + .3;
        float silver = pow(saturate(cosSun), 12.0) * (1.0 - density) * 3.0;
        vec3 sunLit = uSunColor * (1.6 * lit + silver) * (1.0 - uCloud * .6);
        vec3 ambient = uSkyAmbient * 1.25 + vec3(.004, .005, .008);
        cloudColor = ambient * mix(1.0, .55, density * uCloud) + sunLit * (1.0 - density * .45);
        cloudColor += uLightning * vec3(1.4, 1.45, 1.6) * density;
        cloudAlpha = density * saturate(dir.y * 9.0) * (.55 + .45 * saturate(uCloud + .45));
    }
    // Sun disc, hidden behind cloud.
    float disc = smoothstep(.99988, .99994, cosSun);
    col += ext * disc * uSunE * .06 * (1.0 - cloudAlpha * .92);
    col += ext * pow(saturate(cosSun), 600.0) * uSunE * .004 * (1.0 - uCloud * .7);
    col = mix(col, cloudColor, cloudAlpha);
    // Distant haze near the horizon matches the fog.
    col = mix(col, skyRadiance(normalize(vec3(dir.x, .06, dir.z))), saturate(saturate(1.0 - dir.y * 12.0) * uFogDensity * 350.0));
    gl_FragColor = vec4(col, 1.0);
}
