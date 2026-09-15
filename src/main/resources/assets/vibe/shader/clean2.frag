// is PLASMA SHIT V2 YES 
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
const vec2 vp = vec2(30.0, 3.0);

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
	float t = iTime * 1.0;// + iMouse.x;
	vec2 uv = fragCoord.xy / iResolution.xy;
	uv-=0.5;
	
    vec2 p0 = (uv - 0.5) * vp;
    vec2 hvp = vp * 0.5;
	vec2 p1d = vec2(cos( t / 9.0),  sin( t / 3.0)) * hvp - p0;
	vec2 p2d = vec2(sin(-t / 14.0), cos(-t / 4.0)) * hvp - p0;
	vec2 p3d = vec2(cos(-t / 15.0), cos( t / 5.0))  * hvp - p0;
    float sum = 0.5 + 0.5 * (
		cos(length(p1d) / 3.0) +
		cos(length(p2d) / 12.0) +
		sin(length(p3d) / 6.6) * sin(p3d.x / 2.0) * sin(p3d.y / 5.0));
	float ff = sin(iTime*0.3+fract(sum*0.88)*6.28);
	ff=abs(ff-0.2);
	ff=pow(ff,3.3);
    fragColor = vec4(ff*1.7,ff*1.3,ff*2.12,1.0);
	
}


// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}//