#version 120
varying vec2 uv;
uniform vec3 Lower,Upper;
uniform float Time,Layer,Opacity;
float hash(float x){return fract(sin(x*127.1)*43758.5453);}
float noise(float x){float i=floor(x),f=fract(x);return mix(hash(i),hash(i+1.),f*f*(3.-2.*f));}
void main(){
    float drift=Time*.12+Layer*11.;
    float fold=noise(uv.x*8.+drift)*.15+noise(uv.x*21.-drift*.6)*.07;
    float y=uv.y-fold;
    float bottom=smoothstep(-.03,.045,y),top=exp(-max(0.,y)*5.0);
    float ray=.28+.50*noise(uv.x*190.+noise(uv.x*8.+drift)*4.)+.22*noise(uv.x*410.-drift*.5);
    float waves=.50+.50*noise(uv.x*16.+drift*.7);
    float ends=smoothstep(0.,.13,uv.x)*(1.-smoothstep(.87,1.,uv.x));
    float alpha=bottom*top*ray*waves*ends*Opacity;
    vec3 color=mix(Lower,Upper,smoothstep(.02,.53,y));
    color+=Lower*exp(-pow((y-.065)/.045,2.))*.23;
    gl_FragColor=vec4(color,alpha);
}
