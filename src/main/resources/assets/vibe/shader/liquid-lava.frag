// Vibe's theme-colored, softly lit metaballs. No ray marching or framebuffer history.
uniform float time;
uniform vec2 resolution;
uniform vec3 themeAccent;
uniform vec3 themeBackground;

float merge(float a, float b) {
    float h = clamp(0.5 + 0.5 * (b - a) / 0.19, 0.0, 1.0);
    return mix(b, a, h) - 0.19 * h * (1.0 - h);
}
float drop(vec2 p, vec2 center, vec2 stretch, float radius) {
    return (length((p - center) / stretch) - radius) * min(stretch.x, stretch.y);
}
void main() {
    vec2 p = (gl_FragCoord.xy - 0.5 * resolution) / resolution.y;
    float aspect = resolution.x / resolution.y;
    float t = time * 0.27;
    float d = drop(p, vec2((-0.35 + 0.11 * sin(t * 0.73)) * aspect, -0.14 + 0.35 * sin(t * 0.43)), vec2(0.88, 1.32), 0.24);
    d = merge(d, drop(p, vec2((0.34 + 0.09 * cos(t * 0.61)) * aspect, 0.12 + 0.34 * cos(t * 0.49)), vec2(1.16, 0.92), 0.28));
    d = merge(d, drop(p, vec2((-0.06 + 0.18 * sin(t * 0.41 + 1.6)) * aspect, 0.34 + 0.22 * sin(t * 0.67)), vec2(1.0, 1.25), 0.19));
    d = merge(d, drop(p, vec2((0.07 + 0.17 * cos(t * 0.57)) * aspect, -0.38 + 0.24 * cos(t * 0.53)), vec2(1.15, 0.94), 0.22));
    d = merge(d, drop(p, vec2(-0.48 * aspect, 0.35 + 0.24 * sin(t * 0.37 + 2.0)), vec2(0.8, 1.35), 0.19));
    d = merge(d, drop(p, vec2(0.48 * aspect, -0.38 + 0.30 * cos(t * 0.39 + 1.0)), vec2(0.84, 1.22), 0.18));
    float body = 1.0 - smoothstep(-0.025, 0.055, d);
    float bloom = exp(-max(d, 0.0) * 5.2);
    float rim = exp(-abs(d) * 28.0);
    float light = 0.5 + 0.5 * sin(p.y * 2.8 - p.x * 1.1 + t * 0.14);
    vec3 base = mix(vec3(0.018, 0.026, 0.048), themeBackground, 0.55);
    vec3 wax = mix(themeAccent * 0.34, themeAccent * 0.65 + vec3(0.075), light);
    vec3 color = base + themeAccent * bloom * 0.09;
    color = mix(color, wax, body * 0.88);
    color += mix(themeAccent, vec3(0.85), 0.22) * rim * 0.07;
    float mist = 0.5 + 0.5 * sin(p.x * 2.3 + p.y * 1.8 + sin(t * 0.25));
    color = mix(color, themeAccent * 0.17 + vec3(0.025), 0.18 + mist * 0.14);
    color *= 1.0 - 0.22 * smoothstep(0.18, 0.85, length(p / vec2(aspect, 1.0)));
    gl_FragColor = vec4(color, 1.0);
}
