#version 120
uniform sampler2D Skin;
uniform vec3 Tint;
uniform float Opacity;
uniform float SkinMix;
uniform float GlowStrength;
uniform int BrightnessOnly;
uniform int MaskPass;
uniform int OuterLayer;
varying vec2 uv;
varying vec3 normal;
varying vec3 viewPosition;
void main(){
    vec4 skin=texture2D(Skin,uv);
    if(OuterLayer==1&&skin.a<0.1)discard;
    float luminance=dot(skin.rgb,vec3(0.2126,0.7152,0.0722));
    vec3 detail=BrightnessOnly==1?Tint*luminance:skin.rgb;
    float edge=pow(1.0-abs(dot(normalize(normal),normalize(-viewPosition))),2.5);
    vec3 color=mix(Tint,detail,SkinMix);
    color=mix(color,min(vec3(1.0),Tint*1.35+0.15),edge*0.38);
    float alpha=Opacity*(0.65+0.35*edge);
    gl_FragColor=MaskPass==1?vec4(Tint*alpha*GlowStrength,alpha):vec4(color,alpha);
}
