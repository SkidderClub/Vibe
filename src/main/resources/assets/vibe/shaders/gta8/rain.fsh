#include "common.glsl"
uniform float uIntensity;
varying vec2 vUV;
varying vec3 vWorld;
varying float vFade;

void main() {
    float edge = 1.0 - abs(vUV.x);
    float alpha = edge * edge * vFade * uIntensity * .22 * smoothstep(0.0, .2, vUV.y) * smoothstep(1.0, .7, vUV.y);
    vec3 light = uSkyAmbient * 1.6 + uSunColor * .15 + uNight * vec3(.35, .3, .22) * .25 + uLightning * 2.0;
    gl_FragColor = vec4(light * alpha, alpha);
}
