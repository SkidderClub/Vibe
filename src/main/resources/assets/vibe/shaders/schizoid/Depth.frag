/*
 * Copyright (c) 2023. Schizoid
 * All rights reserved.
 */

#version 120
// SPDX-License-Identifier: AGPL-3.0-only
// Modified for Vibe on 2026-09-09; see LICENSES/SCHIZOID.md.

#include "Depth.glsl"

varying vec2 uv;

uniform sampler2D Tex0;
uniform sampler2D Tex1;
uniform float Near;
uniform float Far;
uniform float MinThreshold;
uniform float MaxThreshold;
uniform float Opacity;
uniform bool AffectSky;

void main() {
    // Read in depth value from depth texture
    float depth = texture2D(Tex1, uv).x;

    // Convert depth value to distance
    float distance = linearizeDepth(depth, Near, Far) / Far;

    gl_FragColor = vec4(0.0);
    if ((AffectSky || depth < 0.999999) && distance > MinThreshold) {
        gl_FragColor = clamp(vec4(texture2D(Tex0, uv).rgb, (distance - MinThreshold) / (max(0.000001, MaxThreshold - MinThreshold))), 0.0, 1.0);
        gl_FragColor.a *= Opacity;
    }
}
