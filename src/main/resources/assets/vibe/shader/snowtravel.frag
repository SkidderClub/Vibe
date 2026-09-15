/*
 * Original shader from: https://www.shadertoy.com/view/tttyWn
 */

#extension GL_OES_standard_derivatives : enable

#ifdef GL_ES
precision mediump float;
#endif

// glslsandbox uniforms
uniform float time;
uniform vec2 resolution;

// shadertoy emulation
#define iTime (time + 8.)
#define iResolution resolution

// --------[ Original ShaderToy begins here ]---------- //

float fa;
float snowFlakes;

bool mapShadows = false;

bool volumetricMarching = false;
vec3 glow = vec3(0.);


//#define iTime (iTime + 8.)
#define marchSteps 140
#define marchEps  0.004

#define planetSz 34.
#define atmoSz 10.

#define volumetricDist 30.
#define volumetricSteps 200
#define volumetricDithAmt 0.01
#define volumetricStepSz ( max(volumetricDist/float(volumetricSteps), 0.4*volumT/volumetricDist - 0.1)*(0.5 + dith) )

#define itersAtmo 5.
#define itersOptic 5.

#define lampLightCol vec3(1,0.6,0.5)
#define lampH 2.
#define lampW 0.8 





#define pi acos(-1.)

#define tau (2.*pi)
#define rot(a) mat2(cos(a),-sin(a),sin(a),cos(a))
#define pmod(p,a) mod(p - 0.*a,a) - 0.5*a

#define iMouse (iMouse.y < 10. ? iResolution.xy/2. : iMouse.xy)

#define TT iTime

vec3 acesFilm(const vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d ) + e), 0.0, 1.0);
}

vec3 getRd(vec3 ro, vec3 lookAt, vec2 uv){
    
    uv *= 0.7;
    vec3 dir = normalize(lookAt - ro);
    vec3 right = normalize(cross(vec3(0,1,0),dir));
    vec3 up = normalize(cross(dir,right));
    return normalize(dir + right*uv.x + up*uv.y);
}

float sdVerticalCapsule( vec3 p, float h, float r )
{
  p.y -= clamp( p.y, 0.0, h );
  return length( p ) - r;
}

float sdSolidAngle(vec3 p, vec2 c, float ra)
{
  // c is the sin/cos of the angle
  vec2 q = vec2( length(p.xz), p.y );
  float l = length(q) - ra;
  float m = length(q - c*clamp(dot(q,c),0.0,ra) );
  return max(l,m*sign(c.y*q.x-c.x*q.y));
}

float sdRoundCone( vec3 p, float r1, float r2, float h )
{
  vec2 q = vec2( length(p.xz), p.y );
    
  float b = (r1-r2)/h;
  float a = sqrt(1.0-b*b);
  float k = dot(q,vec2(-b,a));
    
  if( k < 0.0 ) return length(q) - r1;
  if( k > a*h ) return length(q-vec2(0.0,h)) - r2;
        
  return dot(q, vec2(a,b) ) - r1;
}

float opSmoothSubtraction( float d1, float d2, float k ) {
    float h = clamp( 0.5 - 0.5*(d2+d1)/k, 0.0, 1.0 );
    return mix( d2, -d1, h ) + k*h*(1.0-h); }



