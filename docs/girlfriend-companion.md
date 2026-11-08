# Girlfriend companion

Girlfriend remains in Meme. The adult companion has a smaller head, shaped hairline, an opaque outer scalp, a detailed 128-pixel skin, layered hair and a hair clip. A modest clothed bust is part of each outfit. Outfit defaults to Goth: a dark fitted corset-style top, cropped jacket, choker, chain-trimmed pleated skirt, fingerless gloves and laced tall boots. Chic offers the colored cropped jacket/top and skirt. Casual keeps the hoodie, jeans and sneakers. Hair, outfit, accent and eye colors are independent settings. The native walk cycle also animates the hair; idle blinking and a speaking animation add small facial movements.

Follow Distance defaults to two blocks. Stay Within defaults to six blocks. Follow Speed and Model Scale are configurable. The controller follows behind the player's direction of travel; turning the camera while standing still leaves her position unchanged. It routes around loaded block shapes, steps or jumps over obstacles and floats in water. It repositions in nearby safe terrain after a teleport or if it cannot get unstuck. Creative flight keeps her nearby in clear air.

The companion is render-only and is absent from Minecraft's world entity lists. It sends no movement packets and cannot become a combat target, push players, trigger blocks or change the world.

All 19 existing reactions are available as mono Vorbis clips in seven native sound events. Minecraft owns positional playback, distance attenuation, stereo direction, game volume categories and pausing. Each voice follows the companion's mouth; clips do not overlap and stop on disable, disconnect or world change. The old JavaSound MP3 playback is no longer used.

Original MP3s are retained. `tools/convert-girlfriend-audio.ps1` reproduces the mono Ogg derivatives using a local libsndfile library; this library is not needed to run Vibe.

Validation: `CompanionFollowerTest`, `GirlfriendVoiceTest`, `verifyCompanion`, `verifyFreecamProduction`.
