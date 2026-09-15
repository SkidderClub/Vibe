/*
 * Original shader from: https://www.shadertoy.com/view/fls3RS
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
#define MAX_STEPS 100
#define MAX_DIST 1000.0
#define MIN_DIST 0.3
#define pi acos(-1.0)
#define sat(t) clamp(t, 0.0, 1.0)

// 2D rotation
vec2 rot(vec2 p, float a) {
	return p*mat2(cos(a), sin(a), -sin(a), cos(a));
}

// Random number between 0 and 1
float rand(vec2 p) {
	return fract(sin(dot(p, vec2(12.543,514.123)))*4732.12);
}

// Value noise
float noise(vec2 p) {
	vec2 f = smoothstep(0.0, 1.0, fract(p));
	vec2 i = floor(p);
	
	float a = rand(i);
	float b = rand(i+vec2(1.0,0.0));
	float c = rand(i+vec2(0.0,1.0));
	float d = rand(i+vec2(1.0,1.0));
	
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
	
}

// Fractal brownian motion
float fbm(vec2 p) {
    float a = 0.5;
    float r = 0.0;
    for (int i = 0; i < 8; i++) {
        r += a*noise(p);
        a *= 0.5;
        p *= 2.0;
    }
    return r;
}

// Sky SDF
float sky(vec3 p) {
    vec2 puv = p.xz;
    // Move clouds
    puv += vec2(-2, 4)*iTime;
    // Plane with distortion
	return 0.4*(-p.y+25.0+noise(puv/20.0)*1.5*fbm(puv/7.0));
}

// Mountains SDF
float mountains(vec3 p) {
    // Add slope so it forms a valley
    float addSlope = -clamp(abs(p.x/20.0), 0.0, 7.0);
    // Increase intensity of distortion as it moves away from the river
    float rockDist = clamp(2.0*abs(p.x/3.0), 0.0, 30.0);
    // Rock formations
    float rocks = fbm(vec2(0, iTime/5.0)+p.xz/15.0);
    // Plane with distortion
    return p.y-rockDist*rocks+addSlope+10.0;
}

// River SDF
float river(vec3 p) {
    // Rocks that disturb the flow of the river
    float rocks = 0.75*pow(noise(p.xz/6.0+vec2(0, iTime/1.5)),2.0);
    // Surface waves
    float waves = fbm(noise(p.xz/4.0)+p.xz/2.0-vec2(0,iTime/1.5-pow(p.x/7.0,2.0)));
    // Plane with distortion
    return p.y+4.0-rocks+waves;
}

// Scene
float Dist(vec3 p) {
	return min(river(p), min(mountains(p), sky(p)));
}

// Classic ray marcher that returns both the distance and the number of steps
vec2 RayMarch(vec3 cameraOrigin, vec3 rayDir) {
	float minDist = 0.0;
	int steps = 0;
	for (int i = 0; i < MAX_STEPS; ++i) {
		vec3 point = cameraOrigin + rayDir * minDist;
		float d = Dist(point);
		minDist += d;
		if(d < MIN_DIST || minDist > MAX_DIST) {
			break;
		}
		steps++;
	}
	return vec2(minDist, steps);
}

// Main
void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
	vec2 uv = fragCoord.xy/(iResolution.y);
	uv -= iResolution.xy/iResolution.y/2.0;
    // Camera setup
	vec3 cameraOrigin = vec3(0, noise(vec2(0, iTime / 2.5)) - 2.5, 0);
	vec3 ray = normalize(vec3(uv.x, uv.y, 0.4));
    // Camera sway
    ray.yz = rot(ray.yz, +mix(-0.0 , 0.0, 0.0*noise(vec2(0, 0.0+iTime/1.0))+noise(vec2(1.0-iTime/1.0))));
    ray.xz = rot(ray.xz, mix(0.0 , 0.0, noise(vec2(1.0+iTime/1.0))));
    // Ray March
    vec2 rm = RayMarch(cameraOrigin, ray);
    // Color is based on the number of steps and distance
	vec4 col = pow(vec4(rm.y/100.0),vec4(3.0))+pow(rm.x/MAX_DIST,2.5);
    // Gamma correction
	fragColor = pow(col, vec4(1.0/9.2));
}
// --------[ Original ShaderToy ends here ]---------- //

void main(void)
{
    mainImage(gl_FragColor, gl_FragCoord.xy);
}