#ifdef GL_ES
precision mediump float;
#endif

#extension GL_OES_standard_derivatives : enable

uniform float time;
uniform vec2 mouse;
uniform vec2 resolution;

struct ray
{
	vec3 origin;
	vec3 direction;
	float lengthh;
};
	
vec4 opu(vec4 a, vec4 b)
{
	return a.w < b.w ? a : b;	
}

float height(in vec2 p)
{
    return -abs(sin(p.y * fract(p.x * 1.) + time * 1.5)*0.05 + sin(p.x*0.2)*1.12) * 1.2;
}

vec4 objects(vec3 p)
{
	if(!(p.x < 0.5 && p.x > -0.5))
	p.y += height(p.xz);
	//p += sin(p * 20. + time * 5.0)*0.06;
	return vec4(0.3,0.2,0.5, p.y + 0.3);	
}
	
float rendering(ray r, out vec3 color)
{
	for(int i = 0; i < 64; i++)
	{
		vec3 p = r.origin + r.direction * r.lengthh;
		vec4 obj = objects(p);
		r.lengthh += obj.w;
		color = obj.rgb;
		if(r.lengthh > 8.)
			break;
	}
	
	return r.lengthh;
}

vec3 normal(vec3 p)
{
	vec2 offset = vec2(0.001,0.000);
	float copy = objects(p).w;
	return normalize(copy - vec3(objects(p - offset.xyy).w, objects(p - offset.yxy).w, objects(p - offset.yyx).w));
}

float lighting(vec3 p)
{
	vec3 lightPos = vec3(cos(time * 2.0) * 0.5, 2.0, 0.);
	vec3 lightDir = normalize(lightPos - p);
	vec3 n = normal(p);
	
	float light = clamp(dot(lightDir, n),0.02,1.);
	light += pow(max(dot(lightDir, n),0.),90.) * 3.;
	
	return light;
}

#define MAX_RENDER 8.

void main( void ) 
{
	vec2 uv = ( gl_FragCoord.xy - 0.5 * resolution.xy ) / resolution.x;
	vec3 col = vec3(0.);
	
	ray r = ray(vec3(0.), vec3(uv,1.), 0.);
	
	float render = rendering(r, col);
	vec3 p = r.origin + r.direction * render;
	col *= lighting(p);
	vec3 pp = p*5.;
	pp.z += time * 6.5;
	vec3 gridcol = vec3(0.3,0.2,0.5)*2.;
	if(fract(pp.z) > 0.92) col = gridcol;
	if(fract(pp.x) > 0.98) col = gridcol;
	col *= 1. - exp(-0.02 * pow(render, 8.));
	if(p.z > MAX_RENDER) col = vec3(0.4,0.2,0.5)*0.5;
	
	gl_FragColor = vec4( col, 1.0 );
}