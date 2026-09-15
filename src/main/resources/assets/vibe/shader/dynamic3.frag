#ifdef GL_ES
precision highp float;
#endif

#extension GL_OES_standard_derivatives : enable

uniform float time;
uniform vec2 resolution;


void main( void ) {
	vec2 uv = (gl_FragCoord.xy - resolution * 0.7) / max(resolution.x, resolution.y) * 3.0;
	uv *= 1.0;
	
	float e = 0.0;
	for (float i=3.0;i<=15.0;i+=1.0) {
		e += 0.007/abs( (i/15.) +sin((time/2.0) + 0.15*i*(uv.x) *( cos(i/4.0 + (time / 2.0) + uv.x*2.2) ) ) + 2.5*uv.y);
	gl_FragColor = vec4( vec3(e/5.5, e/1.6, e/0.6), 1.0);	

	}
	
}