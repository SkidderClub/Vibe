# Feature additions — 10 November 2026

## Combat and movement

- **Criticals** adds NoGround and Jump modes. NoGround primes with a normal jump and changes only the ground flag of existing outgoing movement packets. Jump requests ordinary jump input around native attacks, including manual clicks and both auras; damage still follows Minecraft's falling-hit rules. NoFall retains the final ground-flag policy.
- **Targetstrafe** circles a combat target, or an optionally selected nearest filtered target. Radius, direction, jump-key activation, auto-jump, void checking and the radius preview are configurable. Native movement forecasts reject blocked, unloaded or unsupported paths. It owns movement through MoveFix while leaving the aura's rotations and aim point intact.
- **Auto Water** (World) places a hotbar water bucket below a burning player, using the shared Basic/Silent switching and highlights, MoveFix action rotations and native bucket use after walking packets. It checks reach, placement space and dimension, yields to active inventory/item actions, and restores the camera and original slot. Rotation speed and action delay are configurable.
- **Aspect Ratio** (Visual) changes perspective projection through ratio presets or a custom slider. Hand projection is separately selectable; orthographic HUD/menu layout, FOV and depth planes are preserved.
- **Projectile Dodge** forecasts incoming arrows, snowballs, eggs and potions against the player's predicted body. The LiquidBounce planner chooses a perpendicular escape, accounts for momentum, and checks obstacles. Assistance works while movement keys are held; Respect Movement Input optionally yields to them. Scaffold and other forced-movement owners retain priority. Only loaded, supported escape paths, including braking, are accepted; the line remains useful with assistance disabled.
- **Pearl Catcher** follows an observed foreign pearl's collision path and aims an owned pearl at its predicted impact. Delay, range, rotation speed, preview and shared Basic/Silent switching are configurable. It does not throw if the simulated shot misses the destination or terrain is unavailable. The destination is retained briefly after the foreign pearl disappears.
- **Step** offers native collision stepping and ordinary obstacle jumps. **Reverse Step** offers Instant and Pull descent, a drop limit and an enabled-by-default void check. **Parkour** uses the existing future-footprint edge approach, with edge distance starting at zero. **Fast Ladder** offers Motion and Pulse modes with separate ascent/descent speeds.
- **AutoFish** uses the native use-item controller, fishing-hook motion, configurable reaction/recast delay, timeout and remaining rod durability. Basic/Silent switching and hotbar highlights use the existing shared service. Physical clicking, inventories and higher-priority actions pause it. Its module description and ArrayList suffix show its actual waiting/reeling/paused status.

Rotation requests use MoveFix ownership. Pearl Catcher reserves its action before the aura rotation phase; clicking follows the walking update through the native controller. No independent inventory-click transport was added.

## World visuals

- **Beverage Notification** reports observed potion drinking, including its displayed potion name, and optionally golden apples. Chat and HUD notifications are independently selectable. A held item alone is not treated as consumption.
- **Movement Predictor** reuses Vibe's 1.8.9 simulated-player physics with current movement, sprint and jump input. Prediction length, line and Static/Rainbow/Fade colours are configurable.
- **Collision Preview** incrementally collects actual collision boxes, retaining its last complete mesh until a replacement is ready. Minimal mode removes internal flat seams and joins continuous edges; height changes and multipart collision geometry remain visible. Liquids and non-colliding decorations contribute no boxes.
- **Enchantment Glint** controls colour, colour animation, sheen speed and opacity in native item and armour passes, including GUI/HUD items. Custom-model glint uses a separate untinted geometry pass when a model has vertex colours.
- **Block Debris** uses block-atlas fragments and the existing bounded, bouncing particle simulation. **Footprints** and **Landing Dust** are modes inside CustomCosmetics, with contact-height placement, fading/count limits and material-coloured landing bursts.
- Teleport's existing cosmetic portals now use blue and orange. Horizontal surfaces determine portal placement and available span; the rim closes with the animation. Teleport success remains separate from its cosmetic finish.

## Practice and cameras

Animus offers Free Practice, Spacing, Height Changes, Target Switches and Projectile Dodges. Exercise performance and manual demonstration coverage are displayed separately. Preview performance never increases recording coverage. Projectile exercises remain inside the local arena.

Freecam retains its original optional FPV controls. The configured perspective key switches to an external view of the drone. Optional trail controls provide Static/Rainbow/Fade colours, glow and lifetime. Enabling **Camera Rig** exposes its editor: capture/reorder/delete poses, edit coordinates/angles/durations, scrub a timeline and play with per-pose timing or fixed travel speed. Collision checking stops playback at blocked or unloaded terrain. Saved paths belong to their world and dimension.

