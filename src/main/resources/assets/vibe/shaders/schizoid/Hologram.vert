#version 120
varying vec2 uv;
varying vec3 normal;
varying vec3 viewPosition;
void main(){
    uv=gl_MultiTexCoord0.xy;
    normal=normalize(gl_NormalMatrix*gl_Normal);
    viewPosition=(gl_ModelViewMatrix*gl_Vertex).xyz;
    gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;
}
