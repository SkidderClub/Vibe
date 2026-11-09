# Teleport

Teleport is a one-shot action in Movement. Its Mode selector currently offers Vanilla. Press G to teleport onto the solid block in view. Rebind it through ClickGUI, Keybind Editor or `.bind Teleport <key>`. The module returns to its off state after each action, so the same key works repeatedly. Holding the key does not repeatedly teleport. Older Basic Teleport profile entries migrate with their keybind and settings.

Range defaults to 64 blocks. A separate block ray trace is independent of Minecraft's interaction reach. Landing uses native collision boxes, including slab heights, requires player headroom and loaded terrain, and rejects fire, lava and cactus. The player keeps their viewing direction. Motion and accumulated fall distance are reset. Vanilla position packets subdivide the move into steps of at most four blocks and explicitly report the destination. Multiplayer servers remain authoritative and may correct the move, especially when the route crosses solid blocks.

The supplied `portal.webp` is bundled verbatim in `assets/vibe/teleport/`. It is a packed static atlas. Its circular layers animate with aperture opening, counter-rotation, glow and closing at the source and destination. Duration, size and glow are adjustable; Portal Animation can be disabled. One cached atlas texture is reused. Effects expire and clear on world changes or disconnects.

Validation: `BasicTeleportTest`, `verifyTeleportPortal`, `verifyFreecamProduction`.

Reference checked: [LiquidBounce Teleport](https://github.com/CCBlueX/LiquidBounce/blob/nextgen/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/ModuleTeleport.kt). Vibe retains its own block-key action and Minecraft 1.8.9 position packets; no server-specific exploit mode is included.
