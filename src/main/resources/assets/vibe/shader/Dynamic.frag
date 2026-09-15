#ifdef GL_ES
precision highp float;
#endif

#extension GL_OES_standard_derivatives : enable

uniform float time;
uniform vec2  mouse;
uniform vec2 resolution;

void main( void ) {
	vec2 uv = (gl_FragCoord.xy - resolution * 0.7) / max(resolution.x, resolution.y) * 2.998;
	vec2 position = gl_FragCoord.xy / resolution.xy;
	float t = 270.255 / abs(abs(tan(1.)) - length(uv));
	uv *= 1.0;
	
	float e = 0.25;
	for (float i=3.0;i<=15.0;i+=1.0) {
		e += 0.01/abs( (i/15.) +sin((time/2.0) + 0.15*i*(uv.x) *( cos(i/4.0 + (time / 2.0) + uv.x*2.2) ) ) + 2.5*uv.y);
	
	gl_FragColor = vec4( vec3(e/1.5, e/1.5, e/0.5), 1.6);
	//gl_FragColor.r = 1.0;
	gl_FragColor.g = mouse.x / 15.0 + 0.128;
	gl_FragColor.b = uv.y / 10.0 + 0.25;
	gl_FragColor.a = uv.y + 1.4;
	}

}