#ifdef GL_ES
precision lowp float;
#endif

#extension GL_OES_standard_derivatives : enable

uniform float time;
uniform vec2 resolution;


void main( void ) {
	vec2 uv = (gl_FragCoord.xy - resolution * 0.7) / max(resolution.x, resolution.y) * 5.0;
	uv *= 0.75;
	
	float e = 0.0;
	for (float i=4.0;i<=18.0;i+=0.22) {
		e += 0.005/abs( (i/15.) +sin((time/2.0) + 0.15*i*(uv.x) *( sin(i/4.0 + cos(sin(time / 2.0)) + uv.x*3.2) ) ) + 2.0*uv.y);
	gl_FragColor = vec4( vec3(e/3.8, e/6.6, e/0.5), 3.0);	

	}
	
}