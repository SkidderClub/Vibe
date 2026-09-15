#version 120
varying vec2 SkinUv;
varying vec3 EyePosition;
varying vec3 EyeNormal;

void main() {
    vec4 eye = gl_ModelViewMatrix * gl_Vertex;
    EyePosition = eye.xyz;
    EyeNormal = gl_NormalMatrix * gl_Normal;
    SkinUv = gl_MultiTexCoord0.xy;
    gl_Position = ftransform();
}
