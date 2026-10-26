# ItemGlow

Enable **Visual → ItemGlow** for a soft halo around first-person held items and
the Phone offhand. Both sources can be selected independently. Empty Hand is
optional and off by default. The Phone's original skin hand, tilt, screen, apps
and camera remain in use.

- **Color Mode:** Static, Gradient or Rainbow; RGBA colors and animation speed.
- **Radius, Intensity, Opacity, Falloff:** halo size, strength and soft edge decay.
- **Fade:** a smooth breathing effect; Fade Speed and Minimum Glow set its cycle.
- **Inner Glow:** strength of the effect inside the object. Zero preserves the
  original item colors and Phone screen, including when an inner mode is selected.
- **Inner Mode:** Tint fills the silhouette; Soft fades toward its edge; Rim
  lights its inner boundary; Pulse breathes independently of the outer halo;
  Scan moves a soft highlight across the object. All five work on held items and
  the Phone. Inner Speed controls Pulse and Scan; Inner Width controls Scan.
  Existing profiles keep their original Tint appearance.
- **Quality:** Performance, Balanced or High. Balanced is the default.

Settings save through the normal module/profile system. Ordinary item animations
and blocking transforms are applied before glow capture.

The renderer draws each source once into Minecraft's real color/depth attachments
and a separate coverage attachment simultaneously. Two smaller recycled textures
blur only that coverage. No scene-color copies, CPU pixel readbacks, per-frame GPU
allocations, world re-rendering or screen-wide scene blur are used. The original
depth renderbuffer is shared, so normal held-item depth writes are preserved.
Translucent item colors blend directly against the real scene. The HUD is excluded
from the glow mask. This uses the compatibility pipeline's draw-buffer broadcast
specified in [OpenGL 2.0](https://registry.khronos.org/OpenGL/specs/gl/glspec20.pdf).
The Phone reuses its existing supersampled canvas texture as the coverage source,
then draws only a transformed halo around that canvas. It needs no separate capture,
scene copy, depth target or full-screen pass. Wide radii adapt the blur resolution to keep
filter taps close together without increasing the shader's sampling cost.
The inner modes reuse that coverage and blur in the existing composite shader;
they add no geometry draws, render targets, or blur passes. Their light stays
inside the source's alpha coverage and leaves the world and HUD untouched.
Held-item and small Phone buffers persist across frames and are freed on disable,
disconnect, resize or quality changes. Incompatible external framebuffer passes
retain ordinary rendering. Unsupported hardware uses the normal renderer.

`gradlew test verifyItemGlow verifyPhoneRendering` checks palette/fade settings,
both render hooks, the actual Phone and skin, original colors/translucency/depth,
camera/Flappy compatibility, source toggles, buffer reuse, resizing and cleanup.
Screenshots and a local synchronized GPU timing comparison are written to
`build/item-glow-check/`. Timings describe that test scene, not an in-game FPS guarantee.
