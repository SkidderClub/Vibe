/*
 * Copyright (c) 2023. Schizoid
 * All rights reserved.
 */

#version 120
// SPDX-License-Identifier: AGPL-3.0-only
// Modified for Vibe on 2026-09-09; see LICENSES/SCHIZOID.md.


varying vec2 uv;

uniform sampler2D Tex0;
uniform vec3 Color;
uniform bool RGBPuke;
uniform vec2 SV;
uniform float Opacity;
uniform bool Alpha;
uniform float Multiplier;
uniform float Time;
uniform float Yaw;
uniform float Pitch;

#include "Hsv.glsl"
#include "3DSimplexNoise.glsl"
#include "RGBPukeNoise.glsl"

void main() {
    gl_FragColor = texture2D(Tex0, uv);
    if (RGBPuke) {
        float time = Time / 8.0;
        float d = rgbPuke(uv, Yaw, Pitch, time);
        gl_FragColor.rgb = mix(gl_FragColor.rgb, hsv2rgb(vec3(mod(d * 0.5 - time, 1.0), SV)), Opacity);
    } else {
        gl_FragColor.rgb = mix(gl_FragColor.rgb, Color, Opacity);
    }
    if (Alpha) {
        gl_FragColor.a = clamp(gl_FragColor.a * Multiplier, 0.0, 1.0);
    } else {
        gl_FragColor.a = 1.0;
    }
}
