#version 120
varying vec2 uv;
varying vec4 tint;
uniform float Trail,Glow,Sharpness;
void main(){
    float d=Trail>.5?abs(uv.x*2.-1.):length(uv*2.-1.);
    float halo=exp(-d*d*5.5)*(1.-smoothstep(.65,1.,d))*Glow;
    float core=exp(-d*d*Sharpness);
    vec3 color=mix(tint.rgb,vec3(1.,.98,.91),core*.85);
    gl_FragColor=vec4(color,tint.a*(halo*.30+core*.85));
}
