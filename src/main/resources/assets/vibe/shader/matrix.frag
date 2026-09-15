/*
 * Original shader from: https://www.shadertoy.com/view/Ntf3Dj
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
const float PI2 = acos(-1.) * 2.;

float hash1D(in float p)
{
    return fract(sin(p) * 6124.7621);
}

float hash3D(in vec3 p)
{
    vec3 v = vec3(123.4116, 162.3271, 137.1618);
    return fract(sin(dot(p, v)) * 6124.7621);
}

float noise3D(in vec3 p)
{
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3. - 2. * f);
    vec3 b = vec3(156, 12, 5);
    float n = dot(i, b);
	return mix(	mix(	mix(hash1D(n), hash1D(n + b.x), f.x),
		   				mix(hash1D(n + b.y), hash1D(n + b.x + b.y), f.x),
		   				f.y),
		  	 	mix(	mix(hash1D(n + b.z), hash1D(n + b.x + b.z), f.x),
		   				mix(hash1D(n + b.y + b.z), hash1D(n + b.x + b.y + b.z), f.x),
		   				f.y),
		  	 	f.z);
}

float sdSeg(in vec2 p)
{
    p = abs(p);
    return max(p.x - .1, p.x + p.y -.3);
}

float sd0(in vec2 p)
{
    p = abs(p);
    p.y -= .3;
    if(p.y > p.x) {
        p = p.yx;
    }
    p.x -= .3;
    return sdSeg(p);
}

float sd1(in vec2 p)
{
    p.y = abs(p.y);
    p -= .3;
    return sdSeg(p);
}
    
float sd2(in vec2 p)
{
    if(p.y < 0.) {
        p *= -1.;
    }
    p.y = abs(p.y - .3);
    if(p.y > p.x) {
        p = p.yx;
    }
    p.x -= .3;
    return sdSeg(p);
}

float sd3(in vec2 p)
{
    p.y = abs(abs(p.y) - .3);
    if(p.y > p.x) {
        p = p.yx;
    }
    p.x -= .3;
    return sdSeg(p);
}

float sd4(in vec2 p)
{
    float d = sdSeg(p - vec2(-.3, .3));
    p.y = abs(p.y) - .3;
    if(p.y < -p.x) {
        p = -p.yx;
    }
    p.x -= .3;
    return min(d, sdSeg(p));
}

float sd5(in vec2 p)
{
    if(p.y < 0.) {
        p *= -1.;
    }
    p.y = abs(p.y - .3);
    if(p.y > -p.x) {
        p = -p.yx;
    }
    p.x += .3;
    return sdSeg(p);
}

float sd6(in vec2 p)
{
    vec2 q = p;
    q = abs(q);
    q.y = abs(q.y - .3);
    if(q.y > q.x) {
        q = q.yx;
    }
    q.x -= .3;
    return max(sdSeg(q), -sdSeg(p - vec2(.3, .3)));
}

float sd7(in vec2 p)
{
    vec2 q = p;
    q.y -= .3;
    if(q.y > q.x) {
        q = q.yx;
    }
    q.x -= .3;
    return min(sdSeg(q), sdSeg(p - vec2(.3, -.3)));
}

float sd8(in vec2 p)
{
    p = abs(p);
    p.y = abs(p.y - .3);
    if(p.y > p.x) {
        p = p.yx;
    }
    p.x -= .3;
    return sdSeg(p);
}

float sd9(in vec2 p)
{
    vec2 q = p;
    q = abs(q);
    q.y = abs(q.y - .3);
    if(q.y > q.x) {
        q = q.yx;
    }
    q.x -= .3;
    return max(sdSeg(q), -sdSeg(p - vec2(-.3, -.3)));
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 p = (fragCoord * 2. - iResolution.xy)/min(iResolution.x, iResolution.y);
    vec3 col = vec3(0);
    vec2 interval = vec2(3., 1.7);
    
    for(float i=0.; i<20.; i++) {
        float L = 1. - fract(iTime) + i;
        vec2 q = p / atan(.5, L);
        
        L = dot(q,q) * 2. + L * L;
        float I = ceil(iTime) + i;
        float n = hash1D(I);
        q += vec2(cos(n * PI2), sin(n * PI2)) * 3.;
        
        vec3 ID = vec3(ceil(q / interval), I);
        q = mod(q, interval) - interval*.5;
        float n2 = hash3D(ID + ceil(iTime * 3.) * 1.3834);
        ID.y += ceil(iTime * 10.);
        float n3 = noise3D(ID * .316572);
        
        float d = 0.;
        float N = 10.;
        if(n2 < 1. / N) {
            d = sd0(q);
        } else if(n2 < 2. / N) {
            d = sd1(q);
        } else if(n2 < 3. / N) {
            d = sd0(q);
        } else if(n2 < 4. / N) {
            d = sd1(q);
        } else if(n2 < 5. / N) {
            d = sd0(q);
        } else if(n2 < 6. / N) {
            d = sd1(q);
        } else if(n2 < 7. / N) {
            d = sd0(q);
        } else if(n2 < 8. / N) {
            d = sd1(q);
        } else if(n2 < 9. / N) {
            d = sd0(q);
        } else {
            d =sd1(q);
        }
        
        if(col.g == 0. && -.08 < d && d < -.02 && n3 < .5) {
            col.rgb = vec3(.3, 1, .4) * exp(-L * .002) + pow(hash1D(n3), 32.);
        }
    }

    fragColor = vec4(col, 1.0);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}