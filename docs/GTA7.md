# GTA 7

Enable **GTA7** in the Meme category to enter the local, standalone game. Northside is now surrounded by eight districts; the world is 540 by 540 metres. The pause menu includes a compass-oriented district map.

## Controls

Movement, sprint, crouch, aim, attack and weapon slots follow your Minecraft bindings. GTA7 also has these dedicated controls:

| Key | Action |
| --- | --- |
| Escape | Pause/resume; exit from the death screen |
| F1 | Toggle controls |
| R | Reload the AK; the Minecraft drop binding also works |
| Space / jump binding | Jump; hold while airborne to use purchased jetpack fuel |
| Space / jump binding near a ladder | Climb |
| Shift / crouch binding near a ladder | Descend |
| E in the Emoji Dome elevator | Next floor |
| Crouch + E | Previous floor |
| Ctrl + E | Top floor; crouch + Ctrl + E returns to the lobby |

The control hint appears for seven seconds after spawning and after 25 seconds without movement, looking, or other gameplay input. F1 keeps it visible when requested.

## Progression

Collect XP drops or bank remaining drops when you die. Buy upgrades and weapon skins from the death screen. Page arrows keep every item accessible at small GUI sizes.

| Upgrade | Effect per level | Limit |
| --- | --- | --- |
| Knife | +15 damage | Uncapped |
| AK47 | +8 damage | Uncapped |
| Health | +25 HP | Uncapped |
| Walk speed | +0.27 metres/second | 10 |
| Fast shooting | +10% of the base fire rate | 10 |
| Bullet penetration | +5 percentage points of wall penetration chance per shot | 10 |
| Max ammo | +5 magazine rounds | Uncapped |
| Jet pack | +1 second of flight fuel | Uncapped |

Jetpack fuel recharges while grounded. Fire rate reaches twice the base rate at level 10; wall penetration reaches a 50% chance. Penetration bypasses building walls, while ground and vehicles remain cover. Glass is transparent, breaks when shot, and is restored on respawn. Buildings have accessible upper floors, stairs and ladders. The Emoji Dome has 18 floors reached by elevator; enter through the ground-floor door at the rear, on its north side.

Gold Rush, Arctic, Neon Circuit and Crimson finishes can be bought separately for the knife and AK. Purchases equip the finish immediately; owned finishes can be equipped again without paying.

Attacking NPCs raises a five-star police search. Police reinforcements respond nearby. Hide from police for 28 seconds to lose the first star, then another star about every 12 seconds while unseen. Always-hostile factions remain hostile after the police stop searching. A full day/night cycle takes ten minutes of active play and pauses with the game.

## Districts

| Direction | District and inhabitants |
| --- | --- |
| Center | Northside, civilians, police and circulating traffic |
| South | Desert cowboy villages, responding sheriffs and hostile outlaw camps |
| North | Snow, igloo villages, Inuit residents and officers, friendly penguins and hostile polar bears |
| East | Goblin Hollow, wooden lodges with green roofs, skull banners, market stalls and hostile knife-wielding goblins with pointed ears and tusks |
| West | Magical forest, wizard cottages and village guards |
| Southeast | Imperial forest garrison with stormtroopers, officers, an Imperial droid, Vader and a TIE fighter |
| Northeast | A compact native recreation of Mirage's A/B sites, Mid, Palace, Apartments, Market and connecting lanes; hostile terrorists and counterterrorists who retaliate when attacked |
| Southwest | Candy land with armed cartoon children carrying AKs |
| Northwest | Emoji Dome, a single round building with the supplied glasses-and-grin emoji facade, plaza visitors and responding police |

These are procedural models in GTA7's existing low-poly style. Mirage is a simplified reconstruction, not a dimensionally exact import of the Counter-Strike map.

The Emoji Dome is approximately 70 metres wide and tall and 22 metres deep. Its gold body follows the emoji's outline, including the hair and glasses. The front uses the supplied 184-by-184 image, converted losslessly to `assets/vibe/textures/gta7/emoji-facade.png`; a pixel checksum test verifies that the decoded image is unchanged. The entrance and elevator are at the rear so the face remains unobstructed.

