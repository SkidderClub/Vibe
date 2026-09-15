/*
 * Original shader from: https://www.shadertoy.com/view/XdtcD4
 */
//modify test
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
#define RADIUS 20.
#define FAR 280.
#define INFINITY 1e32
#define FOV 80.0

// its from here https://github.com/achlubek/venginenative/blob/master/shaders/include/WaterHeight.glsl 
#define EULER 2.7182818284590452353602874
#define IEULER 0.367879

float wave(vec2 uv, vec2 emitter, float speed, float phase, float timeshift) {
	float dst = distance(uv, emitter);
    float time = iTime*0.1;
	return pow(EULER, sin(dst * phase - (time + timeshift) * speed));
}

float map(vec3 p) {
    float n = 0.;
    
    for (float i = 0.; i < 5.; i+= 1.) {
    	n += wave(p.xz, vec2(sin(i) * RADIUS, cos(i) * RADIUS), 1., .4, i + iTime * 3.);
    }
    
    return p.y - n / length(p) * 4.;
}

float trace(vec3 ro,vec3 rd) {
    float 
        h = .2,
    	dt = 0.;
    
	for(int i = 0; i < 15; i++) {
        if (abs(h) < .1) break;
		h = map(ro + rd * dt);
        dt +=h * .9;
	}
    
    return dt;
}

#define EPSILON .01
vec3 getNormalHex(vec3 pos) {
	float d=map(pos);
	return normalize(
        vec3(map(
            pos+vec3(EPSILON,0,0))-d,
            map(pos+vec3(0,EPSILON,0))-d,
            map(pos+vec3(0,0,EPSILON))-d
        )
    );
}

vec4 doColor( in vec3 sp, in vec3 rd, in vec3 sn, in vec3 lp, float d) {
	lp = sp + lp;
    vec3 ld = lp - sp; 
    float lDist = max(length(ld / 2.), 0.001); 
    ld /= lDist;

	float diff = max(dot(sn, ld), 1.);
    float spec = pow(max(dot(reflect(-ld, sn), -rd), .2), 1.);
	
    return vec4(vec3(.5, 0.7, 1.) * (diff + .15) * spec * .04, 0.);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy - .5;
    
    uv *= tan(radians (FOV) / 2.0) * 1.1;

    vec3 
        light = vec3(10., 15., -20.),      
        vuv = vec3(0., 1., 0.),
    	ro = vec3(7., 44. + sin(iTime) * 4., 60.),
        vrp =  vec3(0.),
    	vpn = normalize(vrp - ro),
    	u = normalize(cross(vuv, vpn)),
    	v = cross(vpn, u),
    	vcv = (ro + vpn),
    	scrCoord = (vcv + uv.x * u * iResolution.x/iResolution.y + uv.y * v),
    	rd = normalize(scrCoord - ro),
		sceneColor;
	
    float d = trace(ro, rd);
    
    ro += rd * d;
    vec3 sn = getNormalHex(ro);	

    if (d < FAR) { 
        sceneColor = doColor(ro, rd, sn, light, d).rgb * (
        	1. + length(
            	max(0.2, 1. * max(
                	    0.3,
                    	length(normalize(light.xy) * max(vec2(0.), sn.xy))
                	))
        ));
    } 
    
    fragColor = pow(sceneColor.rgbb, 2./vec4(2.)) * 4.;
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}