# Basic Teleport

Basic Teleport is a one-shot action in Movement. Press G to teleport onto the solid block in view. Rebind it through ClickGUI, Keybind Editor or `.bind BasicTeleport <key>`. The module returns to its off state after each action, so the same key works repeatedly. Holding the key does not repeatedly teleport.

Range defaults to 64 blocks. Landing uses native collision boxes, including slab heights, requires player headroom and loaded terrain, and rejects fire, lava and cactus. The player keeps their viewing direction. Motion and accumulated fall distance are reset; vanilla's next movement update reports the new position. Multiplayer servers remain authoritative and may correct the move.

The supplied `portal.webp` is bundled verbatim in `assets/vibe/teleport/`. It is a packed static atlas. Its circular layers animate with aperture opening, counter-rotation, glow and closing at the source and destination. Duration, size and glow are adjustable; Portal Animation can be disabled. One cached atlas texture is reused. Effects expire and clear on world changes or disconnects.

Validation: `BasicTeleportTest`, `verifyTeleportPortal`, `verifyFreecamProduction`.
