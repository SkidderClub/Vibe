#version 120
varying vec2 uv;
uniform vec3 Color,GlowColor;
uniform float Moon,Phase,Time,Brightness,Glow,Halo,Visibility,Pass;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.),f.x),f.y);}
float fbm(vec2 p){float n=0.,a=.5;for(int i=0;i<5;i++){n+=a*noise(p);p=p*2.07+vec2(17,29);a*=.5;}return n;}
float craters(vec2 p,float scale){
    vec2 g=p*scale,cell=floor(g);float shade=0.;
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
        vec2 c=cell+vec2(float(x),float(y)),center=c+vec2(hash(c+3.),hash(c+27.));
        float r=.08+.28*hash(c+53.),d=length(g-center)/r;
        float floorShade=exp(-d*d*2.)*.24;
        float rim=exp(-pow((d-1.)/.17,2.))*.15;
        float directional=(g.x-center.x-(g.y-center.y)*.7)/r;
        shade+=rim*(.5+directional*.65)-floorShade;
    }
    return shade;
}
void main(){
    vec2 p=(uv*2.-1.)*4.;float r=length(p);
    if(Pass<.5){
        float spread=max(.1,Halo),halo=exp(-max(r-1.,0.)/(.30*spread))*(1.-smoothstep(3.3,3.98,r));
        float corona=exp(-max(r-1.,0.)/(.8*spread))*.045;
        float shafts=1.+.11*sin(atan(p.y,p.x)*9.+Time*.05)*sin(atan(p.y,p.x)*17.-Time*.08);
        float alpha=(halo*.14*shafts+corona)*Glow*Visibility*smoothstep(.88,1.06,r);
        gl_FragColor=vec4(GlowColor*Brightness,alpha);return;
    }
    float aa=max(fwidth(r),.002);float coverage=1.-smoothstep(1.-aa,1.+aa,r);if(coverage<=0.)discard;
    vec3 normal=vec3(p,sqrt(max(0.,1.-r*r)));float lum;
    if(Moon>.5){
        // The lunar terminator follows the world's eight phases, with a little earthshine.
        float angle=Phase*6.2831853/8.;vec3 light=vec3(sin(angle),.08,cos(angle));
        float lit=smoothstep(-.015,.12,dot(normal,normalize(light)));
        vec2 surface=p/(1.+normal.z*.45);
        float maria=smoothstep(.33,.68,fbm(surface*3.+9.));
        float rock=fbm(surface*70.);
        float relief=craters(surface,9.)+craters(surface+13.,23.)*.6;
        float albedo=clamp(.67+maria*.28+(rock-.5)*.19+relief,.20,1.05);
        lum=albedo*(.035+lit*(.70+.30*max(0.,dot(normal,normalize(light)))));
    }else{
        float granules=fbm(p*56.+vec2(Time*.012,0.));
        float cells=noise(p*140.+Time*.02);
        lum=(.64+.36*pow(max(0.,normal.z),.45))*(.89+.14*granules+.035*cells);
    }
    gl_FragColor=vec4(Color*lum*Brightness,coverage*Visibility);
}
