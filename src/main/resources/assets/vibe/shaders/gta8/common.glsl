// GTA8 shared uniforms and helpers. GLSL 1.20.
const float PI = 3.14159265;

uniform vec3 uCameraPos;
uniform vec3 uSunDir;        // key light direction (sun by day, moon by night)
uniform vec3 uSkySunDir;     // the actual sun, for sky scattering
uniform vec3 uMoonDir;
uniform vec3 uSunColor;      // direct light (irradiance / pi), includes cloud dimming
uniform vec3 uSkyAmbient;    // sky irradiance from above
uniform vec3 uGroundAmbient; // bounce light from below
uniform vec3 uBetaR;         // atmosphere coefficients, computed on the CPU
uniform vec3 uBetaM;
uniform float uSunE;
uniform float uMoonE;
uniform float uTime;
uniform float uNight;        // 0 day .. 1 night (drives artificial lights)
uniform float uWet;          // surface wetness
uniform float uRain;         // rain intensity
uniform float uCloud;        // cloud cover 0..1
uniform float uFogDensity;
uniform float uLightning;
uniform sampler2D uNoise;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float hash13(vec3 p3) {
    p3 = fract(p3 * .1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}
// Hardware-filtered value noise (smooth channel) and a 3-octave fbm.
float vnoise(vec2 p) { return texture2D(uNoise, p * (1.0 / 256.0)).g; }
float wnoise(vec2 p) { return texture2D(uNoise, p * (1.0 / 256.0)).r; }
float fbm(vec2 p) { return vnoise(p) * .5 + vnoise(p * 2.03 + 17.1) * .3 + vnoise(p * 4.1 + 31.7) * .2; }

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3 saturate(vec3 x) { return clamp(x, 0.0, 1.0); }
float luma(vec3 c) { return dot(c, vec3(.2126, .7152, .0722)); }

// ---------------------------------------------------------------- atmosphere
float rayleighPhase(float c) { return 3.0 / (16.0 * PI) * (1.0 + c * c); }
float hgPhase(float c, float g) { float g2 = g * g; return (1.0 / (4.0 * PI)) * ((1.0 - g2) / pow(1.0 - 2.0 * g * c + g2, 1.5)); }

// Preetham-style single scattering (after Hoffman & Preetham), returned as linear HDR radiance.
vec3 scatter(vec3 dir, vec3 sun, float sunE, out vec3 extinction) {
    float zen = acos(max(0.0, dir.y));
    float inv = 1.0 / (cos(zen) + .15 * pow(93.885 - zen * 57.2958, -1.253));
    vec3 fex = exp(-(uBetaR * 8.4e3 * inv + uBetaM * 1.25e3 * inv));
    extinction = fex;
    float c = dot(dir, sun);
    vec3 bR = uBetaR * rayleighPhase(c * .5 + .5);
    vec3 bM = uBetaM * hgPhase(c, .76);
    vec3 ratio = (bR + bM) / (uBetaR + uBetaM);
    vec3 lin = pow(sunE * ratio * (1.0 - fex), vec3(1.5));
    lin *= mix(vec3(1.0), pow(sunE * ratio * fex, vec3(.5)), saturate(pow(1.0 - sun.y, 5.0)));
    return lin * .04;
}
vec3 skyRadiance(vec3 dir) {
    vec3 ext, extM;
    vec3 day = scatter(dir, uSkySunDir, uSunE, ext);
    vec3 moon = scatter(dir, uMoonDir, uMoonE, extM) * vec3(.55, .7, 1.25);
    vec3 night = vec3(.0022, .0036, .0075) * (1.0 - .6 * saturate(dir.y));
    vec3 sky = (day + moon) * .075;
    sky = mix(vec3(luma(sky)), sky, .8) + night * .35;
    // Overcast: a grey, low-contrast dome.
    float grey = luma(uSkyAmbient) * 1.15;
    vec3 overcast = mix(vec3(grey * .9), vec3(grey * 1.1), saturate(dir.y * 2.0 + .3));
    sky = mix(sky, overcast, uCloud * uCloud * .85);
    return sky + uLightning * vec3(.8, .85, 1.0) * saturate(dir.y + .3);
}
// Reflection environment: sky above, a darkened skyline with scattered lit windows at night below.
vec3 environment(vec3 R, float rough, float city) {
    vec3 sky = skyRadiance(normalize(vec3(R.x, max(R.y, .02), R.z)));
    vec2 cell = vec2(atan(R.z, R.x) * 90.0, R.y * 120.0);
    // Rippled or rough films blur the window grid into its average instead of sparkling.
    float footprint = max(fwidth(cell.x), fwidth(cell.y));
    float lights = mix(step(.93, hash12(floor(cell))), .07, saturate(footprint * 1.5 + rough * 8.0 - .1));
    vec3 skyline = uSkyAmbient * .28 + uGroundAmbient * .2 + lights * uNight * vec3(1.2, .9, .6) * .35;
    float building = (1.0 - smoothstep(-.02, .22 + .08 * vnoise(vec2(atan(R.z, R.x) * 40.0, 3.0)), R.y));
    vec3 env = mix(sky, skyline, building * .85 * city);
    vec3 diffuse = mix(uGroundAmbient, uSkyAmbient, R.y * .5 + .5);
    return mix(env, diffuse, saturate(rough * rough * 1.2));
}

// Exponential height fog, coloured by the sky in the view direction (aerial perspective).
vec3 applyFog(vec3 color, vec3 world) {
    vec3 d = world - uCameraPos;
    float dist = length(d);
    vec3 dir = d / max(dist, .001);
    float fall = .011;
    float dy = d.y * fall;
    float integral = abs(dy) > 1e-4 ? (1.0 - exp(-dy)) / dy : 1.0;
    float amount = uFogDensity * exp(-max(uCameraPos.y, 0.0) * fall) * integral * dist;
    amount = 1.0 - exp(-amount);
    vec3 fogColor = skyRadiance(normalize(vec3(dir.x, max(dir.y, .04) * .35 + .02, dir.z)));
    fogColor += uSunColor * .35 * pow(saturate(dot(dir, uSunDir)), 8.0) * (1.0 - uCloud * .7);
    return mix(color, fogColor, saturate(amount));
}

vec3 acesFitted(vec3 color) {
    color = mat3(.59719, .07600, .02840, .35458, .90834, .13383, .04823, .01566, .83777) * color;
    vec3 a = color * (color + .0245786) - .000090537;
    vec3 b = color * (.983729 * color + .4329510) + .238081;
    color = a / b;
    color = mat3(1.60475, -.10208, -.00327, -.53108, 1.10813, -.07276, -.07367, -.00605, 1.07602) * color;
    return saturate(color);
}
