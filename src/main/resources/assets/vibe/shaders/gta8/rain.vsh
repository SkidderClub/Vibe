#include "common.glsl"
attribute vec3 aPos;   // seed in the unit cube
attribute vec2 aUV;    // x: side, y: along the streak
uniform mat4 uViewProj;
uniform vec3 uBox;
uniform vec3 uFall;    // velocity in m/s
varying vec2 vUV;
varying vec3 vWorld;
varying float vFade;

void main() {
    vec3 motion = uFall * uTime;
    vec3 local = fract((aPos * uBox - uCameraPos + motion) / uBox) - .5;
    vec3 base = uCameraPos + local * uBox;
    vec3 dir = normalize(uFall);
    vec3 toCam = normalize(uCameraPos - base);
    vec3 side = normalize(cross(dir, toCam));
    float len = .55 + aPos.x * .35;
    vec3 p = base + side * aUV.x * .012 + dir * aUV.y * len;
    vFade = saturate(1.0 - length(local.xz) * 2.0) * saturate(length(base - uCameraPos) - .4);
    vUV = aUV;
    vWorld = p;
    gl_Position = uViewProj * vec4(p, 1.0);
}
