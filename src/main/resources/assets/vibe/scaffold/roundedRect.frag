#version 120

uniform vec2 location, rectSize;
uniform vec4 color;
uniform float radius;
uniform bool blur;

float roundSDF(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b, 0.0)) - r;
}

void main() {
    vec2 rectHalf = rectSize * 0.5;
    vec2 fragPos = gl_TexCoord[0].st * rectSize;
    float dist = roundSDF(rectHalf - fragPos, rectHalf - radius - 1.0, radius);

    float smoothedAlpha = (1.0 - smoothstep(0.0, 1.0, dist)) * color.a;

    if (smoothedAlpha < 0.01) discard;

    gl_FragColor = vec4(color.rgb, smoothedAlpha);
}
