/*
 * Original shader from: https://www.shadertoy.com/view/7dlfzS
 */

#ifdef GL_ES
precision highp float;
#endif

// glslsandbox uniforms
uniform float time;
uniform vec2 resolution;

// shadertoy emulation
#define iTime time
#define iResolution resolution

// --------[ Original ShaderToy begins here ]---------- //
/**
Gaz smol raymarch + meger sponge fracal from previous week shader (lost ref T_T))


*/

#define R(p,a,t) (mix(a*dot(p,a),p,cos(t))+sin(t)*cross(p,a))
mat2 rot(float a){float c=cos(a),s=sin(a);return mat2(c,-s,s,c);}
// distance to a menger sponge of n = 1
float crossDist( in vec3 p ) {
  
    vec3 absp = abs(p);
  
    //return length(p.yx+sin(p.z)*.05)-2.7;
    // get the distance to the closest axis
    float maxyz = max(absp.y, absp.z);
    float maxxz = max(absp.x, absp.z);
    float maxxy = max(absp.x, absp.y);
    float cr = 1.0 - (step(maxyz, absp.x)*maxyz+step(maxxz, absp.y)*maxxz+step(maxxy, absp.z)*maxxy);
    // cube
    float cu = max(maxxy, absp.y) - 3.0;
    // remove the cross from the cube
    return max(cr, cu);
}

// menger sponge fractal
float fractal( in vec3 p ) {
    vec3 pp = p;
    float scale = 1.0;
    float dist = 0.0;
    for (int i = 0 ; i < 6 ; i++) {
    
        dist = max(dist, crossDist(p)*scale);
        
        p = fract((p-1.0)*0.5) * 6.0 - 3.0;
        scale /= 3.;
        //p.yz*=rot(.785);
        
    }

    return dist;
}
vec3 pal(float t){ return vec3(.4,.5,.6)+vec3(.2,.3,.5)*cos(6.28*(vec3(.2,.2,.3)*t+vec3(.2,.5,.8)));}
void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
    vec3 p,c=vec3(0.);
    vec3 d = normalize(vec3(fragCoord.xy -.5*iResolution.xy,iResolution.y));
    float s,e,g=0.,t=iTime;
    for(float i=1.;i<=50.;i++){
        p=g*d;
        p = R(p,normalize(vec3(.1+cos(iTime*.5+p.z*.2)*.5,sin(iTime*.5+p.z*.2)*.5,.5)),.5+t*.2);

        p.z +=iTime;
        p=asin(cos(p))-vec3(2,4,1);
        p = mod(p,4.)-2.;
        s=1.;
        
        g+=e=max(.0001,abs(fractal(p))+.0009);
        c+=2.*sqrt(pal(p.z/6.28))*.02/exp(i*i*e);
    }
    c*=c;
    fragColor = vec4(c,1.0);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}