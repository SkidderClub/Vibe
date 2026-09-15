/*
 * Original shader from: https://www.shadertoy.com/view/tdyGWK
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
const vec4 iMouse = vec4(0.);

// Emulate a black texture
#define texture(s, uv) vec4(0.)

// Emulate some GLSL ES 3.x
#define round(x) (floor((x) + 0.5))

// --------[ Original ShaderToy begins here ]---------- //
#define PI 3.1416
#define TERRAIN_PATTERN 4.0*sin(12.0*pos.x)+sin(20.0*pos.y)+sin(15.0*pos.z)
#define SPEED aTime*0.2
#define MAX_DIST 40.0
#define MAX_STEP 60
#define LIGHT_DIRECTION vec3(0.8,0.2,1.0)
#define LIGHT_INTENSITY vec3(0.5,1.0,2.0)*2.0
#define SKY_COLOR vec3(0.01, 0.01, 0.02)
#define TERRAIN_COLOR vec3(0.01,0.02,0.03)
#define GLOW_COLOR vec3(0.1,0.85,1.2)
#define RIM_POWER 0.25
#define AA_SIZE 1

vec3 rotateY(vec3 p, float rad) {
    p.x = cos(rad)*p.x + sin(rad)*p.z;
    p.z = -sin(rad)*p.x + cos(rad)*p.z;
    return p;
}

vec3 rotateZ(vec3 p, float rad) {
    p.x = cos(rad)*p.x - sin(rad)*p.y;
    p.y = sin(rad)*p.x + cos(rad)*p.y;
    return p;
}

float sdSphere(in vec3 pos, in float r)
{
    return length(pos)-r;
}

//http://iquilezles.org/www/articles/ellipsoids/ellipsoids.htm
float sdElipsoid(in vec3 pos, in vec3 r)
{
    float k0 = length(pos/r);
    float k1 = length(pos/r/r);
    return k0*(k0-1.0)/k1;
}

float sdRoundBox( vec3 p, vec3 b, float r )
{
  vec3 q = abs(p) - b;
  return length(max(q,0.0)) + min(max(q.x,max(q.y,q.z)),0.0) - r;
}

float sdCylinder( vec3 p, vec3 c )
{
  return length(p.xz-c.xy)-c.z;
}

float sdCappedCylinder( vec3 p, float h, float r )
{
  vec2 d = abs(vec2(length(p.xz),p.y)) - vec2(h,r);
  return min(max(d.x,d.y),0.0) + length(max(d,0.0));
}

float sdCappedCone( in vec3 p, in float h, in float r1, in float r2 )
{
    vec2 q = vec2( length(p.xz), p.y );
    
    vec2 k1 = vec2(r2,h);
    vec2 k2 = vec2(r2-r1,2.0*h);
    vec2 ca = vec2(q.x-min(q.x,(q.y<0.0)?r1:r2), abs(q.y)-h);
    vec2 cb = q - k1 + k2*clamp( dot(k1-q,k2)/dot(k2,k2), 0.0, 1.0 );
    float s = (cb.x<0.0 && ca.y<0.0) ? -1.0 : 1.0;
    return s*sqrt( min(dot(ca,ca),dot(cb,cb)) );
}

//http://iquilezles.org/www/articles/smin/smin.htm
float smin(in float a, in float b, in float k)
{
    float h = max( k - abs(a-b), 0.0);
    return min(a,b) - h*h*0.25/k;
}

float smax(in float a, in float b, in float k)
{
    float h = max( k - abs(a-b), 0.0);
    return max(a,b) + h*h*0.25/k;
}

vec4 sdUnion(vec4 d1, vec4 d2)
{
    return (d1.x<d2.x)? d1:d2;
}

vec3 opRepLim( in vec3 p, in float s, in vec3 lim )
{
    return p-s*clamp(round(p/s),-lim,lim);
}

vec3 opRep( vec3 p, vec3 c )
{
    return mod(p,c)-0.5*c;
}

float tombStone(vec3 p) {
    float b = 4.0;
    vec3 rep = vec3(mod(abs(p.x),b)-2.5, p.y, mod(abs(p.z+1.5), b)-2.5);
    vec2 id = vec2(floor(abs(p.x)/b),floor((p.z+1.5)/b));
    float fid = id.x*133.3 + id.y*311.7;
    float wr = 0.5+0.3*sin(fid);
        
    vec3 newP = rotateY(rep, wr);
    newP = rotateZ(newP, 0.2*wr);
    float d = sdRoundBox( newP, vec3(wr*0.5, wr, 0.04), 0.03);
    return d;
}
float streetlight(vec3 p) {
    p -= vec3(0.0,0.5,-2.0);
    
    //vec3 rep = opRepLim(p, 8.0, vec3(2.0, 0.0, 2.0));
    vec3 rep = opRep(p, vec3(12.0, 0.0, 12.0));  
    float t = iTime*0.5;
    float d = sdCappedCone(rep, 5.0+2.0*sin(p.x*0.2+p.z*0.5+t), 0.01, 1.8 ) + 0.1*sin(p.z*3.0+p.y*2.0+iTime);
    d *= 0.6;
    return d;
}

vec4 map(in vec3 pos, float aTime)
{
    vec4 res = vec4(0.0, 0.0, 0.0, 0.0);
    float t = fract(aTime);
    float y = 3.0*t*(1.0-t); 
    float sinT = sin(aTime);
    vec3 cen = vec3(0.0,0.55,SPEED);
    float sy = 0.8 + 0.2*y;
    float sz = 1.0/sy;
    vec3 r = vec3(0.35,0.32+0.05*sy,0.25+0.05*sz);
    vec3 q = pos-cen;
    vec3 h = q;
    
    //body 
    float ta0 = step(fract(aTime*10.0),0.99);
    h.z -= 3.0;
    h.z += ta0*9.0;
    float d = sdElipsoid(h-vec3(0.0, 0.03, -0.09), r);
    {
    
    //head 
    float d1 = sdElipsoid(h - vec3(0.0, 0.32, 0.01), vec3(0.28, 0.2, 0.18));
    float d2 = sdElipsoid(h - vec3(0.0, 0.30, -0.07), vec3(0.2));
    d2 = smin(d1, d2, 0.08);
    d = smin(d, d2, 0.08);
    
    vec3 sh = vec3(abs(h.x), h.yz);//symmetric along x
    
    //ears
    d2 = sdElipsoid(sh - vec3(0.15, 0.47, -0.05), vec3(0.06, 0.09, 0.04));
    d = smin(d, d2, 0.08);
    res = vec4(d,2.0,0.0,0.0);  
     
    //eyes
    float d3 = sdSphere(sh - vec3(0.068,0.4,0.108), 0.07);
    res = sdUnion(res, vec4(d3,3.0,0.0,0.0));
    
    //terrain
    float fh = -0.05+0.05*(sin(2.5*pos.x) + sin(1.5*pos.z));
    float d4 = pos.y - fh;
    d4 -= .01*texture(iChannel0, pos.xz).x;
    res = sdUnion(res, vec4(d4,0.0,0.0,0.0));
    
    //tombstone
    float d5 = tombStone(pos);
    res = sdUnion(res, vec4(d5,1.0,0.0,0.0));
    
    //light
    float d6 = streetlight(pos);
    res = sdUnion(res, vec4(d6,4.0,0.0,0.0));
    
    float glow = d6;
    res.w = glow;
        
    float shadow = min(d4, d5); 
    res.z = shadow;
    }
    
    return res;
}

float calcOcclusion( in vec3 pos, in vec3 nor, in float aTime)
{
	float occ = 0.0;
    float sca = 1.0;
    for( int i=0; i<4; i++ )
    {
        float h = 0.01 + 0.32*float(i)/4.0;
        vec3 opos = pos + h*nor;
        float d = map( opos, aTime ).x;
        occ += (h-d)*sca;
        sca *= 0.85;
        
    }
    return clamp( 1.0 - 2.0*occ, 0.0, 1.0 );
}

vec3 calcNormal(in vec3 pos, in float aTime)
{
    vec2 e = vec2(0.001,0.0);
    return normalize( vec3(map(pos + e.xyy,aTime).x - map(pos - e.xyy,aTime).x,
                          map(pos + e.yxy,aTime).x - map(pos - e.yxy,aTime).x,
                          map(pos + e.yyx,aTime).x - map(pos - e.yyx,aTime).x) );
}

float castShadow(in vec3 ro, in vec3 rd, in float aTime)
{
    float res = 1.0;
    float t = 0.01;
    float tMax = MAX_DIST;
    
    for (int i=0; i<MAX_STEP;++i)
    {
        vec3 pos = ro + t*rd;
        float h = map(pos,aTime).z;
        res = min( res, 16.0*h/t );
        if (res<0.01 || t > tMax) break;
        t += h;
    }
    return clamp(res, 0.0, 1.0);
}

vec4 castRay(in vec3 ro, in vec3 rd, in float aTime)
{
   float t = 0.01;
   vec4 m = vec4(0.0);
   float tMax = MAX_DIST;
   float minDist = MAX_DIST;
   for ( int i = 0; i < MAX_STEP; ++i )
   {
       vec3 pos = ro + t*rd;
       vec4 h = map( pos,aTime );
       minDist = min(minDist, h.w/t);
       m.x = t;
       m.y = h.y;
       m.z = minDist;
       m.w = h.w;
       
       if ( abs(h.x)<(0.001*t) || t>tMax ) break;
       t += h.x;
   } 
    
   if ( t>tMax )
   {
       m.x = MAX_DIST;
       m.y = -1.0;
       m.w = MAX_DIST;
   }
   
   return m;
}

vec3 render(in vec2 fragCoord, in float aTime)
{
    vec2 p = (2.0*fragCoord.xy - iResolution.xy)/iResolution.y;
    vec2 mouse = 2.0*iMouse.xy/iResolution.xy-1.0;
    float angle = 10.0*iMouse.x/iResolution.x;
    
    vec3 ta = vec3(0.0,0.85,3.0+SPEED);
    vec3 ro = ta+vec3( 1.0*sin(angle), 0.0, 1.0*cos(angle) );;
    
    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize( cross( ww, vec3(0.0, 1.0, 0.0) ) );
    vec3 vv = normalize( cross( uu, ww ) );
    
    
    vec3 rd = normalize(p.x * uu + p.y * vv + 2.0*ww);
    vec3 mDir = LIGHT_DIRECTION;
    vec3 bg = SKY_COLOR;
    vec3 col = bg;
    
    vec2 uv = rd.xz/rd.y; //sky dome( intersect the top )
    
    vec4 tm = castRay(ro, rd, aTime);
    float t = tm.x;
    vec3 pos = ro + t*rd;
    vec3 nor = calcNormal(pos, aTime);
    vec3 mate = vec3(0.0);
    float occ = calcOcclusion( pos, nor, aTime );
    float fresnel = clamp(1.0+dot(nor,rd),0.0,1.0);
    float mDiff = clamp( dot(nor, mDir),0.0,1.0 );
    float mShadow = castShadow(pos+nor*0.01, mDir, aTime);
    if ( tm.x < MAX_DIST )
    {  
        if (tm.y == 0.0) {
            mate = TERRAIN_COLOR;//terrain
        } 
        else if (tm.y == 1.0){
           mate = vec3(0.1);
           mate += RIM_POWER*GLOW_COLOR*fresnel;    
        }
        else if (tm.y==2.0)
        {
            mate = vec3(0.5); //body
            mDiff = 0.0;
        } 
        else if (tm.y==3.0)
        {
            mate = vec3(1.0); //eyes
            mDiff = 1.0;
        }
        else if (tm.y==4.0)
        {
            mate = vec3(0.0,0.5,0.5); //light
            mate += step(fract(pos.y*3.0),0.25)*vec3(2.0,0.0,2.0);
    		mate += step(fract(pos.y*1.5),0.15)*vec3(0.0,2.0,2.0);
            mDiff = 1.0;
            mShadow = 1.0;
            occ = 1.0;
        }  
       
        col = mate*LIGHT_INTENSITY*mDiff*mShadow;
        col *= occ*occ; 
        
    } 
    
    // fog
    col = mix( col, vec3(0.0,0.05,0.05), 1.0-exp( -0.003*t*t ) );
    
    float glow = 0.1*exp(-64.0*tm.z); 
    col += GLOW_COLOR*glow;
    return col;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec3 col = vec3(0.0);
    vec2 off = vec2(0.0);
    
#if AA_SIZE>1
    //anti aliasing & motion blur
    for (float aaY = 0.0; aaY < float(AA_SIZE); ++aaY)
    {
        for (float aaX = 0.0; aaX < float(AA_SIZE); ++aaX)
        {
            off = -0.5+vec2(aaY,aaX)/float(AA_SIZE);
            
            float md = texelFetch(iChannel0, ivec2(fragCoord)&255, 0).x;
            float mb = (aaY*float(AA_SIZE)+aaX)/(float(AA_SIZE*AA_SIZE-1));
            mb += (md-0.5)/float(AA_SIZE*AA_SIZE);
            float aTime = iTime - mb*0.5*(1.0/24.0); //1 frame in 24fps for film
#else
            float aTime = iTime;
#endif
            col += render(fragCoord+off, aTime);
             
            
#if AA_SIZE>1
        }
    }
    col /= float(AA_SIZE*AA_SIZE);
#endif
    
    //gamma
    col = pow( col, vec3(0.4546));
   
    // vignetting        
    vec2 q = fragCoord/iResolution.xy;
    col *= 0.5 + 0.5*pow(16.0*q.x*q.y*(1.0-q.x)*(1.0-q.y),0.25);
    fragColor = vec4(col,1.0);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}