#version 120
uniform sampler2D Source;
uniform vec2 Step;
uniform int AlphaMask;
uniform float CoverageY;
uniform vec2 MaskScale;
uniform vec2 MaskOffset;
varying vec2 uv;
float coverage(vec2 at) {
    at = at * MaskScale + MaskOffset;
    // Clamp-to-edge would extend a hand touching the screen edge outside the texture.
    if (at.x < 0.0 || at.y < 0.0 || at.x > 1.0 || at.y > 1.0) return 0.0;
    return texture2D(Source, at).a;
}
float sampleMask(vec2 at) {
    if (AlphaMask == 1) return .5 * (coverage(at + vec2(0.0, CoverageY)) + coverage(at - vec2(0.0, CoverageY)));
    if (at.x < 0.0 || at.y < 0.0 || at.x > 1.0 || at.y > 1.0) return 0.0;
    return texture2D(Source, at).r;
}
void main() {
    float value = sampleMask(uv) * .181861535;
    value += (sampleMask(uv + Step) + sampleMask(uv - Step)) * .164012036;
    value += (sampleMask(uv + Step * 2.0) + sampleMask(uv - Step * 2.0)) * .120304071;
    value += (sampleMask(uv + Step * 3.0) + sampleMask(uv - Step * 3.0)) * .071771936;
    value += (sampleMask(uv + Step * 4.0) + sampleMask(uv - Step * 4.0)) * .034825610;
    value += (sampleMask(uv + Step * 5.0) + sampleMask(uv - Step * 5.0)) * .013743981;
    value += (sampleMask(uv + Step * 6.0) + sampleMask(uv - Step * 6.0)) * .004411600;
    gl_FragColor = vec4(value, value, value, 1.0);
}
