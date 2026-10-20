uniform sampler2D uFoliage;
varying vec2 vUV;
varying float vFoliage;
varying float vParam;

void main() {
    if (vFoliage > 1.5) {
        // Chain-link fences cast a sparse, dappled shadow.
        vec2 d = vec2(vUV.x + vUV.y, vUV.x - vUV.y) / .06;
        vec2 f = abs(fract(d) - .5);
        if (max(f.x, f.y) < .36 && fract(vUV.x / 3.0) > .01 && abs(vUV.y - 2.15) > .03) discard;
    } else if (vFoliage > .5) {
        vec2 cellUv = (vec2(mod(vParam, 2.0), floor(vParam / 2.0)) + clamp(vUV, .004, .996)) * .5;
        if (texture2D(uFoliage, cellUv).a < .5) discard;
    }
    gl_FragColor = vec4(1.0);
}
