#include "common.glsl"
attribute vec3 aPos;
uniform mat4 uViewProj;
uniform vec2 uOffset;
varying vec3 vWorld;

void main() {
    vec3 p = aPos + vec3(uOffset.x, 0.0, uOffset.y);
    // Gentle swell near the camera; the far grid stays flat.
    float fade = saturate(1.0 - length(p.xz - uCameraPos.xz) / 220.0);
    p.y += (sin(p.x * .07 + uTime * .9) * .18 + sin(p.z * .11 - uTime * 1.1) * .12 + sin((p.x + p.z) * .05 + uTime * .6) * .2) * fade;
    vWorld = p;
    gl_Position = uViewProj * vec4(p, 1.0);
}
