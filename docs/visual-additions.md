# KillEffects, MotionBlur and Keystrokes

KillEffects and MotionBlur are in Visual. Keystrokes is an optional element in HUD Editor.

KillEffects offers Shattered Glass, Dissolving Silhouette, Shockwave, Holy and Helicopter. It observes a player's death once and freezes their skin and pose independently of the original entity. Holy lifts an upright player with raised arms, optional wings, halo and a light beam. Helicopter approaches, lowers a rope, lifts the player and flies away. Both are local visual meshes.

The default Only Your Kills filter requires a recent local attack within the Hit Window. This is client-side attribution; it cannot read a server's authoritative kill credit. Disappearing or unloading a player does not trigger the effect. Range, simultaneous effects and duration are bounded. Mode-specific controls cover shards and gravity, dissolution, wave size/count, Holy decorations and helicopter lift, scale, rotor speed and rope length.

MotionBlur reuses one history texture and composites before the HUD. Strength is independent of FPS. Zero strength bypasses it. Optional Only While Turning limits the effect to camera turns. Menus, camera cuts, world changes and resizing clear its history. The HUD remains sharp.

Keystrokes always shows Minecraft's current movement keybinds. Left Click, Right Click and Space are separate toggles; disabled rows collapse automatically. Mouse tiles reflect physical mouse buttons; movement/jump tiles follow the user's bindings. Vibe, Skeet, LiquidGlass and Lavalamp themes share the HUD's scale, position and color controls, with extra key size, spacing, pressed colors and transition speed settings.

Validation: `test --tests dev.vibe.hud.VisualAdditionsTest`, `verifyVisualAdditions`, `verifyHudEditorRendering`, `verifyCameraHologramRendering`, and `verifyFreecamProduction`.
