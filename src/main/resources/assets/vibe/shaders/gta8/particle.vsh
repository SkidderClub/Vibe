#include "common.glsl"
attribute vec3 aPos;
attribute vec2 aUV;
attribute vec4 aColor;
attribute vec4 aMat;     // size, rotation, type, stretch
attribute vec3 aNormal;  // velocity, for stretched sparks
uniform mat4 uViewProj;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
varying vec2 vUV;
varying vec4 vColor;
varying vec4 vMat;
varying vec3 vWorld;

void main() {
    float size = aMat.x, rot = aMat.y;
    vec2 c = vec2(cos(rot) * aUV.x - sin(rot) * aUV.y, sin(rot) * aUV.x + cos(rot) * aUV.y);
    vec3 p = aPos + (uCamRight * c.x + uCamUp * c.y) * size;
    if (aMat.z > 9.5) p = aPos;
    else if (aMat.w > 0.0 && dot(aNormal, aNormal) > .01) {
        // Sparks and tracers are stretched along their motion.
        vec3 dir = normalize(aNormal);
        vec3 toCam = normalize(uCameraPos - aPos);
        vec3 side = normalize(cross(dir, toCam));
        p = aPos + side * aUV.x * size + dir * aUV.y * aMat.w;
    }
    vWorld = p;
    vUV = aUV;
    vColor = aColor;
    vMat = aMat;
    gl_Position = uViewProj * vec4(p, 1.0);
}
