// SPDX-License-Identifier: AGPL-3.0-only
// From Schizoid ea2a0acc78cf1e6868accc30e089d65bc918f140; see LICENSES/SCHIZOID.md.
// Notice added for Vibe on 2026-09-11; algorithm unchanged.
float rgbPuke(vec2 uv, float yaw, float pitch, float time) {
    vec2 pos = vec2(uv.x + yaw / 180.0, uv.y - pitch / 90.0);
    return snoise(vec3(pos, time));
}
