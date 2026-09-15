/*
 * Original shader from: https://www.shadertoy.com/view/dtBSDK
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
float det=.01,st=.1,sph;
vec3 pos;


mat2 rot(float a)
{
    float s=sin(a),c=cos(a);
    return mat2(c,s,-s,c);
}

float de(vec3 p)
{
    float s=sin(iTime*2.);
    p.xz*=rot(iTime-p.y*.4);
    p.xy*=rot(iTime-p.z*.2);
    sph=length(p)-1.-length(sin(p*3.))*.2;
    sph-=s*s*.5;
    pos=p;
    float d=length(p)-2.;
    d=max(d,-length(p.xy)+3.);
    d=max(d,-length(p.xz)+3.);
    d=max(d,-length(p.yz)+3.);
    d-=length(sin(p*3.))*.9;
    d=min(d,sph);
    return d*.25;
}

vec3 normal(vec3 p)
{
    vec2 e=vec2(0.,det);
    return normalize(vec3(de(p+e.yxx),de(p+e.xyx),de(p+e.xxy))-de(p));
}

vec3 march(vec3 from, vec3 dir) 
{
    float td=0.,d=0.,maxdist=30.,g=0.,ref=0.;
    vec3 p=from,col=vec3(.0);
    for (int i=0; i<100; i++)
    {
        p+=dir*d;
        d=de(p);
        if (td>maxdist) break;
        if (d<det&&ref<1.) 
        {
            ref+=1.;
            vec3 n=normal(p);
            dir=reflect(dir,n);
            p+=det*dir;
        }
        //td+=st;
        td+=d;
        g=max(g,.15/(.1+sph*.5));
    }
    if (d<.1) 
    {
        //vec3 ldir=normalize(vec3(2.,1.,-1.));
        vec3 ldir=normalize(-p);
        vec3 n=normal(p);
        float amb=.3;
        float dif=max(0.,dot(ldir,n))*.5;
        vec3 ref=reflect(dir,n);
        float spe=pow(max(0.,dot(ldir,ref)),10.)*.5;
        col=normalize(abs(fract(pos)-.5))*(amb+dif)+spe;
    }
    else
    {
        p=maxdist*dir;
        if (ref==1.) p=dir;
        p*=length(p.xy)*.015;
        p+=vec3(.3,.2,.1);
        for (int i=0; i<10; i++)
        {
            p=abs(p)/dot(p,p)-.78;
        }
        col+=dot(p,p)*.005*p;
    }
    return col+g*vec3(1.5,.7,.5);
}


void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    // Normalized pixel coordinates (from 0 to 1)
    vec2 uv = (fragCoord-iResolution.xy*.5)/iResolution.y;
    vec3 dir=normalize(vec3(uv,1.));
    float s=sin(iTime*.5);
    float c=cos(iTime*.5);
    dir.xy*=rot(s*.2);
    vec3 from=vec3(c*c*c*2.,0.,-10.+s*s*3.);
    vec3 col=march(from, dir);
    // Output to screen
    fragColor = vec4(col,1.0);
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}