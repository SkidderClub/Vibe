#version 120
uniform sampler2D Current;
uniform sampler2D Previous;
uniform vec2 Resolution;
uniform float CurrentWeight;
uniform float PreviousWeight;
void main(){vec2 uv=gl_FragCoord.xy/Resolution;gl_FragColor=texture2D(Current,uv)*CurrentWeight+texture2D(Previous,uv)*PreviousWeight;}
