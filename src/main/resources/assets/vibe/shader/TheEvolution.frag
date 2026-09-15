/*
 * Original shader from: https://www.shadertoy.com/view/4tl3zn
 */

#ifdef GL_ES
precision mediump float;
#endif

// glslsandbox uniforms
uniform float time;
uniform vec2 resolution;

// shadertoy emulation
#define iTime time
#define iResolution resolution

// --------[ Original ShaderToy begins here ]---------- //
/*
a	param
b	param		bar
c	iter		cross
d	dist		vec2
e
f	distFunc	float
g	getNorm
h				fractal theta
i
j	camC-camP
k
l	rayL		clamp
m
n
o
p	rayP		camP
q
r	res			return
s				camS
t	time		vec3
u	uv			camU
v	0,1			reverb
w	ray			fractal power
x	no
y	no
z	no
*/

#ifdef GL_ES
precision highp float;
#endif

#define t iTime
#define r iResolution.xy
#define L(i) clamp(i,0.,1.)
#define F float
#define D vec2
#define T vec3
#define R return
#define v D(0,1)

F B(D a,D b)
{
    D d=abs(a)-b;
    R min(max(d.x,d.y),0.)+length(max(d,0.));
}

F C(T a,D b)
{
    R min(B(a.xy,b),min(B(a.yz,b),B(a.zx,b)));
}

F f(T a)
{
    F b=0.;
    for(F c=0.;c<7.;c+=1.)
    {
        F H=c*(t*.002);
        a.xy=mat2(cos(H),-sin(H),sin(H),cos(H))*a.xy;
        F W=pow(1.6,c*.7);
        b=max(b,-C(mod(a+.15/W,.6/W)-.15/W,D(.1/W)));
    }
    R b;
}

T g(T a,F b)
{
	D d=v*b;
	R normalize(T(
		f(a+d.yxx)-f(a-d.yxx),
		f(a+d.xyx)-f(a-d.xyx),
		f(a+d.xxy)-f(a-d.xxy)
	));
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
	D u=(fragCoord.xy*2.-r)/r.x;
	T P=T(sin(-t*.204),cos(t*.04),0.)*-.35+v.yyx*.3,
		j=normalize(v.yxx-P),
		S=cross(j,v.xyx),
		U=cross(S,j),
		w=normalize(S*u.x+U*u.y+j),
        p=P;
	
	F d=0.,l=0.;
    for(int i=0;i<99;i++){
        d=f(p);
        l+=d;
        p=P+w*l;
    }
	
    d=pow(l*.9,2.);
	fragColor=vec4((dot(-w,g(p,1E-4))*max(1.-d*.002,0.)+L(d+.2))*((d+L((1.-dot(g(p,1E-4),g(p,1E-3)))*8.+.1)*pow(.7,mod(t,9.)))*T(.2,.3,.6)+T(.1))*(1.-pow(1E-7,t*.04))*(1.3-length(u)*.7),1.);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}