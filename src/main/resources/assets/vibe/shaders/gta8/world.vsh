#include "common.glsl"
attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec4 aColor;
attribute vec2 aUV;
attribute vec4 aMat;
uniform mat4 uViewProj;
uniform mat4 uModel;
#ifdef SKINNED
uniform mat4 uBones[16];
#endif
varying vec3 vWorld;
varying vec3 vNormal;
varying vec4 vColor;
varying vec2 vUV;
varying vec4 vMat;

void main() {
    vec4 p = vec4(aPos, 1.0);
    vec3 n = aNormal;
#ifdef SKINNED
    mat4 b = uBones[int(aMat.w + .5)];
    p = b * p;
    n = mat3(b[0].xyz, b[1].xyz, b[2].xyz) * n;
#endif
    vec4 w = uModel * p;
#ifndef SKINNED
    // Foliage sways in the wind; card tops move more than the attachment points.
    if (abs(aMat.x - 14.0) < .5) {
        float gust = .06 + .1 * uRain;
        float phase = uTime * 1.7 + dot(w.xz, vec2(.31, .27));
        w.xyz += vec3(sin(phase), 0.0, cos(phase * .83)) * gust * (.25 + aUV.y);
    }
#endif
    vWorld = w.xyz;
    vNormal = mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz) * n;
    vColor = aColor;
    vUV = aUV;
    vMat = aMat;
    gl_Position = uViewProj * w;
}
