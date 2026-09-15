# Schizoid component provenance

Upstream: https://github.com/SchizoidDevelopment/schizoid

Pinned revision: `ea2a0acc78cf1e6868accc30e089d65bc918f140` (master retrieved 2026-09-09).
Upstream copyright notices identify **Schizoid**, 2023 for shaders and 2024 for the
Kotlin modules. These notices are retained in the ports. The repository license
at this revision is GNU Affero General Public License version 3, reproduced in
[`LICENSES/AGPL-3.0.txt`](AGPL-3.0.txt).

The functional adaptations were made for Vibe on **2026-09-09**. License comments
and document organization were clarified on **2026-09-11**. The original
Kotlin code targets a newer Minecraft/Fabric/OpenGL API; these are source ports,
not unmodified Kotlin classes loaded into Forge.

The Media HUD received a further visual adaptation on **2026-09-12**: smooth
text using Vibe's existing system-font renderer, antialiased album corners and
code-drawn vinyl, a cached blurred-color surface, and compact playback/timeline
details. The Schizoid copyright and AGPL-3.0-only notice remain in the renderer.

Paths below are relative to upstream `src/main/` and Vibe `src/main/` respectively.

| Upstream source | Vibe destination | Changes |
| --- | --- | --- |
| `kotlin/dev/lyzev/schizoid/feature/features/module/modules/render/ModuleToggleableBlur.kt`, fog settings and `EventRenderWorld` pass | `java/dev/vibe/module/impl/FogModule.java`, `java/dev/vibe/ui/effect/FogRenderer.java` | Split into a Fog module; Java 8, Forge events, raw LWJGL 2; depth/color copied from the currently bound framebuffer; reusable targets; distance in world units, custom color, sky exclusion and configurable opacity; also called by GTA7 before its weapon pass. |
| `kotlin/dev/lyzev/schizoid/feature/features/module/modules/render/ModuleToggleableTorus.kt` | `java/dev/vibe/ui/effect/TorusRenderer.java` | Complete 10-by-30 torus mesh and normals, cubic growth/thinning, noise-driven reflection; Java/LWJGL port; integrates with Vibe's local hit notifications and saved impact direction; independent lifetime, depth and scale; copied scene texture prevents framebuffer feedback. |
| `kotlin/dev/lyzev/schizoid/feature/features/module/modules/render/ModuleToggleableMediaPlayer.kt` | `java/dev/vibe/ui/MusicHudRenderer.java` | Album tile, blurred album background, title/artist scrolling, timeline and playback indicator adapted to Vibe's smooth font renderer and HUD editor. Antialiased, cached artwork surface; center-cropped covers; compact time/status row; nested marquee clipping. Configurable dimensions/colors. Vibe's own SMTC/radio service replaces the upstream media library. Fallback vinyl is drawn in code; upstream `vinyl.png` and fonts are not bundled. |
| `resources/assets/schizoid/shaders/core/Depth/Depth_FP.glsl` | `resources/assets/vibe/shaders/schizoid/Depth.frag` | GLSL 1.20 syntax; sky exclusion, opacity and nonzero threshold range. |
| `resources/assets/schizoid/shaders/include/Depth.glsl` | `resources/assets/vibe/shaders/schizoid/include/Depth.glsl` | Corrected reconstruction to convert OpenGL window depth [0,1] to NDC [-1,1]. |
| `resources/assets/schizoid/shaders/core/Tint/Tint_FP.glsl` | `resources/assets/vibe/shaders/schizoid/Tint.frag` | GLSL 1.20 syntax; fixed RGBPukeNoise include, Vibe HSV conversion replaces the upstream StackExchange-derived Color.glsl. |
| `resources/assets/schizoid/shaders/include/RGBPukeNoise.glsl` | `resources/assets/vibe/shaders/schizoid/include/RGBPukeNoise.glsl` | Algorithm unmodified; provenance/SPDX comments added 2026-09-11. |
| `resources/assets/schizoid/shaders/core/Kawase/Kawase_FP.glsl` | `resources/assets/vibe/shaders/schizoid/Kawase.frag` | GLSL 1.20 syntax; initialize accumulated output to zero. |
| `resources/assets/schizoid/shaders/core/Gaussian/Gaussian_FP.glsl` | `resources/assets/vibe/shaders/schizoid/Gaussian.frag` | GLSL 1.20 syntax and explicit integer-to-float conversions; retains incremental Gaussian weights and linear-sampling path. |
| `resources/assets/schizoid/shaders/core/Reflection/Reflection_FP.glsl` | `resources/assets/vibe/shaders/schizoid/Reflection.frag` | GLSL 1.20 syntax; retains complete cube-map projection and noise/reflection algorithm; camera uniform is the view-space origin. |
| `resources/assets/schizoid/shaders/core/Reflection/Reflection_VP.glsl` | `resources/assets/vibe/shaders/schizoid/Reflection.vert` | Fixed-function vertex/normal/matrix inputs and GLSL 1.20 built-in normal matrix. |
| `resources/assets/schizoid/shaders/include/3DSimplexNoise.glsl` | `resources/assets/vibe/shaders/schizoid/include/3DSimplexNoise.glsl` | Algorithm unmodified; upstream credits Ian McEwan and Stefan Gustavson, webgl-noise. MIT/SPDX notice comments added 2026-09-11; complete MIT license bundled separately. |

