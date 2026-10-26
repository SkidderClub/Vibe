#version 120
uniform sampler2D Mask;
uniform sampler2D Halo;
uniform vec3 Primary;
uniform vec3 Secondary;
uniform vec2 ColorAlpha;
uniform float Strength;
uniform float Falloff;
uniform float InnerGlow;
uniform int InnerMode;
uniform float InnerPhase;
uniform float InnerWidth;
uniform vec2 MaskScale;
uniform vec2 MaskOffset;
varying vec2 uv;
void main() {
    vec2 at = uv * MaskScale + MaskOffset;
    float coverage = at.x < 0.0 || at.y < 0.0 || at.x > 1.0 || at.y > 1.0 ? 0.0 : texture2D(Mask, at).a;
    float gradient = smoothstep(0.0, 1.0, uv.y);
    vec3 tint = mix(Primary, Secondary, gradient);
    float strength = Strength * mix(ColorAlpha.x, ColorAlpha.y, gradient);
    float blurred = texture2D(Halo, uv).r;
    float halo = pow(clamp(blurred * 1.8, 0.0, 1.0), Falloff);
    // Preserve the original item and phone screen, including translucent item pixels.
    halo *= 1.0 - step(.001, coverage);
    float inner = 0.0;
    if (InnerGlow > 0.0 && coverage > .001) {
        inner = coverage;
        if (InnerMode == 1) inner *= .15 + .85 * smoothstep(.2, .95, blurred);
        else if (InnerMode == 2) inner *= 1.0 - smoothstep(.3, .95, blurred);
        else if (InnerMode == 3) inner *= .25 + .75 * (.5 + .5 * sin(InnerPhase * 6.2831853));
        else if (InnerMode == 4) {
            float band = abs(fract(uv.y + InnerPhase) - .5);
            inner *= 1.0 - smoothstep(0.0, max(.001, InnerWidth), band);
        }
    }
    vec3 light = tint * (halo + inner * InnerGlow) * strength;
    // Screen blending adds light to the existing scene without copying or sampling its color.
    gl_FragColor = vec4(clamp(light, 0.0, 1.0), 0.0);
}
