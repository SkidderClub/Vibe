# HitMarker Particle mode

Select Particle in Hitmarker's Modes multiselect. It can run alongside the crosshair, world X, Torus and Hologram effects.

Each hit emits a small burst at the stored attack-ray contact. Particles scatter, fall, bounce, slide and settle using loaded Minecraft block collision shapes, including partial blocks. They are visual objects only: no game entities or packets are created. Removing their supporting block lets them fall again.

Settings include burst count and delay, maximum active particles, lifetime, size, spread, upward impulse, gravity, bounce, ground friction and air drag. Static, lifetime Fade and Rainbow colors are available, with color alpha, optional glow, glow radius/strength, end-of-life fade and easing. Through Walls defaults off, so the world occludes both particle cores and glow.

Physics runs on client ticks and rendering interpolates between positions. Small variations in size and lifetime prevent identical-looking bursts. Unloaded terrain and blocks placed around particles discard those particles without loading chunks. Changing worlds, disabling Hitmarker or deselecting Particle clears its state.

Rendering uses one cached radial sprite and at most two batches. The default limit is 256 particles; the configurable maximum is 512. No scene blur or additional framebuffer is needed.

Verification: `test --tests "dev.vibe.hitmarker.*" --tests "dev.vibe.combat.HitmarkerDelayTest"` and `verifyHitParticles`.
