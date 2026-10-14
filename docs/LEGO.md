# LEGO character collection

Install `Vibe-Lego-0.0.6.jar` alongside core Vibe in your game's `mods/` folder and restart. Then open **Meme** in ClickGUI. All LEGO code and assets belong to the extension; core includes none. Removing it preserves your progress and model cache. See [Extensions](EXTENSIONS.md) for installation, the API and the example Test extension.

The collection contains **989 selectable characters and costumes**: Complete Saga (126), Clone Wars (126), Harry Potter 1–4 (170), Harry Potter 5–7 (206), The Hobbit (110), and DC Super-Villains (251). This includes supplied DLC collections and linked character transformations. Game filters, 64-character pages, mouse wheel, Page Up/Down and arrow keys make every entry reachable. Vehicles, blank customizer templates and hidden non-playable entries are recorded separately in the coverage report.

- **Lego** enables the local character replacement, animated stud pickups and LEGO HUD.

- **Lego Character selection** opens the starfield collection. Select with the mouse or arrow keys and Enter. Tab switches to the pass.

- **Lego BattlePass** opens the six-chapter Saga Pass. Claim completed quests or buy the next tier with collected studs. Purchases show a confirmation and exact price.

Obi-Wan Kenobi and Qui-Gon Jinn start unlocked. Thirty further characters unlock across the six films, ending with The Emperor. Locked collection slots use the original question-mark icon. The collection follows the original compact portrait grid and centered selected portrait, without the large header, side preview or oversized controls. All frames and controls use the main menu’s antialiased rounded mask, with smooth font rendering and normal text capitalization. Characters are cosmetic, with identical Minecraft movement, hitboxes, health and equipment behavior. LEGO takes priority for your own body while enabled, using `CustomModelRenderer.drawPlayer`, the existing model fitting/loading pipeline and vanilla limb poses. Your CustomModelRenderer selections are preserved. Armor rendering is hidden on the LEGO body, while its armor value remains visible in the LEGO HUD.

The portrait can be placed at the **top right** or **top left** using **Portrait position**. It uses the original game icon inside a smooth blue frame. Four original heart sprites represent the current fraction of Minecraft health; fractional hearts, numeric health, absorption and armor are shown. The centered stud counter shows spendable currency. The segmented True Jedi bar fills at **10,000 collected studs in the current world/session**, independently of purchases; it is cosmetic and does not give a reconnect bonus. HUD scale, audio volume, pickup radius and the number of visible studs are configurable.

## Stud economy

Original TCS values are preserved: **silver 10, gold 100, blue 1,000, purple 10,000**. All four use the game's original 32-frame spinning stud sprites. Pickups scatter, bounce, bob, attract toward you within 3.5 blocks by default, and expire after 20 seconds. Currency is awarded only on pickup. Original silver/gold/blue pickup sounds, drops, UI sounds, quest and unlock cues are included. Sound volume respects Minecraft's master and players volume controls.

| Confirmed action | Drop | Expected value before collection |

| --- | --- | ---: |

| Player hit, block break or placement | 2–3 silver; 5% chance to replace one with gold | 29.5 |

| Player kill | 11 silver, 4 gold, 1 blue; 1% chance the blue becomes purple | 1,600 |

| Bed destruction | 15 silver, 5 gold, 2 blue; 2.5% chance one blue becomes purple | 2,875 |

Killing and bed-breaking rewards also include any earlier hit or break rewards from that action sequence. Purple studs are occasional jackpots; progression does not require finding them. At the particle cap, new drops are skipped instead of deleting existing uncollected rewards.

Block and jump quests consume the same confirmed actions as the Statistics module. Completed mining packets and observed world changes are deduplicated, including late packets. Quest progress is independent of the stud-drop cooldown and repeat-position protection. Rewarded placements are remembered for ten minutes; breaking or replacing those positions does not pay again. Block rewards have a three-tick global cooldown. Hits require observed damage or a new hurt animation, with a ten-tick target cooldown. Kills are deduplicated per player entity for ten seconds; a respawned player can count again immediately. Each bed has a two-second deduplication window for its two halves and server announcements; distinct beds can count immediately. Creative and spectator modes earn no rewards. World changes clear pending interactions and pickups.

This is a client-only feature. Hit and death observation cannot prove which player dealt every point of damage on every multiplayer server. Standard `... by <your name>` kill/bed system announcements supplement the observed interactions; player chat is ignored. Server-specific messages or servers that hide death/health information may require a future adapter. No actual server items, balance or inventory are changed.

