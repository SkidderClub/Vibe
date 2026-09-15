/* Copyright (c) 2023. Schizoid. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * Reflection_VP.glsl port: GLSL 1.20 built-ins, modified 2026-09-09.
 */
#version 120
varying vec3 Normal;
varying vec3 FragPos;
void main() {
    FragPos = vec3(gl_ModelViewMatrix * gl_Vertex);
    Normal = gl_NormalMatrix * gl_Normal;
    gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
}
