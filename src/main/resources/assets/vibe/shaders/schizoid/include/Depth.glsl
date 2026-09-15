// Schizoid Depth.glsl, AGPL-3.0-only. Modified 2026-09-09: OpenGL depth to NDC.
// SPDX-License-Identifier: AGPL-3.0-only
// SPDX notice added 2026-09-11; see LICENSES/SCHIZOID.md.
#define linearizeDepth(depth, near, far) ((2.0 * near * far) / (far + near - (depth * 2.0 - 1.0) * (far - near)))