## Visual detail and stability

Buildings now include recessed window trim, striped shop awnings, gutters, roof equipment, bookshelves, curtains, ceiling lamps and office equipment. Districts have matching street furniture, market stalls, wagons, desert campfires, snowy pines, ice formations, mushrooms, crystals and low hills. Native repeating textures cover sand, snow, stone, wood and metal; no downloaded texture pack is required.

Sunlight changes the geometry's illumination throughout the day, with warm dusk colours and cooler nights. Nearby lamps illuminate surfaces, with visible light sources, ground pools, animated fire and forest fireflies. Glass has subtle reflections and shatters into particles; bullet impacts also produce brief particles. The HUD includes a local radar with hostile NPCs and XP, and a reload progress bar. The pause atlas retains the overview of all nine districts.

Meshes load when visible, belong to one spatial group each, and use bounds that include wide structures and upper floors. Navigation reuses its search storage and schedules at most two bounded route searches per frame. Particles are capped and expire; excess loose XP is automatically banked so lengthy sessions do not accumulate an unlimited drop list. Collision checks include the hills and large new props, fast jumps stop at ceilings, elevated NPCs no longer stop cars below, and blocked turns restore the car's heading. Resident respawns track their original spawn record, avoiding duplicate populations after a spawn has moved to a safe position. Police keep their uniform details after death.

NPC bodies, car bodies and wheels reuse cached meshes while limbs and wheels remain animated. Camera culling skips offscreen actors, cars and drops. Animated and glowing scenery has separate indexes, avoiding scans of every static prop each frame. Shooting and visibility rays traverse only the collision grid cells they cross, with reusable stamps to avoid duplicate tests and temporary sets. Navigation uses a reusable primitive heap, and vehicle collision checks avoid allocating temporary bounds. Supersampling remains at 1.5 times the window resolution.

The native rendering fixture also records timings in `build/gta7-render-check/performance.txt`. In a local comparison on an RTX 4070 Ti SUPER, at 1280 by 720 with 80 frozen NPCs and unchanged supersampling, median frame time fell from 15.551 ms to 10.205 ms (about 34%). A batch of 12,000 collision rays fell from 633.059 ms to 27.780 ms (about 23 times faster). These measurements include the district replacement and compare the same city camera after 30 warm-up frames over 90 measured frames, synchronizing the GPU after each frame. They describe this offscreen workload; live gameplay timings vary. An independent test compares 1,700 rays with an exhaustive collision scan, including vertical rays and grid boundaries.

## Saves

`vibe/gta7-progress.json` retains its filename for compatibility, but its contents and `.bak` backup now use authenticated AES-GCM encryption. An existing version-1 JSON save migrates automatically on first load. After migration, plaintext replacements and unauthenticated edits are rejected. A damaged primary recovers from the previous valid backup when available. A backup is created on the very first save. Both copies are written through temporary files and atomic replacement where supported; a failed backup write leaves the primary intact. If both copies are unreadable, saving is disabled and the originals are preserved. A missing key is never silently replaced for an existing encrypted profile; restoring the original key and reopening GTA7 restores access.

Back up the save **and** the adjacent `.gta7-save.key` together. This is local tamper resistance, not server-authoritative anti-cheat: someone controlling the executable, key, or a previously valid backup can still alter or roll back their own offline progress. Existing plaintext progress cannot be authenticated retroactively during its first migration.

The encryption follows Java's [authenticated encryption and fresh GCM IV requirements](https://docs.oracle.com/javase/8/docs/api/javax/crypto/Cipher.html).

## Verification

Run `gradlew test --tests "dev.vibe.game.gta.*"` for progression, tamper detection, missing-key recovery, bounded navigation, ceiling and terrain collision, traffic, factions, world connectivity, combat, ray traversal, the emoji image and all 18 elevator landings. `gradlew verifyGta7Rendering` performs native offscreen OpenGL checks and writes timings plus menu, combat and district screenshots to `build/gta7-render-check/` without launching a Minecraft window or loading a player profile.
