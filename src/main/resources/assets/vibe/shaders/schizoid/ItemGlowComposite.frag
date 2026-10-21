#version 120
uniform sampler2D Mask;
uniform sampler2D Halo;
uniform vec3 Primary;
uniform vec3 Secondary;
uniform vec2 ColorAlpha;
uniform float Strength;
uniform float Falloff;
uniform float InnerGlow;
uniform vec2 MaskScale;
uniform vec2 MaskOffset;
varying vec2 uv;
void main() {
    vec2 at = uv * MaskScale + MaskOffset;
    float coverage = at.x < 0.0 || at.y < 0.0 || at.x > 1.0 || at.y > 1.0 ? 0.0 : texture2D(Mask, at).a;
    float gradient = smoothstep(0.0, 1.0, uv.y);
    vec3 tint = mix(Primary, Secondary, gradient);
    float strength = Strength * mix(ColorAlpha.x, ColorAlpha.y, gradient);
    float halo = pow(clamp(texture2D(Halo, uv).r * 1.8, 0.0, 1.0), Falloff);
    // Preserve the original item and phone screen, including translucent item pixels.
    halo *= 1.0 - step(.001, coverage);
    vec3 light = tint * (halo + coverage * InnerGlow) * strength;
    // Screen blending adds light to the existing scene without copying or sampling its color.
    gl_FragColor = vec4(clamp(light, 0.0, 1.0), 0.0);
}
