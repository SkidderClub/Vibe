# Vibe changelog

## Unreleased

- Improved frame rates of several visuals without changing how they look. BedESP's Wool colour stops at the nearest wool block instead of reading 1183 blocks per bed every frame, and its bed scan resolves each block id once. ESP, ChestESP, ItemESP and BedESP boxes draw their outline in one call instead of three. 2D ESP saves and restores the OpenGL state once per frame instead of once per player and prepares its gradient shader without per-element lookups. LiquidGlass copies only the part of the screen a pane can sample instead of the whole screen for every pane. Cute Visuals reuses precomputed heart, dot and star shapes, rounded HUD backgrounds and blurred ArrayList rows are drawn in one call, and module, language and font-width lookups are cached.
- Added CustomModelRenderer (Visual) with two modes. **Swords** replaces swords in hand with a 3D model held exactly like the sword in first and third person, including blocking and Animations. **Player Model** replaces the player body with a character whose head, arms and legs follow vanilla's walking, sneaking, swinging and bow poses. Defaults are the Sketchfab CS knife and Star Wars character collections, downloaded on selection with your Sketchfab API token (`.models token`); own `.glb`, `.gltf`, `.obj` and `.zip` models go into `.minecraft/vibe/models/swords` or `players`, and `.models import` adds any Sketchfab model or collection. Knives are oriented automatically, rigged characters use their skeleton and static ones are split by their proportions, with T-poses lowered. Sword Scale and Player Scale resize the models. Loading runs in the background; display lists, a triangle Detail Limit, a distance LOD and a Texture Size cap keep large models fast. See [docs/CUSTOM_MODELS.md](CUSTOM_MODELS.md).
- Improved Scaffold. The rotations are now called Normal (formerly Intave) and GodBridge (formerly Polar and God Bridge); saved profiles keep their choice. Normal rotations turn at a Rotation Speed min/max range with Normal and Acceleration modes like Killaura (replacing Smooth Speed) and never turn more than 90 degrees away from the bridging direction, so jumping no longer makes them look ahead. Sideways looks back-left or back-right depending on which half of the block the player stands on, and straight back on diagonal bridges.
- Scaffold's Sneak now sneaks only at the end of a block, with Block End Distance and a fixed or randomized unsneak delay like Eagle (replacing the timed Sneak Delay). Prevent Double Sneaking keeps sneaking on a diagonal bridge from the first edge until the player has crossed into the block at the corner, instead of sneaking at each edge.
- Fixed Scaffold's GodBridge staring at the block without placing: it no longer holds sneak before building or skips placements while sneaking, looks past whichever side of the block reaches the face, and keeps the Keep Y height instead of jumping every seven blocks.
- Scaffold's Telly mode now telly-bridges: it runs and jumps facing forward, turns around only after Telly Ticks of a jump that would land in a gap, places several blocks in the air and faces forward again for the next run-up. Jumps over existing blocks no longer turn around.
- Added Smooth Back Rotate to MoveFix: when on, the server rotation turns back to the camera at Rotate Back Speed after Scaffold, Killaura or another module releases it; when off, it returns at once.
- Fixed 2D ESP health and armor bars starting above the box and ending short of its bottom: bars now span exactly the drawn box, outlines included, and every element keeps its gap from the box's visible edge.
- Improved the HUD editor preview: it renders the HUD at the game's own resolution and shows it scaled down, so outlines and text no longer break up, and every element uses its in-game renderer, size and anchor, also in the main menu. Elements without live data show sample content instead of disappearing or being drawn as differently sized placeholder cards (Stalker with nobody tracked, Health hidden at full health, an empty sidebar, no armour). Long music titles no longer vanish in the preview, and ArrayList blur no longer samples the wrong screen region there.
- Fixed the HUD scoreboard being drawn upside down with its title below the scores; it now matches vanilla's order. Stalker skin faces are no longer tinted by the row outline colour.
- Fixed the Vibe Launcher's memory setting having no effect: Unimined's default `-Xmx2G` replaced `vibeMaxMemory` when the game started.
- Reorganized the source: modules live in one package per ClickGUI category, the UI is split into ClickGUI, screens, menus, accounts, games and renderers, and the build logic moved into `gradle/`. The mod JAR no longer bundles Nashorn 15 and ASM 7, which cannot run on Minecraft's Java 8 and made Forge log class-scan errors at startup; the NES emulator keeps using Rhino. `run-fresh.bat` now uses the `vibeFreshRun` Gradle property.
- Added Scaffold (World): Normal and Telly modes, Intave, Polar and God Bridge rotations, Sideways, Keep Y, NCP/Timer/Intave tower, Always/Off/Legit sprint, timed Sneak, Safe Walk, Jump, Drag Click, Swing, Smooth Speed and a Render Count panel. Rotations go through MoveFix, whose Correct Movement mode applies to them (Scaffold's Move Fix option uses Silent when MoveFix is set to Off, and Off when the option is disabled); blocks are only placed when the ray of the rotation actually sent hits the chosen face. Spoof Slot keeps the visible hotbar slot while the server holds the blocks. KillAura, LagRange, TimerRange and Sprint pause while Scaffold is enabled.
- Redesigned the HUD editor in the main-menu and Account Manager style, following the selected menu theme: an element list with visibility switches, a live preview with snap guides, scale and theme controls, and compact setting cards with sliders, dropdowns and a draggable colour picker. Changes still save automatically, now confirmed in the status bar.
- Added Token login to the Account Manager: paste a Minecraft access token (`eyJra...`, usable until it expires, with the remaining time shown in the list) or a Microsoft refresh token (`M.C...`, stays signed in and renews itself). Tokens are stored encrypted in the account vault and only shown as a short preview.
- Rebuilt the Vibe Launcher: a play bar with live download/build progress and Stop, Vibe/GTA7/GTA8 modes, account choice with skin heads, a rotating 3D skin preview with Vibe's ESP, mod management from `mcmod.info`, shared themes, settings for memory and updates, a console, delta source updates and German/English text. The client adds the `gta8` launcher route and the `vibeMaxMemory` Gradle property for `runClient`.
- Added GTA8: Los Vibes in the Meme category, a standalone 1.6 km open city with an HDR shader renderer (cascaded shadows, physical sky, weather, wet roads, bloom), Pacejka tyre driving physics, lane and traffic-light traffic, pedestrians, a witness-based police search, six weapons, jobs, shops and encrypted saves. See [docs/GTA8.md](GTA8.md); `./gradlew verifyGta8Rendering` renders offscreen checks.
- Moved the changelog into `docs/` and documented the project layout; Windows build/run launchers remain in the project root.
- Added Licenses & credits in the main and pause menus, with Schizoid attribution, offline license texts, scrolling and source links. Consolidated license texts, THIRD_PARTY_NOTICES.md and review evidence in LICENSES/.
- Added Schizoid-derived Fog with depth-based Kawase/Gaussian blur, custom colors, animated rainbow and sky controls; Fog and CustomCrosshair also render in GTA7.
- Added the expanding reflective Torus option to Hitmarker, with the complete ported mesh, normals and noise/reflection shaders.
- Added Music with a draggable cover-art HUD, Windows media titles/timeline, MP3 internet radio and configurable audio-reactive waves, bars and lines from radio PCM or Windows output loopback.
- Added run-fresh.bat for a separate empty first-start profile on every launch; run.bat continues to use its existing settings and accounts.
- Added a small Changelog toggle that expands the scrollable release history alongside the menu, or over it on small windows, with Escape/outside-click dismissal.
- Added six persistent menu/account colour presets: Lavender, Ocean, Mint, Rose, Amber and Graphite.
- Added automatic Netscape cookie folder import at `vibe/cookies`, with saved-account deduplication, valid/invalid/error folders, per-file result notes and folder/pause controls under More.
- Added Vibe Accounts in the main menu and multiplayer server list, with Microsoft browser sign-in, saved account switching, offline profiles and launcher-session restoration.
- Added encrypted account storage, automatic Microsoft token refresh, cancellation and readable authentication errors.
- Added encrypted Vibe backup file/folder import with duplicate handling and backup export.
- Added Cookie login for exported Microsoft session cookies in Netscape .txt format, with expiry/domain/path validation, OAuth PKCE, encrypted refresh-token storage and browser-login guidance when confirmation is required.
- Documented the pinned Schizoid sources and AGPLv3/MIT terms, bundled their license texts, and added matching source archives plus the `.source` command.
- Fixed skin heads using stale launcher properties after account switching; public profiles now resolve independently, with name lookup for offline profiles and retries after temporary failures. Skin faces render square without a rounded frame.
- Removed the vanilla first frame when returning to the main menu and forced newly resized shader buffers to render immediately.
- Centred the main-menu controls and matched the account manager with smooth, antialiased rounded panels, subtle buttons and an active-profile card that also fits small windows.
- Replaced Realms with Alt Manager, removed the language globe and grouped Discord/shader controls inside the menu.
- Replaced profile initials with asynchronously loaded Minecraft skin faces, including the hat layer and standard-skin fallback.
- Enabled the bundled prestige.frag shader by default on first launch; saved shader choices remain respected.
- Redesigned account screens with rounded panels, profile initials, active-account badges, responsive layouts and clearer status messages. Moved offline, launcher and backup tools into More, and matched the Microsoft code, offline-profile and removal screens to the new style.
- Fixed cookie login rejecting Microsoft's normal redirects with a trailing empty `#`, which incorrectly reported that browser confirmation was required.
- Cookie login now uses Minecraft Java authentication directly, removing the extra IAS consent step for valid Microsoft sessions.
- Saved accounts retain their issuing OAuth application for correct token refresh; existing version 1 vaults and backups remain readable.
- Cookie login now distinguishes missing application consent, interactive sign-in requirements and declined access.

## v0.0.5

- Added catalogue paging and search for an offline snapshot of every published catalogue accessory, including 1.8.9-safe local PNG previews; the per-installation asset cache remains a fallback for future entries.
- Added a 1.8.9 JSON model renderer for authored element rotation and rotated face UVs, attached to the matching head, body, arm or leg pose.
- Reworked Cosmetics around editable, persistent presets for the local player and individual friends.
- Replaced all generated stand-in cosmetics with the public Cosmetica catalogue: presets now equip the original authored model JSON and texture from the catalogue.

## v0.0.4

- Added BlockOverlay with independent outline/fill colour modes, directional fades/rainbows and three block-break animations.
- Added the Language module and first-run flag selection for English, Chinese, Russian, Japanese and Bavarian interface labels.
- Reworked Silent and Strict MoveFix around shared effective rotation plus walking-packet transport; the camera is no longer reassigned while silent correction is active.
- Reworked W-Tab timing around chance, release delay, re-press delay and vulnerable-hit selection; added Debug messages for backtracked hits and active FakeLag queueing.
- Reworked the main menu into a Skeet surface with an in-menu changelog, capped-resolution/throttled shader rendering, and a safe vanilla kick/disconnect backdrop.

## v0.0.3

- Added W-Tab, optional local debug messages, CPS and CPS Graph HUD elements.
- Added BedESP, BlockChangeESP, FakeLag and Backtrack modules.
- Added a Skeet-style main menu while retaining third-party menu buttons.
- Improved particle glyphs, FastPlace CPS accounting, HUD defaults and Inventory Editor copy.
- Optimized the NES frame/input path and bundled runtime loading.
- Tightened delayed-packet ordering: inventory, interaction, transaction, correction and keep-alive traffic now bypass delay queues safely.