The death screen's **Explore** button opens local camera exploration. Movement keys control the camera; hold right mouse to look. Respawn and return buttons remain available. Exploration does not restore player health or replace the server-controlled player/view entity.

## Meme and VibeOS

- **Emote Wheel** offers Dab, Sit, Wave, Cheer, Dance, Bow, Facepalm and Stop with a skin preview. Its key opens a floating wheel over the unchanged world. Emotes continue through movement and turning; Sit ends on a physical movement or jump key. Optional sitting starts after two minutes of physical inactivity. The existing no-settings, two-second automatic Dab module remains separate.
- **Bobblehead** adds a damped cosmetic spring to head turns and landing responses, without changing player rotations or collision.
- **Bigbrain** gives all players an enlarged head and a smaller, grounded body. It has no settings, preserves Bobblehead poses, and scales native skin layers, armour and held-item attachments with the model.
- **Pocket Pets** offers a ghost and rubber duck, collision-aware following, colour/size/speed/radius controls, idle behaviour and optional shoulder perching when space is clear.
- VibeOS includes **Minesweeper** with three board sizes, first-click safety, flood reveal, flags and persistent best times. **Sudoku** includes difficulties, number selection, pencil notes, undo, optional hints and validated saved progress. The home screen pages automatically to keep all app icons visible.

The phone's configured navigation bindings apply to both games. In Minesweeper, Select reveals and Delete flags. In Sudoku, Select opens numbers, Delete toggles pencils, and Back undoes a change or closes the number picker. Home leaves either app. Key hints use the same boxed keycaps as other VibeOS apps.

## HUD and interface

Five independently movable/scalable elements are available in the HUD editor:

- **Radar**: radar or a cached top-down terrain map, zoom/range, rotation, marker styles, friends/targets/items and an optional sweep. Map queries only loaded chunks and spreads work over ticks.
- **Network Graph**: passive ping and jitter history, measured server-tick-rate estimates and client-stall markers. Missing measurements remain blank; no artificial server timing is generated.
- **Kill Feed**: server death translations and observed local kill events, optional faces/held-weapon snapshots, fading and bounded rows. `~` identifies weaker observed attribution. Custom server chat formats are not guessed as confirmed kills.
- **Session Timeline**: kills, deaths, profile changes and bookmarks. Open History lists the bounded session history; selecting an entry opens that account's Statistics view.
- **Candle**: a layered, glowing flame reacting to view changes.

All five support Skeet, Vibe, LiquidGlass and Lavalamp. Radar and Candle also allow a transparent surface. Kill Feed additionally supports CounterStrike and Fortnite styles.

Custom Healthbar's colour grading is **local to that HUD element**: exposure, contrast, temperature and colour-vision presets. Its editor offers Graded and Original preview modes. Other HUD elements and the world stay outside the grading pass.

**Wurst** is an additional ClickGUI theme: grey category windows, green enabled rows, movable/collapsible panels and separate module-setting windows. It uses Vibe's modules, categories, settings and bindings.

Chatter has a **Hotkey** source. Bind the trigger in the Message Menu; its action still uses the standard delay/cooldown/session queue and existing match notification. Held-key repeats and typing in another GUI do not trigger it.

## References

These features use Vibe's implementations and rendering/input services. The requested visual/behaviour references were inspected; no external client JAR is required:

- [Wurst ClickGUI](https://wurst.wiki/clickgui)
- [ThunderHack CandleHud](https://github.com/Pan4ur/ThunderHack-Recode/blob/HEAD/src/main/java/thunder/hack/features/hud/impl/CandleHud.java)
- [BleachHack DeathExplorer](https://github.com/BleachDev/BleachHack/blob/HEAD/src/main/java/org/bleachhack/module/mods/DeathExplorer.java)

## Validation

- Full offline suite: 932 tests, 0 failures/errors, 4 skipped.
- Native OpenGL captures cover the five new HUD widgets in four themes, Wurst category/settings windows, the camera editor, pet meshes, emote poses, both phone games and their menus.
- Health grading checks confirm the Original preview, unchanged surrounding pixels, translucent colours and additive glow. Native health icon transparency also passes.
- Animus native rendering, preview isolation and small-screen checks pass. The five-opponent fixture measured about 0.6 ms per simulation tick and 9.8 ms per rendered frame on the laptop's GT 740M; this is a fixture measurement, not a multiplayer FPS guarantee.
- Production Forge/OptiFine loading verifies the final transformed item/armour glint, block debris, model-pose restoration and wrapped F5 input hooks.
- Native portal animation/state checks pass. The production JAR is `build/libs/Vibe-1.8.9-0.0.7.jar`; start it through `run.bat`.

Live multiplayer behaviour has not been play-tested during this change.
