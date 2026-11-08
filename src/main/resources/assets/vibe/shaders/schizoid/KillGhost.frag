#version 120
uniform sampler2D Skin;
uniform vec3 Tint;
uniform float Opacity;
uniform float Progress;
uniform float Detail;
uniform int Dissolve;
uniform int OriginalSkin;
uniform int OuterLayer;
uniform int MaskPass;
uniform float GlowStrength;
varying vec2 uv;
varying vec3 normal;
varying vec3 viewPosition;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    vec4 skin=texture2D(Skin,uv);
    if(OuterLayer==1&&skin.a<0.1)discard;
    float noise=hash(floor(uv*Detail*4.0));
    if(Dissolve==1&&noise<Progress)discard;
    float rim=pow(1.0-abs(dot(normalize(normal),normalize(-viewPosition))),2.0);
    float edge=Dissolve==1?1.0-smoothstep(0.0,0.085,noise-Progress):0.0;
    vec3 base=OriginalSkin==1?mix(skin.rgb,Tint,0.22):Tint*(0.6+rim*0.4);
    base=mix(base,min(vec3(1.0),Tint*1.5+0.2),max(edge,rim*0.3));
    float alpha=Opacity*(0.8+0.2*rim);
    gl_FragColor=MaskPass==1?vec4(Tint*alpha*GlowStrength,alpha):vec4(base,alpha);
}
