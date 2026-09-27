uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uExposure;
uniform float uBloomStrength;
uniform float uSaturation;
uniform float uContrast;
uniform float uVignette;
uniform float uGrain;
uniform float uWasted;
uniform float uHurt;
uniform float uTime;
uniform vec3 uGrade;      // warm/cool balance applied in linear space
uniform vec2 uResolution;
uniform float uHdr;
varying vec2 vUV;

vec3 acesFitted(vec3 color) {
    color = mat3(.59719, .07600, .02840, .35458, .90834, .13383, .04823, .01566, .83777) * color;
    vec3 a = color * (color + .0245786) - .000090537;
    vec3 b = color * (.983729 * color + .4329510) + .238081;
    color = a / b;
    color = mat3(1.60475, -.10208, -.00327, -.53108, 1.10813, -.07276, -.07367, -.00605, 1.07602) * color;
    return clamp(color, 0.0, 1.0);
}
float hash(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * .1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }

void main() {
    vec3 color = texture2D(uScene, vUV).rgb;
    color += texture2D(uBloom, vUV).rgb * uBloomStrength;
    color *= uExposure * uGrade;
    vec3 mapped = uHdr > .5 ? acesFitted(color * 1.1) : clamp(color, 0.0, 1.0);
    float l = dot(mapped, vec3(.2126, .7152, .0722));
    mapped = mix(vec3(l), mapped, uSaturation * (1.0 - uWasted * .92));
    mapped = clamp((mapped - .5) * uContrast + .5, 0.0, 1.0);
    // "Wasted": washed out, cold and slightly darker, like the classic death camera.
    mapped = mix(mapped, mapped * vec3(.92, .95, 1.02) * .82 + .06, uWasted);
    vec2 c = vUV - .5;
    c.x *= uResolution.x / uResolution.y;
    float vig = 1.0 - uVignette * smoothstep(.35, 1.05, length(c));
    mapped *= vig;
    mapped = mix(mapped, mapped * vec3(1.0, .25, .22), uHurt * smoothstep(.2, .9, length(c)));
    mapped = pow(mapped, vec3(1.0 / 2.2));
    mapped += (hash(gl_FragCoord.xy + fract(uTime * 7.13) * 91.0) - .5) * uGrain;
    gl_FragColor = vec4(mapped, dot(mapped, vec3(.299, .587, .114)));
}
