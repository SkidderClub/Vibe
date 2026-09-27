uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uEnabled;
uniform float uSharpen;
varying vec2 vUV;

float lumaOf(vec4 c) { return c.a; }

void main() {
    vec4 m = texture2D(uTex, vUV);
    if (uEnabled < .5) { gl_FragColor = vec4(m.rgb, 1.0); return; }
    float nw = lumaOf(texture2D(uTex, vUV + vec2(-1.0, -1.0) * uTexel));
    float ne = lumaOf(texture2D(uTex, vUV + vec2(1.0, -1.0) * uTexel));
    float sw = lumaOf(texture2D(uTex, vUV + vec2(-1.0, 1.0) * uTexel));
    float se = lumaOf(texture2D(uTex, vUV + vec2(1.0, 1.0) * uTexel));
    float lm = m.a;
    float lmin = min(lm, min(min(nw, ne), min(sw, se)));
    float lmax = max(lm, max(max(nw, ne), max(sw, se)));
    vec2 dir = vec2(-((nw + ne) - (sw + se)), (nw + sw) - (ne + se));
    float reduce = max((nw + ne + sw + se) * (.25 * (1.0 / 8.0)), 1.0 / 128.0);
    float rcp = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);
    dir = clamp(dir * rcp, vec2(-8.0), vec2(8.0)) * uTexel;
    vec3 a = .5 * (texture2D(uTex, vUV + dir * (1.0 / 3.0 - .5)).rgb + texture2D(uTex, vUV + dir * (2.0 / 3.0 - .5)).rgb);
    vec3 b = a * .5 + .25 * (texture2D(uTex, vUV + dir * -.5).rgb + texture2D(uTex, vUV + dir * .5).rgb);
    float lb = dot(b, vec3(.299, .587, .114));
    vec3 color = (lb < lmin || lb > lmax) ? a : b;
    // Mild contrast-adaptive sharpening recovers texture detail lost to FXAA and upscaling.
    if (uSharpen > 0.0) {
        vec3 n = texture2D(uTex, vUV + vec2(0.0, -uTexel.y)).rgb, s = texture2D(uTex, vUV + vec2(0.0, uTexel.y)).rgb;
        vec3 e = texture2D(uTex, vUV + vec2(uTexel.x, 0.0)).rgb, w = texture2D(uTex, vUV + vec2(-uTexel.x, 0.0)).rgb;
        float amount = uSharpen * (1.0 - smoothstep(.05, .25, lmax - lmin));
        color = color + (color * 4.0 - n - s - e - w) * amount * .12;
    }
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
