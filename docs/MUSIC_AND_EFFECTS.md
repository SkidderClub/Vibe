# Music and visual effects

## Fog

Enable **Visual → Fog**. Start/End Distance use world units. The end is always
at least 0.1 beyond the start, including when an old profile reverses the sliders.
Choose Kawase or Gaussian blur, strength, overall opacity and Scene/Custom/Rainbow
color. Rainbow adds Schizoid's animated noise tint with opacity, saturation,
brightness and speed controls. Affect Sky includes/excludes the sky. Keep Vanilla
Fog retains the game's normal fog beneath the effect.

The same settings affect Minecraft and GTA7. Fog runs before the hand/weapon and
HUD, using the active view's depth buffer. OpenGL 2.0 shaders and framebuffer
support are required; unsupported hardware keeps the normal rendering path.
Third-party shader packs that replace the depth pipeline need live compatibility
testing; standard Forge rendering and GTA7's own framebuffer are supported.

## Hitmarker → Torus

Select **Torus** in Hitmarker → Modes, alongside 2D and/or 3D if desired. Each
local hit creates Schizoid's reflective ring at the saved hit position, facing
the impact view. The ring expands and its tube thins with cubic easing. Lifetime,
noise, scale and depth testing have separate controls. Torus does not extend the
duration of the existing 2D/3D markers.

## CustomCrosshair in GTA7

Enable CustomCrosshair to replace the minigame's default crosshair, including when
aiming. Styles, size, colors, rainbow, rotation, offsets and dynamic movement gap
use the same settings as Minecraft. GTA7's own movement drives the dynamic gap.
Minecraft's third-person view setting does not hide the GTA7 crosshair. The block
breaking indicator applies to Minecraft blocks only.

## Music

Enable **Client → Music**. **Media HUD** controls the draggable card independently
of the general HUD module. Press H to position it; middle-click it for layout
settings. All colors and media/radio/wave options are in the Music module.
The card also appears in GTA7. The Schizoid-inspired layout uses smooth text,
rounded album artwork and an optional blurred cover-color background. Long
title/artist text scrolls, or ends with an ellipsis when scrolling is disabled.
The compact timeline shows elapsed/duration and playback state; live radio and
connection messages use actual media snapshots. Missing artwork uses a smooth,
code-drawn vinyl fallback. Existing width, scale and color settings still apply.

**System Media** uses Windows 10/11 media sessions (SMTC), including supported
Spotify and browser playback. Player Priority is a comma-separated owner list,
searched in order. Players must publish a Windows media session; browser tabs
without a Media Session are not discoverable this way. No account login or
access token is requested. Waiting/unsupported/failure states appear in the HUD.

**Radio** switches to a selectable station: Groove Salad, Drone Zone, Secret Agent
or Custom. Custom accepts an HTTP(S) MP3 stream, M3U or PLS URL. AAC, HLS and web
player pages are unsupported. Radio Volume changes decoded audio gain; Radio
Reconnect retries interrupted streams with a bounded delay. ICY titles are shown
when the station supplies them. Station changes and disabling Radio/Music cancel
the previous stream. Streams are not saved to disk.

**Volume Display** draws an audio-reactive spectrum at the bottom of the screen.
Audio Source Auto selects Radio while it is enabled, otherwise System; either can
also be selected explicitly. System captures the default Windows playback device
using WASAPI loopback, including other applications. It does not use a microphone.
Radio visualization works independently of Windows media-session support.

Customize Waves/Bars/Line, detail, height, width, bottom offset, sensitivity,
smoothing, frequency range, opacity, line width, bar gaps, layered waves, mirrored
spectrum, falling peak markers, solid/gradient/rainbow colors and rainbow speed.
The FFT consumes real PCM, and silence decays to zero. The HUD editor shows the
current audio-source status. Windows helpers run hidden and stop with Music/the
client; changing the default output device reconnects loopback capture.

## HUD LiquidGlass

Select **HUD -> Mode -> LiquidGlass** for a clear surface with rounded, polished
edges. Refraction follows the curved rim; the center stays still and preserves
the scene's colors. All dimensions scale with Minecraft's GUI scale.

**Glass Refraction** controls edge distortion. **Glass Blur** and **Glass Blur
Strength** soften the scene independently; disable blur for the clearest glass.
**Glass Opacity** controls the effect's strength and **Glass Tint** adds a subtle
color. The scoreboard uses the same rounded surface without a second rectangular
blur pass. If the shader is unavailable, Vibe logs the error and uses a rounded
translucent fallback.

`build.bat verifyClientChanges --args=liquid-glass` checks the real OpenGL shader,
including transparency, refraction, blur, sampling and rounded corners at GUI
scales 1 and 2. The preview is `build/client-changes-check/liquid-glass-preview.png`.

## Source and validation

`.source` identifies the matching source archive generated by `build`, the project
repository and the combined license terms. [Port provenance](../LICENSES/SCHIZOID.md)
lists every adapted Schizoid file and shader dependency.
The main and pause menus also offer **Licenses & credits**, with Schizoid attribution,
the complete license texts and source instructions available offline.

`build.bat test verifyVisualEffects verifyGta7Rendering` runs unit and offscreen
OpenGL checks. The visual-effects fixture writes images to
`build/visual-effects-check/`; GTA7 checks write to `build/gta7-render-check/`.
`build.bat verifyMusicIntegration` optionally connects to a real MP3 station,
decodes into a muted output line and verifies cancellation. It does not change
the system volume. The ordinary `test` task does not contact radio servers.