float hash13(vec3 p3){
    p3 = fract((p3)*0.1031);
    p3 += dot(p3, p3.yzx  + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}
float r21(vec2 p)
{
	vec3 p3  = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float valueNoise(vec3 p, float pw){
    
	vec3 s = vec3(1., 25, 75);
	
	vec3 ip = floor(p); // Unique unit cell ID.
    vec4 h = vec4(0., s.yz, s.y + s.z) + dot(ip, s);
    
	p -= ip; // Cell's fractional component.
	
    // A bit of cubic smoothing, to give the noise that rounded look.
    if(pw == 1.){
        p = p*p*(3. - 2.*p); 
    } else {
    	p = p*p*(p*(p * 6. - 15.) + 10.);
    }
    
    //p = smoothstep(0.,1.,p);
    // Smoother version of the above. Weirdly, the extra calculations can sometimes
    // create a surface that's easier to hone in on, and can actually speed things up.
    // Having said that, I'm sticking with the simpler version above.
	//p = p*p*(p*(p * 6. - 15.) + 10.);
    h = mix(fract(sin(h)*43758.5453), fract(sin(h + s.x)*43758.5453), p.x);
	
    // Interpolating along Y.
    h.xy = mix(h.xz, h.yw, p.y);
    
    // Interpolating along Z, and returning the 3D noise value.
    return mix(h.x, h.y, p.z); // Range: [0, 1].
	
}
float opSmoothUnion( float d1, float d2, float k ) {
    float h = clamp( 0.5 + 0.5*(d2-d1)/k, 0.0, 1.0 );
    return mix( d2, d1, h ) - k*h*(1.0-h); }


float opSmoothIntersection( float d1, float d2, float k ) {
    float h = clamp( 0.5 - 0.5*(d2-d1)/k, 0.0, 1.0 );
    return mix( d2, d1, h ) + k*h*(1.0-h); }

vec3 getRdSpherical(vec3 ro,vec2 uv){
    
    // polar coords
    uv = vec2(atan(uv.y,uv.x),length(uv));
    uv += 0.5;
    uv.y *= tau/2.;
    
    // parametrized sphere
    vec3 offs = vec3(cos(uv.y)*cos(uv.x),sin(uv.y),cos(uv.y)*sin(uv.x));
    
    // insert camera rotations here
    offs.yz *= rot(-(1.)*pi);
    
    vec3 lookAt = ro + offs;
    
    return normalize(lookAt - ro);
}

vec2 sphIntersect( in vec3 ro, in vec3 rd, in vec3 ce, float ra )
{
    vec3 oc = ro - ce;
    float b = dot( oc, rd );
    float c = dot( oc, oc ) - ra*ra;
    float h = b*b - c;
    if( h<0.0 ) return vec2(-1.0); // no intersection
    h = sqrt( h );
    //return -b+h;
    //return max(max(-b-h,0.),max(-b+h,0.));
    return vec2( -b-h, -b+h );
}

vec2 dmin(vec2 a, float b, float cmp){return a.x < b ? a : vec2(b,cmp);}

// PBR atmospheric scattering, learned from Sebastian Lague https://www.youtube.com/watch?v=DxfEbulyFcY
// ACES from knarkowitz
// cheap noise from nimitz
// voronoi, smoothmin from iq
// noise from dave hoskins
 

float sdSnowflakes(vec3 _p, float md, float s){
  vec4 p = vec4(_p,1.);
  
  p.yw *= rot(0.4);
  p = abs(p);
  p.xz *= rot(0.4);
  p = abs(p);
  
  p.xy *= rot(2.4);
  p = abs(p) - 1.4;
  
  p.yz *= rot(.7);
  p = abs(p);
  p = pmod(p,md);
  
  return length(p) - s ;
}

float fe;
float fbmSnow(vec3 p){

    vec3 op = p;
    float n = 0.;
    p *= 0.25;
    fa = valueNoise(p,1.); 
    
    
    p *= 8.;
    
    p.y = 1.;
    
    float fb = valueNoise(p*2.,1.);
    
    
    float fc = valueNoise(p*4.,1.);
    
    float fd = valueNoise(p*8.1,1.);
  
    fe = valueNoise(p*20.1,1.);
  

    n += fa + (fb*0.51 + fc*0.255 + fd*.125 + fe*0.07)*0.02 ; /*fa*/;// + valueNoise(op*5.8,1.)*0.0;
    
    n *= 1.2;
    
    return n;
}

float sdSnow(vec3 p){
    float d = 10e5;
    
    d = p.y;
    
    d -= fbmSnow(p); 
    
    return d;
}

float getWalk(vec3 p){
    return valueNoise(vec3(1,1,p.z*0.1),1.)*24.;
}


vec3 getLampP(vec3 p, bool getLightP, bool getLightRelative){
    vec3 op = p;
    p.y -= lampH * 0.5;

    p.x -= 1.5;
    
    float md = 8.;
    float id = floor(p.z/md)*md;
    p.z = pmod(p.z,md);
    
    float walk = getWalk(vec3(1,1,id + 0.5*md));
    p.x += walk;
    
    if(getLightRelative){
        p = vec3(0,0.,id + md*0.5);
        p.x -= walk;
        p.x += 1.5;
        p.x -= lampW;
        p.y += lampH * 0.5;
    } else if( getLightP ){
    
        p.y -= lampH - 0.4;
    
        p.x += lampW*0.55;
    }
        
    return p;

}

float noiseLamp;
vec2 sdLamp(vec3 p){

    p = getLampP(p,false,false);
    
    float lampR = 0.02;
    
    float d = sdVerticalCapsule( p, lampH, lampR );
    
    p.y -= lampH;
    
    d = min(d, sdRoundCone( p, lampR, 0.01, 0.1));
    
    d -= smoothstep(0.3,0.,abs(p.y + 0.1)*8.)*abs(sin(p.y*48.))*0.04;
    
    
    d -= smoothstep(1.,0.,abs(p.y + 0.32)*8.)*pow(abs(sin(p.y*8.)),5.)*0.04;
    
    d -= pow(smoothstep(3.1,2.,abs(p.y + 1.32)*5.),0.01)*pow(abs(sin(p.y*4.)),.3)*0.03;
    
    d -= pow(smoothstep(3.1,2.,abs(p.y + 1.32)*15.),0.01)*pow(abs(sin(p.y*24.)),.3)*0.03;
    
    
    
    d = opSmoothUnion( d, sdRoundCone( p + vec3(0.,lampH*1.,0), lampR, 0.07, 0.4), 0.01 );
    
    d = opSmoothUnion( d, sdRoundCone( p + vec3(0.,lampH*1.5,0), lampR*11.2, lampR*2.5, 0.8) - 0.01, 0.3 );
    
    
    p.y += 0.3;
    p.xy *= rot(-0.5*pi);
    
    vec3 op = p;
    p.x += smoothstep(1.,0.,(p.y- 0.1)*1.)*0.2;
    
    p.y += lampW*0.2;
    float vert =sdVerticalCapsule( p, lampW*1., lampR ); 
    
    vert -= smoothstep(1.,0.,abs(p.y + 0.02)*18.)*abs(sin(p.y*78.))*0.04;
    
    
    
    
    p = op;
    p.y += lampW*0.2;

    //vert -= smoothstep(1.,0.,abs(p.y - lampW)*4.)*pow(abs(sin(p.y*42.)),0.2)*0.04;
    //vert -= smoothstep(0.3,0.,abs(p.y - lampW)*4. - 1.)*0.015;
    
    d = opSmoothUnion( d, vert, 0.02 );

    p.xy *= rot(0.5*pi);
    
    p.x += lampW*0.85;
    p.y += 0.4;
    
    float cup = abs(sdRoundCone( p + vec3(0,0.05,0) , 0.2, 0., 0.2)) - 0.01;

    cup += sin(length(p.xz)*55.)*smoothstep(1.,0.,length(p.xz))*0.02;
    cup = opSmoothSubtraction( -cup, -p.y, 0.05 );
    
    cup = opSmoothUnion(cup,  sdRoundCone( p - vec3(0,0.14,0), 0.04, 0.005, 0.3 ), 0.07);
    
    d = opSmoothUnion( d, cup, 0.01 );
    
    //d = min(d, );
    
    vec2 od = vec2(d,2.);
    
    float bulb = length(p) - 0.1;
    
    od = dmin(od,bulb,4.);
    
    if(!volumetricMarching)
        glow += exp(-bulb*6.)*smoothstep(0.2,0.,p.y + 0.15)*smoothstep(1.,0.,bulb*1.5);
    
    
    if(mapShadows){
        od = max(od,-(length(p) - 1.5));
    }
         
    od.x -= (fa = pow(valueNoise(p*120.,1.),3.))*0.001;//valueNoise(p*20.,1.);
    
    return od;
}

vec3 fluv = vec3(0.);

float getFloor(){
    vec3 uv = fluv;
    vec3 buv = uv;
    vec2 md = vec2(0.5,0.25);
    
    vec2 id = floor(uv.xz/md);
    uv.x += md.x*0.5*id.y;
    uv.xz = pmod(uv.xz,md);
    
    buv.z = pmod(buv.z,4.);
    
    
    
    uv.xz = abs(uv.xz);
    
    float d = 0.;
    
    d += smoothstep(0.,0.05, max( (uv.x - md.x*0.45)/md.x/2., uv.z - md.y*0.4));
    
    
    d = mix(d,-1.+smoothstep(0.02,0.0,length(buv.xz) - 0.11),1.-smoothstep(0.0,0.02,length(buv.xz) - 0.2));
    
    
    
    d -= fa*0.125;
    return d;
}    
float sdSideRail(vec3 p){
    
    p.x = abs(p.x) - 0.9;
    p.y -= 0.6;
    float d = length(p.xy) - 0.02;
    
    vec3 pb = p;
    
    p.z = pmod(p.z,1.);
    
    pb.z = pmod(pb.z - 2.5,4.);
    
    d = min(
            d,
        max(
            length(p.zx) - 0.02,
            p.y - 0.01
            )
        );
    
    float topHat = sdRoundCone( pb, 0.01, 0.04, 0.1 );
    
    d = min(d, topHat);
    
    
    
    d -= fa*0.001;
    return d;
}


float dsnow;
vec2 map(vec3 p){
    vec2 d = vec2(10e5);
    

    
    float snow = sdSnow(p);
    vec2 lamp = sdLamp(p);
    
    float walk = getWalk(p);
    

        
    
    float ground = -abs(p.x + walk) + 0.9;
    //ground = min( ground, p.y + snow + 0.2);
    ground = opSmoothUnion(ground,p.y + snow + 0.2 + pow(valueNoise(p,1.),1.)*1.5,1. );
    
    
    float fl = p.y + 0.1;
    
    fluv = p;
    fluv.x += walk;
    fl += getFloor()*0.015;
    
    d = dmin(d,fl,5.);
    
    
    snow = opSmoothSubtraction(-snow,ground,0.4);
    
    
    
    vec3 flakeWindDir = -normalize(vec3(-4,-2.6,-1.6));
    vec3 pFlakes = p + iTime*flakeWindDir;
    float n = valueNoise(pFlakes*1.,1.);
    float flakeWind = n*0.5;
    
    pFlakes.y += n*0.6;
    
    snowFlakes = sdSnowflakes(pFlakes + flakeWindDir*flakeWind*7. , 1., 0.017);

    snowFlakes = min(snowFlakes, sdSnowflakes(pFlakes + 1.2 + flakeWindDir*iTime*.7 + flakeWind , 1., 0.014));
    
    snowFlakes = min(snowFlakes, sdSnowflakes(pFlakes + 0.6 + flakeWindDir*iTime*2. + flakeWind , 0.75, 0.014));
    
    snowFlakes = min(snowFlakes, sdSnowflakes(pFlakes + 4.6 + flakeWindDir*iTime*2.5 + flakeWind , 0.75, 0.014));
    

    
    //snow = opSmoothUnion(snow,,0.41);
    
    
    

    float sideRail = sdSideRail(vec3(p.x + walk,p.y,p.z));
    
    snow = opSmoothSubtraction( -snow, -lamp.x, 0.2);
    
    d = dmin(d,lamp.x,lamp.y);
    
    
    d = dmin(d,sideRail,lamp.y);
    

    if(!volumetricMarching && !mapShadows)
        dsnow = snow;
    
    snow = opSmoothUnion(snow,sideRail,0.1);

    
    
    d = dmin(d,snow,1.);
    
    //float drocks = sdRocks(p, 14.4, 3.8);
    //drocks = opSmoothSubtraction( -drocks, -(-length(p) + planetSz + 4.5), 1.2 );
    
    //d = dmin(d, drocks,3.);
    
    
    d.x *= 0.75;
    return d;
}
float mapVolumetricFog(vec3 p){
    float dens = 0.;
    
    vec3 op = p;
    p += vec3(iTime*3.,-4.3*iTime,0);
    
    dens = valueNoise(p*0.4,1.);
    
    dens *= smoothstep(1.,0.,op.y*.1);
    
    
    //dens = pow(max(dens,0.),0.4);
    
    return dens*0.1 + 0.007;
}
float mapVolumetricLight(vec3 p){
    
    vec3 lp = getLampP(p,true,false);
    lp.xy *= rot(pi);
    
    return sdRoundCone( lp, 0., 12., 14.9 );
    return sdSolidAngle(lp, vec2(.25,0.5), 15.9);
}

vec3 getNormal(vec3 p)
{
    const vec2 e = vec2(1.0,-1.0)*0.5773;
    const float eps = 0.001;
    return normalize( e.xyy*map(p + e.xyy*eps).x + 
                      e.yyx*map(p + e.yyx*eps).x + 
                      e.yxy*map(p + e.yxy*eps).x + 
                      e.xxx*map(p + e.xxx*eps).x );
}

vec3 getSun(vec2 uv, vec2 sunUV, vec2 sunUVPos,vec3 sunCol){
    
    vec2 sunUVB = sunUV;
    
    sunUV -= sunUVPos;
    vec3 sunRays = 0.4*sunCol * smoothstep(0.035*(1. + smoothstep(1.,0.,abs(sunUV.x)) ) ,0.,abs(sunUV.y))*smoothstep(0.5,0.,abs(sunUV.x));

    for(float i = 0.; i < 8.; i++){
        sunUV *= rot(pi/8./1.);
        sunRays += .1* (sunCol) * smoothstep(0.04 + sin(i*pi/4.)*0.01,0.,abs(sunUV.y))*smoothstep((0.2 + sin(i*pi/4.)*0.1)*1.5,0.,abs(sunUV.x));
    }   
    sunUV = sunUVB - sunUVPos;
    vec3 flares = vec3(0);
    vec2 toMid = sunUVPos;
    vec2 dirToMid = -normalize(toMid);
    float lenToMid = length(toMid);

    for(float i = 0.; i < 12.; i++){
          sunUV -= 2.*lenToMid*dirToMid/12.;
          float dfl = length(sunUV) - (0.1 + 0.1*sin(i*5.))*0.5;
          dfl *= 0.5;
          vec3 flare = 0.01*(sunCol)*smoothstep(0.02,0.,dfl);
          flare += 0.003*(sunCol*sunCol)*smoothstep(0.01,0.,abs(dfl - dFdx(uv.x)));
          flares += flare*abs(sin(i*10.));
    }   
    
    
    return sunRays + flares*2.;
}

float atmosphericDensity( vec3 p){
    float fact = (length(p) - planetSz)/atmoSz;
    return exp(-max(fact,0.)*1.);//*pow(smoothstep(1.,0.,fact),0.2);
}

float opticalDepth(vec3 p, vec3 rd, float len, float iters){
    float stSz = len / iters;
    float depth = 0.;
    for(float opticIdx = 0.; opticIdx < itersOptic; opticIdx++ ){
        depth += atmosphericDensity(p);
        p += rd*stSz;
     }
    return depth/iters;
}

vec3 getAtmosphere(vec3 ro, vec3 rd, out float depthView, bool hit, vec3 sunPos){
    
    vec3 accumAtmo = vec3(0);
    float atmoMarchLen = 0.;
    
    vec3 p = ro;
    ro.y += planetSz*1.;
    
    if(hit){
        atmoMarchLen = length(p - ro);
    } else {
        float s = sphIntersect( ro, rd, vec3(0), planetSz + atmoSz ).y;
        atmoMarchLen = s;
    }
    
    float stepSz = atmoMarchLen/itersAtmo;

    const float redLightLen = 655.;
    const float greenLightLen = 540.;
    const float blueLightLen = 465.;

    const float transStrength = 400.;
    vec3 scatteringCoefficients = vec3(
        pow(transStrength/redLightLen,4.),
        pow(transStrength/greenLightLen,4.),
        pow(transStrength/blueLightLen,4.)
    );


    for(float atmoIdx = 0.; atmoIdx < itersAtmo ; atmoIdx++ ){
        vec3 dirToSun = normalize(sunPos - p);
        float lenSunDirToEndOfAtmosphere = sphIntersect( p, dirToSun, vec3(0), planetSz + atmoSz ).y;
        float lenViewDirToEndOfAtmosphere = sphIntersect( p, -rd, vec3(0), planetSz + atmoSz ).y;
        
        float depthSun = opticalDepth(p, dirToSun, lenSunDirToEndOfAtmosphere, itersOptic);
        depthView = opticalDepth(p, rd, lenViewDirToEndOfAtmosphere, itersOptic);
        
        float localDens = atmosphericDensity(p);
        

        vec3 transmittance = exp(-(depthSun + depthView) * scatteringCoefficients);        
        accumAtmo += transmittance * localDens * scatteringCoefficients;
        
        p += rd * stepSz;
    }
    
    accumAtmo /= itersAtmo;
    return accumAtmo;
}
float softshadow( in vec3 ro, in vec3 rd, float mint, float maxt, float k )
{
    float res = 1.0;
    float ph = 1e20;
    float t = mint;
    for(int i=0; i<30; ++i)
    {
        if (t>=maxt) break;
        float h = map(ro + rd*t).x;
        if( h<0.001 )
            return 0.0;
        float y = h*h/(2.0*ph);
        float d = sqrt(h*h-y*y);
        res = min( res, k*d/max(0.0,t-y) );
        ph = h;
        t += h;
    }
    return res;
}
void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = (fragCoord - 0.5*iResolution.xy)/iResolution.y;

    vec3 col = vec3(0);
    
    vec3 ro = vec3(0);
    
    
    vec3 sunPos = vec3(1,0.1 + sin(iTime)*0.02,1.)*200.;
    vec3 sunDir = normalize(sunPos);
    
    ro.y += 2.;
    ro.z += iTime;
    ro.x -= getWalk(ro);
    
    vec3 lookAt = vec3(0,0,ro.z + 9.);
    
    lookAt.x -= getWalk(lookAt);
    lookAt.y += valueNoise(lookAt*0.25,1.);
    
    
    
    vec3 rd = getRd( ro, lookAt, uv );
    
    #define pump(a,b) (floor(a) + pow(fract(a),b))
    float sphericalEnv = cos(pump(iTime*0.06,20.)*pi + pi)*0.5 + 0.5; 
    sphericalEnv -= sphericalEnv;
        
    vec3 p = ro;
    

    float t = 0.;
    bool hit = false;
    
    vec3 albedo = vec3(1.,0.9,0.6);
    // Marching
    
    vec2 d;
    for(int i = 0; i < marchSteps ; i++){
        d = map(p);
        
        d = dmin(d,snowFlakes,3.);
        
        if(d.x < marchEps){
            hit = true;
            break;
        } else if (t > 29.){
            break;
        }
        
        p = ro + rd*(t += d.x);
    }
    

    vec3 sunCol = vec3(0.9,0.9,0.9)*0. + vec3(1,0.9,0.7);
        
    float depthView; 
    vec3 atmosphere = getAtmosphere( ro - vec3(0,0,iTime), rd, depthView, hit, sunPos);


    // Volumetrics
    volumetricMarching = true;
    float dith = r21(fragCoord + sin(iTime*20.)*20.)*volumetricDithAmt;
    vec3 volP = ro;
    float totalVolDens = 0.;
    float totalVolLightDens = 0.;
    float volumT = 0.;
    for(int i = 0; i < volumetricSteps ; i++){
        float volDens = mapVolumetricFog(volP);
        float volLight = mapVolumetricLight(volP);
        
        
        if ( volumT >  t ){
            break;
            
        }
        
        totalVolLightDens += 5.* smoothstep(0.,0.9,-volLight + 0.2) * volumetricStepSz * (volDens ) * clamp((1.-totalVolDens*1.),0.,1.);//(1. - totalVolLightDens);
        totalVolDens += volDens * volumetricStepSz *(1. - totalVolLightDens);
        
        if(totalVolDens > 1.){
            break;
        }
        
        volP = ro + rd*(volumT += volumetricStepSz);
    }
    
    
    // Shading
    
    if(hit){
        
        vec3 n = getNormal(p);
        
        vec3 lampP = getLampP(p,false,true);
        
        vec3 lampDir = normalize(lampP - p);
        
        
        //n.xz *= rot(-0.5);
        
        vec3 hf = normalize(sunDir - rd);
        float diff = max(dot(n,sunDir),0.);
        float spec = pow(max(dot(n,hf),0.),12.);
        float fres = pow( 1. - max(dot( n, -rd),0.001),4.);
        
        vec3 hfLamp = normalize(lampDir - rd);
        float diffLamp = max(dot(n,lampDir),0.);
        float specLamp = pow(max(dot(n,hfLamp),0.),5.)*smoothstep(1.,0.,abs(mod(p.z,8.) -4.) - 3.);;
        
        
        float specFact = 0.45;
        
        vec3 snowAlbedo = vec3(0.9,0.9,0.95)*1.;
        #define SSS(a) clamp(map(p + sunDir*a).x/a,0., 1.)
        #define ao(a) clamp(map(p + n*a).x/a,0., 1.)
        float sssfact = SSS(0.26)*SSS(1.45)*8.5;
        
        mapShadows = true;
        float shad = softshadow( p , lampDir, 0.2 + r21(fragCoord + sin(iTime)*5.)*0.04, length(p - lampP)*1. + 0.8, 5. );
        
        snowAlbedo = mix(snowAlbedo , vec3(0.,0.04,0.2)*0.5 + snowAlbedo*0.1,smoothstep(1.,0.,sssfact));
        
        
        vec3 snowColor = snowAlbedo*(diff + smoothstep(1.,0.,sssfact)*0. + sssfact*(1. + diff)*0.1)
            *(sunCol*0.5 + atmosphere) 
            + spec*specFact*(sunCol + atmosphere)*0.02;
        
        vec3 lampColor = 1.*(snowAlbedo*lampLightCol + lampLightCol)*(diffLamp + smoothstep(1.,0.,sssfact)*0.9 + sssfact*(1. + diff)*0.1)
            *(sunCol*0.5 + atmosphere)
            + lampLightCol*specLamp*specFact*10.;
        
        float lampRange = smoothstep(3.,0.,length(p - lampP) - 1.);
        snowColor += lampColor * lampRange *0.4*shad;
        //snowColor += specLamp*specFact*(lampLightCol)*20.;
        //snowColor += specLamp*specFact*(lampLightCol)*smoothstep(1.,0.,length(p - lampP) - 1.1);

        
        float aofact = ao(0.2)*ao(1.)*1.5 + 0.2; 
        if (d.y == 1.){
            // snow
            col += snowColor*aofact;
        } else if (d.y == 3.){
            // snoflakes
            col += (snowAlbedo*0.4 + (lampRange) *lampColor )*ao(3.);
        } else if (d.y == 2.){
            // lamppost
            vec3 lampPostColor = .1*(1.- fa)*(diffLamp)
                *(sunCol*0.25 + atmosphere)
                + lampLightCol*specLamp*specFact*1.;            
            
            lampPostColor = lampPostColor*(shad + atmosphere*(1.-fa)*.15);
            lampPostColor += fres*specFact*atmosphere*1.45;
            lampPostColor = mix(lampPostColor*aofact,snowColor*aofact,smoothstep(0.4,0.0,dsnow + 0.05));
            col += lampPostColor;
                //+ spec*specFact*(sunCol + atmosphere)*.45;
                //+ spec*.45*atmosphere;
        } else if (d.y == 4.){
            col += lampColor;
        } else if (d.y == 5.){
            // floor
             
            vec3 flCol = .23*vec3(1,0.6,0.4)*(1.- fa)*(diffLamp)
                *(sunCol*0.25 + atmosphere) 
                + lampLightCol*pow(specLamp,4.)*specFact*.61;
        
            
            //flCol += fl*shad + atmosphere*(1.-fa)*.15*0.;
            flCol = mix(flCol*aofact,snowColor*aofact,smoothstep(0.2,0.0,dsnow - 0.02));
            col += flCol ;
            //col += fres*specFact*atmosphere*1.45;
        }
        
    }
    
    // Atmosphere
    
    //col=mix(col,atmosphere,smoothstep(0.,1.,(p.z - iTime - 23.)*0.6));
    
    col += lampLightCol*144.*glow/float(marchSteps)*smoothstep( 1., 0., t*0.08 - 1.7 + totalVolDens*0.02 );
    vec3 fogC = atmosphere;
    fogC = mix(fogC, vec3(0.3,0.3,0.35), clamp(totalVolDens,0.,1.));
    fogC = mix(fogC, (lampLightCol + fogC)*0.5, clamp(totalVolLightDens,0.,1.));
    
    if(hit){
       atmosphere *= 1.-pow(exp(-(t)*0.05 ),2.);
    }
    
    col = col * exp(-depthView) + atmosphere; 
    
    col = mix(col, fogC,totalVolDens);
    
    col *= vec3(0.95,0.98,1.04);
    
    col = mix(col,smoothstep(0.,1.,col*1.3),0.7);
    col = mix(col,smoothstep(0.,1.,col*1.5),0.2);
    col = mix(acesFilm(col), col, 1.);
    col *= 1. - dot(uv,uv*0.4)*2.1;
    
    col = pow(col,vec3(0.454545));
    fragColor = vec4(col,1.0);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}