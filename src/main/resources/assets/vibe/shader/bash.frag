/*
 * Original shader from: https://www.shadertoy.com/view/WdVyzh
 */

#ifdef GL_ES
precision mediump float;
#endif


uniform float time;
uniform vec2 resolution;

// shadertoy emulation
#define iTime time*0.25
#define iResolution resolution

// Emulate some GLSL ES 3.x
int int_mod(int a, int b)
{
    return (a - (b * (a/b)));
}

// --------[ Original ShaderToy begins here ]---------- //
// Libraries

#define tau (3.1415926535*2.)

float rand(vec2 co){
  return fract(sin(dot(co.xy, vec2(12.9898,78.233))) * 43758.5453);
}
float noise0(float t, float s) {
  float r = floor(t);
  float f = fract(t);
  f = smoothstep(0., 1., f);
  float u0 = rand(vec2(r+0.,s)) - 0.5;
  float u1 = rand(vec2(r+1.,s)) - 0.5;
  return mix(u0, u1, f);
}
float noise1(float t, float s) {
  float r = floor(t);
  float f = fract(t);
  f = smoothstep(0., 1., f);
  float v0 = rand(vec2(r+0.,s));
  float v1 = rand(vec2(r+1.,s));
  float v2 = rand(vec2(r+2.,s));
  float u0 = v1 - v0;
  float u1 = v2 - v1;
  return mix(u0, u1, f);
}
float noise2(float t, float s) {
  float r = floor(t);
  float f = fract(t);
  f = smoothstep(0., 1., f);
  float w0 = rand(vec2(r+0.,s));
  float w1 = rand(vec2(r+1.,s));
  float w2 = rand(vec2(r+2.,s));
  float w3 = rand(vec2(r+3.,s));
  float v0 = w1 - w0;
  float v1 = w2 - w1;
  float v2 = w3 - w2;
  float u0 = v1 - v0;
  float u1 = v2 - v1;
  return mix(u0, u1, f);
}
float noiseR0(float t, float p) {
  t += noise0(t, 0.) * p;
  return noise0(t, 1.);
}
float inst0(float f, float t, float m, float o, float p) {
  float rep = 1./f;
  float fac = mod(t,rep);
  float mult = m;
  float offset = t*o;
  return mix(
    noiseR0(fac*mult + offset, p),
    noiseR0((fac+rep)*mult + offset, p),
    smoothstep(0.,1.,1.-fac/rep));
}
float noiseR1(float t, float p) {
  t += noise0(t, 0.) * p;
  return noise1(t, 1.);
}
float inst1(float f, float t, float m, float o, float p) {
  float rep = 1./f;
  float fac = mod(t,rep);
  float mult = m;
  float offset = t*o;
  return mix(
    noiseR1(fac*mult + offset, p),
    noiseR1((fac+rep)*mult + offset, p),
    smoothstep(0.,1.,1.-fac/rep));
}
float noiseR2(float t, float p) {
  t += noise0(t, 0.) * p;
  return noise2(t, 1.);
}
float inst2(float f, float t, float m, float o, float p) {
  float rep = 1./f;
  float fac = mod(t,rep);
  float mult = m;
  float offset = t*o;
  return mix(
    noiseR2(fac*mult + offset, p),
    noiseR2((fac+rep)*mult + offset, p),
    smoothstep(0.,1.,1.-fac/rep));
}
float chirp(float sf, float df, float rate, float t) {
  // integrate sf+df*exp(-rate*t) = sf*t-df*exp(-rate*t)/rate
  float v = sf*t - df*exp(-rate*t)/rate;
  return sin(v*tau) + sin(v*1.5*tau)*0.5;
}
vec2 psh(float sf, float df, float rate, float t) {
  // integrate sf+df*exp(-rate*t) = sf*t-df*exp(-rate*t)/rate
  float v = sf*t - df*exp(-rate*t)/rate;
  return vec2(noise2(v*tau, 0.), noise2(v*tau+.2, 0.))*.7 + noise1(v*tau, 0.) * .3;
}
vec2 ov(float f, float t, float m, float e) {
  vec2 v = vec2(0.);
  float ff = f;
  float ee = (1.+e)/2.;
  for(int i=0;i<4;i++) {
    v += sin(ff*t*tau+float(i)+vec2(-0.25,0.25)) * ee;
    ff *= m;
    ee *= e;
  }
  return v;
}
float ev(float s, float e, float t) {
  return (1.-exp(-s*t)) * exp(-e*t);
}
float ev2(float s, float e, float t) {
  return ev(s, e, t) + ev(s*4., e*8., t) * 2.;
}

#define R(x) step(x*32., bt) 
#define I(x) step(bt, x*32.) 
#define C(x) max(step(bt, x*32.-4.), R(x))

// #define AA

struct C {
    float d;
    int m, n, o;
};

