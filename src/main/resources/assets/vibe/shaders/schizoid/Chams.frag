#version 120
uniform sampler2D Skin;
uniform vec3 Tint;
uniform float Opacity;
uniform int ShowSkin;
uniform int Material;
varying vec2 SkinUv;
varying vec3 EyePosition;
varying vec3 EyeNormal;

void main() {
    vec4 skin = texture2D(Skin, SkinUv);
    // Preserve transparent hat/jacket/armor cutouts even on an untextured material.
    if (skin.a < 0.1) discard;
    vec3 base = Tint * (ShowSkin != 0 ? skin.rgb : vec3(1.0));
    vec3 normal = normalize(EyeNormal);
    vec3 view = normalize(-EyePosition + vec3(0.0, 0.0, 0.0001));
    float facing = clamp(abs(dot(normal, view)), 0.0, 1.0);
    vec3 rgb = base;
    if (Material == 1) {
        // Emissive center and a bright Fresnel rim, independent of world lighting.
        float rim = pow(1.0 - facing, 2.0);
        rgb = base * (0.62 + 0.65 * rim) + vec3(0.32) * rim;
    } else if (Material == 2) {
        // Procedural studio reflections remain stable while the model/camera rotates.
        vec3 reflected = reflect(-view, normal);
        float band = pow(0.5 + 0.5 * sin(reflected.y * 14.0 + reflected.x * 5.0), 10.0);
        float highlight = pow(max(dot(reflected, normalize(vec3(-0.35, 0.7, 0.65))), 0.0), 40.0);
        float fresnel = pow(1.0 - facing, 4.0);
        rgb = base * (0.3 + 0.55 * max(normal.y, 0.0) + 0.55 * band)
                + vec3(0.85, 0.91, 1.0) * (0.65 * highlight + 0.23 * band + 0.28 * fresnel);
    }
    // Every material uses source-over alpha. Glow/metal highlights never replace it.
    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), Opacity * skin.a);
}
