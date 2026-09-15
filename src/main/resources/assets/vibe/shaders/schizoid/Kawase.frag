/*
 * Copyright (c) 2023. Schizoid
 * All rights reserved.
 */

#version 120
// SPDX-License-Identifier: AGPL-3.0-only
// Modified for Vibe on 2026-09-09; see LICENSES/SCHIZOID.md.


varying vec2 uv;

uniform sampler2D Texture;
uniform vec2 TexelSize;
uniform bool Alpha;

uniform float Size;

void main() {
    gl_FragColor = vec4(0.0);
    vec2 offset = (TexelSize * Size) + TexelSize / 2.0;
    gl_FragColor += texture2D(Texture, uv + offset);
    gl_FragColor += texture2D(Texture, uv - offset);
    gl_FragColor += texture2D(Texture, uv + vec2(offset.x, -offset.y));
    gl_FragColor += texture2D(Texture, uv + vec2(-offset.x, offset.y));
    gl_FragColor *= 0.25;
    if (!Alpha) {
        gl_FragColor.a = 1.0;
    }
}
