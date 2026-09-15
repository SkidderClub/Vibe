/* N130920N fork
 * Original shader from: https://www.shadertoy.com/view/WtG3RD
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

vec4 baseColor = vec4(0.0, 0.2, 0.025, 0.05);

// --------[ Original ShaderToy begins here ]---------- //
//CBS
//Parallax scrolling fractal galaxy.
//Inspired by JoshP's Simplicity shader: https://www.shadertoy.com/view/lslGWr

// http://www.fractalforums.com/new-theories-and-research/very-simple-formula-for-fractal-patterns/
float field(in vec3 p, in int n, float s) {
	float strength = 7. + .03 * log(1.e-6 + fract(sin(iTime) * 4373.11));
	float accum = s/4.;
	float prev = 0.;
	float tw = 0.;
	for (int i = 0; i < 50; ++i) {
		if (i>=n) break;
		float mag = dot(p, p);
		p = abs(p) / mag + vec3(-.15, -.4, -1.5);
		float w = exp(-float(i) / 7.);
		accum += w * exp(-strength * pow(abs(mag - prev), 2.2));
		tw += w;
		prev = mag;
	}
	return max(0., 5. * accum / tw - .7);
}

vec3 nrand3( vec2 co )
{
	vec3 a = fract( cos( co.x*8.3e-3 + co.y )*vec3(1.3e5, 4.7e5, 2.9e5) );
	vec3 b = fract( sin( co.x*0.3e-3 + co.y )*vec3(8.1e5, 1.0e5, 0.1e5) );
	vec3 c = mix(a, b, 0.5);
	return c;
}


void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
	baseColor = vec4(.5, .15, .15, .10);

    	vec2 uv = 2. * fragCoord.xy / iResolution.xy - 1.;
	vec2 uvs = uv * iResolution.xy / min(iResolution.x, iResolution.y)/1.;
	
	vec3 p = vec3(uvs / 2., 0) + vec3(1., -1.3, 0.);
	p += .2 * vec3(sin(1.0 * iTime / 8.), sin(1.0 * iTime / 16.),  sin(1.0 * iTime / 32.));
	
	float t = field(p, 16, baseColor.w);
	float v = (1. - exp((abs(uv.x) - 16.0) * 6.0)) * (1.0 - exp((abs(uv.y) - 1.5) * 1.));
	
	//Second Layer
	vec3 p2 = vec3(uvs / (4.+sin(iTime*0.11)*0.2+0.2+sin(iTime*0.15)*0.3+0.4), 1.5) + vec3(1., -1.3, -1.);
	//vec3 p2 = vec3(1,1,1);
	p2 += 0.25 * vec3(cos(iTime / 32.),  sin(iTime / 32.),  sin(iTime / 64.));
	float t2 = field(p2, 16, baseColor.w);
	//vec4 c2 = mix(v*baseColor.x, v*baseColor.y, v*baseColor.z) * vec4(1. * t2 * t2 * t2 , 1.  * t2 * t2 , t2, t2*baseColor.w);
	vec4 c2 = mix(.4, 1., v) * vec4(1. * t2 * t2 * t2*baseColor.x , 1.  * t2 * t2*baseColor.y , t2*baseColor.z, t2*baseColor.w);
	//vec4 c2 = vec4(0);

	//Thanks to http://glsl.heroku.com/e#6904.0
	vec2 seed = p.xy * 2.0;	
	seed = floor(seed * iResolution.x/8.0);
	vec3 rnd = nrand3( seed );
	vec4 starcolor = vec4(pow(rnd.y,40.0)) * baseColor;
	
	//Second Layer
	vec2 seed2 = p2.xy * 2.0;
	seed2 = floor(seed2 * iResolution.x/4.0);
	vec3 rnd2 = nrand3( seed2 );
	starcolor += vec4(pow(rnd2.y,80.0));
	
	fragColor = mix(baseColor.w-.3, 1., v) * vec4(1.5*baseColor.x * t * t* t , 1.2*baseColor.y * t * t, baseColor.z*t, 1.0)+c2+starcolor;
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}