# Companion hits, Dab and HUD glow

The duplicate project-root `portal.webp` has been removed after confirming it matched the copy already bundled at `src/main/resources/assets/vibe/teleport/portal.webp`. Teleport continues to load that bundled asset.

Girlfriend can be hit through the normal attack key. A local ray check respects vanilla combat reach, closer block/entity hits, Freecam and open screens. Minecraft's native knockback calculation supplies the initial velocity, including sprint and weapon knockback. Collision, gravity, friction and a short hit cooldown apply locally. Following pauses through the knockback arc and resumes after landing, with a recovery grace period to avoid snapping back at the normal follow radius. The companion remains outside server/world entity lists; these clicks send no entity-attack or block-dig packets.

Dab is in Meme and has no settings. It listens to Vibe's existing kill activity and runs a forty-tick celebration with smooth entry and exit. Only the local player's third-person world model is posed. Native regular/slim models, outer clothing and armour follow the pose. First-person hands and inventory previews retain their native pose. Disable, death, respawn and world changes reset the animation.

Effects timer bars, Block Counter lines/circles, Custom Health bars and Target HUD health/absorption bars have Bar Glow enabled by default. The glow applies to LiquidGlass and Lavalamp styles and follows the fill colour, opacity and remaining fraction. Each supported element has a glow strength control. Small additive local meshes provide the halo without a fullscreen blur or bloom pass, and preserve clipping and alpha state.

Validation: `CompanionFollowerTest`, `CompanionHitTest`, `DabModuleTest`, `MemeAnimationTransformerTest`, `verifyMemeAdditions`, `verifyHudEditorRendering`, and `verifyFreecamProduction`.