float sdBox( vec3 p, vec3 b ) {
  vec3 q = abs(p) - b;
  return length(max(q,0.0)) + min(max(q.x,max(q.y,q.z)),0.0);
}
float sdCappedCylinder( vec3 p, float r, float h ) {
  vec2 d = abs(vec2(length(p.xz),p.y)) - vec2(r,h);
  return min(max(d.x,d.y),0.0) + length(max(d,0.0));
}
float smax(float d1, float d2, float k){
    float h = exp(k * d1) + exp(k * d2);
    return log(h) / k;
}
float smin(float d1, float d2, float k) {
    return -smax(-d1,-d2,k);
}

vec2 pmod(vec2 p, int m) {
    float a = atan(p.y, p.x);
    float s = 3.1415926535 / float(m);
    a = mod(a + s, 2. * s) - s;
    return length(p) * vec2(cos(a), sin(a));
}
C cc(float d, int m, int n) {
	C c;
    c.d = d;
    c.m = m;
    c.n = n;
    return c;
}
C cmin(C a, C b) {
	if(a.d < b.d) return a;
    return b;   
}
C scene(vec3 p) {
    vec3 lp;
    
    C shaft = cc(length(p.xz) - 0.04, 0, 0);
    
    float t = iTime;
    float bpm = 140.;
    float spb = 60./bpm;
    float bt = t/spb;
    p.y += t*0.23;
    
    float yh = 0.23/140.*60.;
    float ly = mod(p.y, yh) - yh/2.;
    float yi = floor(p.y/yh);
    lp = vec3(p.x, ly, p.z);
    C cyl = cc(sdCappedCylinder(lp, mod(yi,2.) < 1. ? 0.05 : 0.06, yh*0.1), 1, int_mod(int(yi+8.),2));
    shaft = cmin(shaft, cyl);
    
    vec2 seed = vec2(yi,0.);
    float abt = bt < 40. ? smax(32., bt, 2.) : bt < 344. ? bt : bt < 360. ? 352.+smin(0., bt-352., 2.) : 352.;
    
    float le = 0.1 * ev(100., 3., fract(bt)) * min(R(2.), max(I(4.), min(R(8.), I(10.)))) * C(4.);
    
    float ld = (mod(yi,2.) < 0.5 ? 0.35 : 0.25) + mix(0., 0.6, pow(rand(seed+1.), 4.));
    float lh = 0.;
    float rspd = rand(seed+0.)-.5;
    if(mod(yi,3.) < 0.5) {
        float u = pow(smoothstep(-2.2, 0.2, bt-224.), 6.);
        ld += 0.8 * u;
        lh += 0.02 * u;
        rspd += sign(rspd) * .3;
    }
    float a = rspd * abt * .5 - 0.1 * t;
    
    float u = fract(bt/4.-0.25);
    float ce = ev(100., 8., u);
    if(bt < 256.) {
        u = fract(bt/8.-0.875+0.0625)*2.;
        ce += ev(100., 4., u);
    }
    ce *= ld * .2 * min(R(6.), I(12.)) * C(8.);
    
    bool beatPos = int_mod((int_mod(int(floor(bt)-yi),4)+4),4) == 3;
    bool clapPos = int_mod((int_mod(int(yi),4)+4),4) == 0;
    if(!beatPos) le = 0.;
    if(!clapPos) ce = 0.;
    ld += le;
    int sep = 24;
    lp = vec3(pmod(vec2(p.x,p.z)*mat2(cos(a),-sin(a),sin(a),cos(a)),sep) - vec2(ld,0), ly);
    C cubes = cc(min(yh/2., sdBox(lp, vec3(0.02+le,0.02+ce,0.02+lh)) - 0.003), 2, int_mod(int(yi+8.),4));
    bool clapPhase = 192. <= bt && bt < 256.;
    if(beatPos && !clapPos && clapPhase || !clapPhase && beatPos) cubes.n += 4;
    else if(clapPos) cubes.n += 8;
    
    return cmin(shaft, cubes);
}
vec3 normal(vec3 p) {
	C c = scene(p);
    vec2 e = vec2(0.0001, 0.);
    return normalize(vec3(
    	scene(p+e.xyy).d,
    	scene(p+e.yxy).d,
    	scene(p+e.yyx).d
    )-c.d);
}
vec3 color(vec2 uv) {
    float t = iTime;
    float bpm = 140.;
    float spb = 60./bpm;
    float bt = t/spb;
    float u;
    
    float a = 0.8;
    uv *= mat2(cos(a),-sin(a),sin(a),cos(a));

    vec3 dir = normalize(vec3(uv,1.));
    a = -0.5;
    dir.yz *= mat2(cos(a),-sin(a),sin(a),cos(a));
    vec3 cam = vec3(0.1,-0.693,-1.1);
    float dist = 0.;
    C ld;
    ld.d = 10000.;
    ld.m = -1;
    float eps = 0.001;
    for(int i=0;i<80;i++) {
        vec3 p = cam + dir * dist;
        ld = scene(p);
        dist += ld.d;
    	if(abs(ld.d) < eps) break;    
    }
	
    vec3 bg = vec3(0.);
    a = t*0.1;
    vec2 bgdir = mat2(cos(a),-sin(a),sin(a),cos(a)) * dir.xz;
    vec2 bguv = vec2(atan(bgdir.y, bgdir.x), dir.y*.8/length(dir.xz) + t*0.05) * 8. / 3.1415926535;
    bguv = (fract(bguv*2.)-0.5) * 2.;
    float bd = pow(pow(abs(bguv.x),3.) + pow(abs(bguv.y), 3.), 1.);
    bg += (1.-exp(-bd)) * vec3(0.2,0,0.8) * 0.3;
    vec3 col = vec3(0.);
    
    if(ld.d < eps) {
	    vec3 p = cam + dir * dist;
        vec3 n = normal(p);
        vec3 v = normalize(cam - p);
        float dif = dot(n,normalize(vec3(1,0.5,1))) * 0.5 + 0.5;
        float rim = 1. - dot(n,v);
        float ref = cos(dot(reflect(v,n), normalize(vec3(1,-1,1))) * 2.) * 0.5 + 0.5;
        col = vec3(0.);
        col += dif * vec3(0,0.2,0.5);
        col += pow(rim,2.) * vec3(0.1,0.2,1);
        col += pow(ref, 5.) * vec3(0.2,0.,0.3);
        
        // p
        float pa = 0.;
        u = fract(bt/4.-0.0)*4.;
        pa += ev(100., 1., u);
        u = fract(bt/4.-0.375)*4.;
        pa += ev(100., 4., u);
        u = fract(bt/4.-0.75)*4.;
        pa += ev(100., 3., u);
        
        vec3 emi = vec3(0.);
        if(ld.m == 0) {
            // a
            emi += vec3(0.5,0.5,2.) * smoothstep(417., 415., bt);
            emi *= rim * R(1.);
            emi += pa * vec3(1.5,1,0) * (rim + dif) * min(R(3.), I(10.));
        } else if(ld.m == 1) {
            // h
            if(ld.n == 1) {
				/*u = fract(bt/4.-0.5)*4.;
            	emi += 1. * R(5.) * C(6.);
                */
            	u = fract(bt+0.5)*2.;
            	emi += 1. * C(6.);
            } else if(ld.n == 0) {
                u = fract(bt*4.);
                int i = int_mod(int(floor(bt*4.)),32);
                if(bt < 320. && int_mod(i,3) == 0) {
                	emi += 1. * C(4.) * max(I(4.), R(8.));
                }
            }
            emi *= rim * ev(100., 2., u) * 4. * min(R(.5), I(11.)) * C(2.) * C(8.);
            emi += pa * vec3(1.5,1,0) * (rim + dif) * min(R(3.), I(10.));
        } else if(ld.m == 2) {
            // m & b
            float ut = fract(bt/64.+0.125)*64. < 62. ? bt : bt+0.5;
            int n = int_mod(ld.n,4);
            if(n == 0) {
                u = fract(bt/4.) * 2.;
            	emi += vec3(0.,0.5,2.) * (1. + exp(-u));
            } else if(n == 2) {
                u = fract(bt/4.-0.375) * 2.;
            	emi += vec3(0.7,0.,2.) * (1. + exp(-u));
            } else if(bt > 8.) {
                if(n == 1) {
                    u = fract(ut/16.-15./16.)*16.;
                    emi += vec3(2.,0.8,0.);
                } else if(n == 3) {
                    u = fract(ut/32.-31.5/32.)*32.;
                    emi += vec3(2.,0.2,0.);
                }
            }
            emi *= rim * ev(100., 2., u) * 4. * I(12.);
            if(bt > 384.) {
                u = bt-384.;
                emi += rim * ev(100., 1., u) * 4.;
            }
            if(4 <= ld.n && ld.n < 8) {
            	u = fract(bt);
                emi += vec3(1,1,1) * ev(100., 2., u) * 4. * dif * min(R(2.), max(I(4.), min(R(8.), I(10.)))) * C(4.);
            } else if(ld.n >= 8) {
                float u = fract(bt/4.-0.25);
                float ce = ev(100., 8., u);
                if(bt < 256.) {
                    u = fract(bt/8.-0.875+0.0625);
                    ce += ev(100., 4., u);
                }
                ce *= min(R(6.), I(12.)) * C(8.);
                emi += ce * vec3(3,1.5,0) * ev(100., 0.5, u) * 4. *dif;
            }
        }
        col += emi;
        float beatOffset = bt - 129.6;
        float shd = max(0.,p.y+beatOffset*0.5);
        col += (ev(100., 16., shd) * 2. + ev(10., 1.5, shd)) * 4. * (rim + dif) * vec3(1.,0.7,0.5);
        
        col = mix(bg, col, 1. - pow(rim, 5.));
    } else {
        col = bg;
    }
    return col;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv = (fragCoord-iResolution.xy/2.)/iResolution.y;
    vec3 col = vec3(0.);
    #ifdef AA
    int x = 2;
    for(int i=0;i<x;i++) {
        for(int j=0;j<x;j++) {
    		col += color(uv + vec2(i,j)/float(x)/iResolution.xy);
        }
    }
    col /= float(x*x);
    #else
    col += color(uv);
    #endif
    fragColor = vec4(col, 1.);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}