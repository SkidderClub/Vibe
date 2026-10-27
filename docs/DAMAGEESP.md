# DamageESP

Visual module displaying floating numbers when a nearby living entity's observed health changes. Players and mobs are selected initially; Self is optional. New entities establish a baseline without producing a number. Damage is measured from health updates, including absorption if enabled. Hurt animations and attacks do not fabricate amounts. Servers that do not report a target's health cannot provide accurate damage numbers.

Appearance controls include:

- Smooth, Bold, Monospace, Serif and Minecraft fonts; scale and optional distance scaling.
- Custom damage/healing colors, an age-based two-color gradient or animated rainbow.
- Colored glow with radius and strength, adjustable outline and optional shadow.
- Health or heart units, decimal precision and signs; optional healing numbers.
- Lifetime, fade-in, Smooth/Linear/Late/None fading and pop-in animation.
- Upward speed with Linear, Cubic Out or Exponential Out easing; horizontal spread, drift and height offset.
- Entity filters, range, maximum number count and Through Walls.

The non-linear rise modes gradually reduce upward velocity. Numbers face the actual view matrix, including Freecam roll. Depth testing is enabled by default. The renderer caches font atlases, limits live markers and uses glyph offsets for glow instead of allocating per-number framebuffers. Tracking runs once per client tick; animation uses elapsed time. World changes and disabling clear the history.

Verification: `gradlew.bat build verifyDamageEspRendering --offline`. Graphics captures are written to `build/damage-esp-render-check`.
