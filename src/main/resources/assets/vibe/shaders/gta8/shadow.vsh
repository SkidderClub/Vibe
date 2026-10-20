attribute vec3 aPos;
attribute vec2 aUV;
attribute vec4 aMat;
uniform mat4 uViewProj;
uniform mat4 uModel;
uniform float uTime;
#ifdef SKINNED
uniform mat4 uBones[16];
#endif
varying vec2 vUV;
varying float vFoliage;
varying float vParam;

void main() {
    vec4 p = vec4(aPos, 1.0);
#ifdef SKINNED
    p = uBones[int(aMat.w + .5)] * p;
#endif
    vec4 w = uModel * p;
    vFoliage = abs(aMat.x - 14.0) < .5 || abs(aMat.x - 30.0) < .5 ? 1.0 : 0.0;
#ifndef SKINNED
    if (abs(aMat.x - 14.0) < .5) {
        float phase = uTime * 1.7 + dot(w.xz, vec2(.31, .27));
        w.xyz += vec3(sin(phase), 0.0, cos(phase * .83)) * .06 * (.25 + aUV.y);
    }
#endif
    vFoliage *= abs(aMat.x - 30.0) < .5 ? 2.0 : 1.0;
    vUV = aUV;
    vParam = floor(aMat.z + .5);
    gl_Position = uViewProj * w;
}