## Quests and tiers

| Repeatable quest | Initial target | XP |

| --- | ---: | ---: |

| Break beds | 2 | 500 |

| Defeat players | 8 | 450 |

| Jump | 120 | 200 |

| Place blocks | 96 | 300 |

| Break blocks | 64 | 300 |

| Collect studs | 2,500 | 300 |

Quests stop accumulating when ready and must be claimed. After each claim the target rises by 10% of its initial target, up to twice the initial target; XP remains fixed. There is no daily reset or expiry. Only actions while LEGO mode is enabled count. Jump counts require an actual grounded-to-airborne transition with jump input, using the Statistics tracker.

Tier `t + 1` requires `600 + 50t` XP. Excess XP carries into later tiers. Buying it costs `8,000 + 2,000t` studs before the discount for already-earned XP; the final price is rounded up. Completing all thirty tiers with studs alone costs **1,110,000**, while quest-only completion takes **39,750 XP**. The original pass stops at tier 30. Additional collection characters each cost **6,000 studs**, with a confirmation, independently of pass tier. After tier 30, repeatable quests pay five studs per former XP point (1,000-2,500 studs per claim). Quest rewards do not recursively complete the stud-collection quest. These values provide a bounded starting economy; multiplayer playtesting is still needed to tune the pace for individual servers and player skill.

## Assets and saves

The supplied PC installations are read without modification. Native NU20/GHG and NXG/DX11 geometry is assembled with its authored layers, attachments, diffuse materials, vertex colors, skeletons and skin weights, then packaged as glTF for the existing CustomModelRenderer. Standalone original character icons are preferred; characters without an icon receive a portrait rendered from their own imported model. The original 32 pass IDs and ordering are retained so existing unlocks and saved selections stay valid.

`catalog.json` is the runtime roster. `coverage.json` records every source collection entry, game, character definition, imported model, exclusion and missing cosmetic reference; publication fails if any declared playable character lacks a valid bundle. `provenance.json` retains the original TCS HUD, sound and pass-asset provenance. The game's `STUFF/ATS/COIN.ATS` cycles the 32 original spinning stud frames.

The conversion preserves diffuse appearance and uses Minecraft's limb animation. Proprietary BRDF, dynamic facial morphs, glow/refraction and other game-specific shaders are not reproduced. Some Hobbit/DC faces therefore differ from the original animated appearance. Several shipped character definitions reference cosmetics that are absent from the supplied archives, such as King Shark's leftover Hulk face reference and a few costume prints. Their native body/material remains usable, and each omitted reference is explicit in `coverage.json`; no other character is substituted.

To rebuild from the supplied folders under `context/lego`:

```text
python tools/import-lego-roster.py
python tools/publish-lego-roster.py
```

Python, Pillow and NumPy are required. Assets go to `extensions/lego/src/main/resources/assets/vibe/lego`; `gradlew legoExtensionJar` packages them. Archive indexes and intermediate extraction are cached under `build/lego-catalog` and `build/lego-sources`; partial extracted files are detected and replaced. Bundles are written through a temporary file. The older `import-lego-assets.py` and `bundle-lego-assets.py` rebuild the original TCS HUD/audio assets. The LZ2K decoder is adapted from OpenSagaDev/nudat under MIT, with its notice in `extensions/lego/LICENSES/OpenSaga-nudat-MIT.txt`. Archive/model layout references are attributed in the scripts; research tools are not bundled. These user-supplied game assets remain proprietary, separate from Vibe's code license; distribution requires the rights holders' permission.

Only the selected model is installed asynchronously into `<game directory>/vibe/lego/models`. Versioned bundle names refresh converted assets after updates without overwriting user custom models. GPU loading remains asynchronous through the existing model manager. Currency, unlocks, selection, purchased characters and quests are saved to `vibe/lego/progress.json`, independently of config profiles. Writes use a temporary file, atomic replacement where supported and a previous-save `.bak`. Corrupt data is preserved and a valid backup is used when available. If both saves are invalid, the client preserves them and blocks saving the temporary empty state.

`gradlew testLegoExtension verifyLegoRendering` checks progression, backups, packaged portraits, all stud animation frames and PCM audio, collision, hit-to-pickup and Statistics-to-quest regression paths, the actual collection/pass/purchase screens at two sizes, scaled selection input, both HUD corners, game filters, paging, extra-character purchases, and all 989 bundled characters in idle and walking/swing poses. Galleries are paged into groups of 32. Captures are in `build/lego-render-check`.