All Schizoid-derived files above remain under AGPL-3.0-only, except the separately
licensed MIT noise implementation. `EffectProgram`, `EffectState`, `SceneTexture`,
`fullscreen.vert`, `Hsv.glsl`, `MusicModule`, `MusicVisualizer` and `dev/vibe/media/`
are new Vibe code under Vibe's GPLv3 license. Existing integration files remain
Vibe code; references to these ported components do not remove their AGPL terms.
No other Schizoid shaders are needed by these selected effects. Unrelated compute
shaders, blur methods, fonts and artwork are not included.

## Building and distributing this combination

Vibe combines GPLv3 and AGPLv3 components under section 13 of those licenses. The
AGPL network-interaction provisions apply to the combination when their conditions
are met. The top-level GPL
license is not a replacement for the Schizoid components' AGPL license. This port
does not add an exception for linking with Minecraft or other proprietary code.
The unresolved linking and distribution questions are recorded under
[Open distribution issues](THIRD_PARTY_NOTICES.md#open-distribution-issues).

`build` produces `build/libs/Vibe-1.8.9-<version>-sources.zip` alongside the JAR.
It contains this checkout's sources, assets, tests, Gradle wrapper, build scripts,
license texts and documentation. Distribute the matching source archive next to
the JAR with free access; a link to upstream Schizoid alone is insufficient.
The archive includes the current working changes, so it also represents a local
build that has not been committed or published yet. Public build dependencies are
declared in the included Gradle files. The generated `LICENSES/dependencies/INDEX.md`
records the actual bundled JARs and preserves their embedded notices alongside it.
Their source archives are not automatically included, so their own source/notice
requirements need a separate check. The
client `.source` command identifies the
archive and the project repository; that repository may differ from an unpublished
local build. If a modified version supports remote user interaction, provide a
prominent source offer to those users as required by AGPL section 13. The local
command does not by itself provide an offer visible to remote users.

The source archive and these notices document this port. They do not resolve the
pre-existing license/provenance gaps recorded elsewhere in THIRD_PARTY_NOTICES.md.

## Media implementation references

- [Windows SMTC session manager](https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssessionmanager): OS title, artist, thumbnail, playback state and timeline. No Spotify API credentials are needed.
- [Windows WASAPI loopback](https://learn.microsoft.com/en-us/windows/win32/coreaudio/loopback-recording): samples the default output device, never microphone input. Device changes are detected and reopened.
- [SomaFM official listen links](https://somafm.com/listen/): MP3 PLS presets for Groove Salad, Drone Zone and Secret Agent. Audio is streamed only when Radio is enabled and is not bundled or recorded to disk.
- [webgl-noise](https://github.com/stegu/webgl-noise): the separately licensed noise include, with [MIT notice](webgl-noise-MIT.txt).
