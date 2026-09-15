/*
 * Copyright (c) 2023. Schizoid
 * All rights reserved.
 */

#version 120
// SPDX-License-Identifier: AGPL-3.0-only
// Modified for Vibe on 2026-09-09; see LICENSES/SCHIZOID.md.


varying vec2 uv;

uniform sampler2D Tex0;
uniform vec2 Direction;
uniform vec2 TexelSize;
uniform bool Alpha;

uniform vec3 Gaussian;// "Incremental Computation of the Gaussian" by Ken Turkowski
uniform int Support;// ceil(sigma * 3)
uniform bool LinearSampling;

void main() {
    vec3 gaussian = Gaussian;
    gl_FragColor = texture2D(Tex0, uv) * gaussian.x;
    float sum = gaussian.x;
    if (LinearSampling) {
        for (int i = 1; i <= Support; i += 2) {
            gaussian.xy *= gaussian.yz;
            float w1 = gaussian.x;
            gaussian.xy *= gaussian.yz;
            float w2 = gaussian.x;
            float w = w1 + w2;
            vec2 offset = TexelSize * Direction * ((float(i) * w1 + (float(i) + 1.0) * w2) / w);
            gl_FragColor += texture2D(Tex0, uv + offset) * w;
            gl_FragColor += texture2D(Tex0, uv - offset) * w;
            sum += w * 2.0;
        }
    } else {
        for (int i = 1; i <= Support; i++) {
            gaussian.xy *= gaussian.yz;
            vec2 offset = TexelSize * Direction * float(i);
            gl_FragColor += texture2D(Tex0, uv + offset) * gaussian.x;
            gl_FragColor += texture2D(Tex0, uv - offset) * gaussian.x;
            sum += gaussian.x * 2.0;
        }
    }
    gl_FragColor /= sum;
    if (!Alpha) {
        gl_FragColor.a = 1.0;
    }
}
