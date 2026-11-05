#version 120
varying vec2 uv;
varying vec4 tint;
void main(){
    uv=gl_MultiTexCoord0.xy;
    tint=gl_Color;
    gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;
}
