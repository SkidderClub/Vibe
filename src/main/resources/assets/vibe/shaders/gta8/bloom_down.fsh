uniform sampler2D uTex;
uniform vec2 uTexel;       // texel size of the source
uniform float uPrefilter;  // 1 on the first pass: threshold with a soft knee and firefly suppression
uniform float uThreshold;
varying vec2 vUV;

vec3 fetch(vec2 o) { return texture2D(uTex, vUV + o * uTexel).rgb; }
float karis(vec3 c) { return 1.0 / (1.0 + dot(c, vec3(.2126, .7152, .0722))); }

void main() {
    // 13-tap downsample (Jimenez 2014).
    vec3 a = fetch(vec2(-2.0, 2.0)), b = fetch(vec2(0.0, 2.0)), c = fetch(vec2(2.0, 2.0));
    vec3 d = fetch(vec2(-2.0, 0.0)), e = fetch(vec2(0.0, 0.0)), f = fetch(vec2(2.0, 0.0));
    vec3 g = fetch(vec2(-2.0, -2.0)), h = fetch(vec2(0.0, -2.0)), i = fetch(vec2(2.0, -2.0));
    vec3 j = fetch(vec2(-1.0, 1.0)), k = fetch(vec2(1.0, 1.0)), l = fetch(vec2(-1.0, -1.0)), m = fetch(vec2(1.0, -1.0));
    vec3 color;
    if (uPrefilter > .5) {
        vec3 g0 = (a + b + d + e) * .25, g1 = (b + c + e + f) * .25, g2 = (d + e + g + h) * .25, g3 = (e + f + h + i) * .25, g4 = (j + k + l + m) * .25;
        float w0 = karis(g0), w1 = karis(g1), w2 = karis(g2), w3 = karis(g3), w4 = karis(g4);
        color = (g0 * w0 * .125 + g1 * w1 * .125 + g2 * w2 * .125 + g3 * w3 * .125 + g4 * w4 * .5) / (w0 * .125 + w1 * .125 + w2 * .125 + w3 * .125 + w4 * .5);
        float br = max(color.r, max(color.g, color.b));
        float knee = uThreshold * .5;
        float soft = clamp(br - uThreshold + knee, 0.0, 2.0 * knee);
        soft = soft * soft / (4.0 * knee + 1e-5);
        color *= max(soft, br - uThreshold) / max(br, 1e-5);
    } else {
        color = e * .125 + (a + c + g + i) * .03125 + (b + d + f + h) * .0625 + (j + k + l + m) * .125;
    }
    gl_FragColor = vec4(max(color, vec3(0.0)), 1.0);
}
