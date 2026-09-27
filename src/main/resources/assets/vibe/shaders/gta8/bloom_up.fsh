uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uRadius;
varying vec2 vUV;

void main() {
    // 9-tap tent filter, added onto the next larger mip with additive blending.
    vec2 o = uTexel * uRadius;
    vec3 c = texture2D(uTex, vUV).rgb * 4.0;
    c += (texture2D(uTex, vUV + vec2(-o.x, 0.0)).rgb + texture2D(uTex, vUV + vec2(o.x, 0.0)).rgb + texture2D(uTex, vUV + vec2(0.0, -o.y)).rgb + texture2D(uTex, vUV + vec2(0.0, o.y)).rgb) * 2.0;
    c += texture2D(uTex, vUV + vec2(-o.x, -o.y)).rgb + texture2D(uTex, vUV + vec2(o.x, -o.y)).rgb + texture2D(uTex, vUV + vec2(-o.x, o.y)).rgb + texture2D(uTex, vUV + o).rgb;
    gl_FragColor = vec4(c / 16.0, 1.0);
}
