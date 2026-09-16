# Battlefront 3

Enable **Battlefront 3** in **Meme** while in a Minecraft world. It opens an independent,
offline third-person Star Wars game, using the same standalone GUI approach as GTA7.
Minecraft entities and terrain are not modified. Single-player Minecraft pauses behind
the game; multiplayer servers continue running.

## Command deck

The command deck uses dark, rounded cards, rose accents, distinct navigation icons and
a prominent deployment button. Labels remain beside the icons. The 24 original SVG
symbols live in `src/main/resources/assets/vibe/battlefront/command-icons.svg`; the native
client rasterizes and caches those vector assets for crisp icons without a network dependency.
Troop models retain faction colors. HUD and map markers consistently use mint for your
army, rose for hostiles and gold for neutral objectives and your waypoint on either side.

- **Operations:** choose Geonosis or Endor, either side, operation, threat tier and loadout.
- **Your army:** edit composition, doctrine, formation, role equipment, veteran training
  and individual appearances. Live previews show your actual customized equipment.
- **Armory:** purchase/equip weapons and armor, modify each item, and research twelve
  permanent character/army upgrade paths.
- **Service record:** view campaign totals, faction victories and field contract rewards.
- **Field manual:** see controls and rules in game.

Geonosis features the Republic against the Separatists, with clones, commanders, B1s,
B2s, repair and commando droids. Endor features the Empire against an alliance of Rebel
resistance fighters and Ewok hunters. Every side is playable. All troop and scenery
meshes are original procedural models; no Sketchfab assets are bundled.

## Controls

| Control | Action |
| --- | --- |
| Minecraft movement, jump and sprint bindings | Move, jump and sprint |
| Mouse | Look |
| Minecraft attack / use bindings | Fire / aim |
| R | Reload power cell; ammunition supply is unlimited |
| G | Ion detonation at your aim point, up to 25m; 16-second cooldown |
| Q | Bacta heal; 26-second cooldown |
| V | Orbital barrage ahead; requires 6 personal kills; 70-second cooldown |
| E | Search a nearby indoor supply cache |
| Hold jump while airborne in Beskar armor | Use the four-second jetpack; fuel replenishes on the ground |
| Tab | Army orders: advance, follow, hold current position |
| M | Pause and open the tactical atlas; mark locations and rally the army |
| Z | Cycle the local minimap between 55m, 85m and 130m radius |
| F1 | Combat help |
| Escape | Pause/resume; return from debrief; exit from command deck |

Dedicated R/G/Q/V/E/Tab/M/Z keys take priority over Minecraft actions. Losing window focus
pauses the battle and releases the cursor. Resizing retains the active operation.

## Operations and progression

**Conquest:** capture a majority of the three posts to drain enemy tickets. A soldier's
death costs one ticket; a player's death costs four. Soldiers replenish after 6–9 seconds;
the player returns after five, with a three-second insertion shield.

**Breakthrough:** capture all three enemy posts in order. Either selected faction is the
attacker. Secure all three before reserves or the seven-minute limit run out.

**Supremacy:** eliminations cost two tickets; command posts exert a smaller ticket drain.

Stand within a post's eight-metre ring to capture it. Opposing troops contest capture.
Commanders count twice as much as regular troops; the player counts 2.5 times as much.
Support units heal nearby soldiers and the player. All player loadouts also regenerate
slowly after seven seconds without taking damage.

## Battalion customization

The Army page has four tabs: **Composition**, **Tactics**, **Unit equipment**, and
**Appearance**. These settings persist across all factions and apply when deploying.

| Doctrine | Effect on allied troops |
| --- | --- |
| Combined arms | Balanced stats |
| Shock assault | +18% damage, +8% movement, -15% base health |
| Defensive bulwark | +25% base health, -15% movement, -8% damage |
| Recon expedition | +20% movement, +25% capture weight, -15% damage |

Choose a **wedge**, **firing line**, **marching column**, or **dispersed patrol** formation.
Follow formations rotate with your heading; hold formations preserve the heading when
ordered. Advance orders continue to prioritize the command posts. Soldiers plan ground
routes around obstacles for distant rally orders and when blocked by cover.

Each of the five roles can receive any owned weapon and armor set. Cycle the equipment
buttons to assign an item; selecting the role service weapon restores its original gear.
Purchasing equipment in the Armory unlocks it for army assignment without a per-soldier
charge. Troops visibly carry their assigned weapons and armor. Army weapon damage and
fire rates use separate balancing from the player; ion weapons retain their droid bonus.
Weapon power and cycling modifications also improve the army version at reduced strength.
Army armor grants 65% of its health bonus, 70% of its resistance and its movement multiplier.
NPC flight rigs are armor equipment; only the player's flight rig enables controlled flight.

Each role has **five veteran training levels**. A level adds 5% of role base health and
4% of role base damage, alongside existing army research. Support veterans also heal
15% faster per level. Training costs rise with the level and are charged once for the
entire role, including replacement soldiers. The interface shows the resulting stats.

**Appearance** has separate settings for each role and your character:

- Eight color schemes, including faction issue, azure, crimson, desert, forest, arctic,
  obsidian and violet.
- Four insignia styles: clean plates, center stripe, twin stripes and veteran chevrons.
- Standard headgear, a rangefinder, macrobinoculars or communications antennas.
- Standard gear, an expedition backpack, a signal relay or bacta canisters.
- Standard shoulders, an officer pauldron or reinforced shoulder plates.
- Fresh, campaign-worn and battle-scarred finishes.

Cosmetic changes are free. **Copy look to all army roles** copies the displayed appearance
without changing your character or equipment assignments. Attachments adapt to B2 and
Ewok proportions; veteran rank bars and equipment upgrades remain visible.

Assault is balanced. Heavy has 15% more health and 15% slower movement. Marksman
has 10% less health and 12% faster movement. These class bonuses stack with purchased
equipment; the selected weapon determines ammunition, damage, fire rate and reloads.
Head hits deal extra damage. The shoulder camera retracts near solid scenery and inside
buildings. Aiming stays in third person; muzzle collision prevents firing through cover
that the offset camera can see around.

### Reticle and hit feedback

The crosshair uses an outlined center dot and geometric aiming marks, with a smaller
aimed reticle and expansion during recoil. The bowcaster has a segmented spread ring;
the aimed marksman rifle uses precision brackets. A circular indicator shows reload
progress. Dark outlines keep the reticle visible over both sand and shaded forest.

Hit confirmations are separate strokes around the aiming point: white for a body hit,
gold with a small chevron for a headshot, and rose with double ticks for an elimination.
They animate and fade, preserve the stronger feedback during multi-pellet hits, and clear
on respawn. Damage blocked by scenery does not produce a hit confirmation.

### Combat HUD and movement

Compact instruments anchor to the actual screen edges, including wider and taller
aspect ratios. The upper center holds tickets, objective status and a heading compass;
health and armor resistance sit at lower left, with ammunition and ability keys at
lower right. Segoe UI typography (system sans-serif fallback), larger key numbers,
subtle shadows and translucent backing keep information readable over bright sand
and dark interiors. Detailed controls and contract goals appear with **F1**.

The player-centered minimap shows shaded terrain, contour lines, trails, building
footprints and entrances. It includes your heading and view cone, nearby troops,
deployment bases, objective letters and a rally marker. Off-map objectives stay pinned
to the edge with direction arrows. **Z** changes range; **M** opens the full theater
atlas, which uses the same cached terrain image and preserves marker/rally controls.

Incoming hits create short-lived, camera-relative directional arcs and a soft edge flash.
Up to four directions can appear together; they fade and clear on respawn. Your hits
produce floating numbers above the target: white body damage, gold headshots and rose
eliminations. Numbers show health actually removed, combine simultaneous pellets and
quick hits, and expire after about one second. Armor remains damage resistance; there
is no separate replenishing shield pool.

Reloading visibly lowers and tilts the weapon, moves the support arm with the detached
power cell, reseats it and returns to the firing pose. Rifle cells, the repeater drum
and other feed components preserve their individual models. The reticle arc, ammo
progress line and model share the reload clock, including cycling upgrades. Ammunition
refills only when the animation completes. Death cancels an in-progress reload.

The jump now peaks around **2.4m** above the starting floor (previously about 0.9m).
Gravity, landing and ceiling collision still apply. Holding jump does not automatically
repeat ordinary jumps; Beskar retains its limited airborne flight mode.

## Weapons and armor

The Armory includes separate **Weapons** and **Armor** tabs with rotating model previews,
purchase/equip actions, ownership and three modification tracks for every individual item.
Reselecting owned equipment is free. Equipment applies to all playable factions.

| Weapon | Price | Base damage / cell | Distinguishing behavior |
| --- | ---: | --- | --- |
| DC-15A | Starter | 28 / 18 | Balanced service rifle |
| E-11 | 320 CR | 24 / 22 | Compact, fast carbine |
| A280 | 750 CR | 37 / 20 | Higher-damage assault rifle |
| DL-44 | 1,000 CR | 66 / 10 | Heavy pistol |
| DC-15LE | 1,300 CR | 22 / 48 | Rapid repeater with a drum magazine |
| DLT-19X | 2,000 CR | 112 / 8 | 210m range and tighter aiming view |
| Bowcaster | 2,400 CR | 42 per bolt / 12 | Three bolts per shot |
| Ion pulse repeater | 3,200 CR | 28 / 32 | +60% damage against droids |

Each weapon has eight levels of **Power coupling** (+9% damage per level),
**Extended cell** (+3 rounds) and **Cycling assembly** (+5% fire/reload speed).
Native models include distinct barrels, receivers, stocks, scopes, rails, vents,
drums, crossbow limbs, ion coils and power-upgrade indicators.

| Armor | Price | Health bonus | Reduction | Movement |
| --- | ---: | ---: | ---: | ---: |
| Field issue | Starter | 0 | 0% | 100% |
| Recon harness | 550 CR | -10 | 5% | 112% |
| Assault cuirass | 950 CR | +35 | 10% | 99% |
| Heavy siege armor | 1,600 CR | +75 | 22% | 88% |
| ARC commando rig | 2,600 CR | +50 | 18% | 105% |
| Beskar flight rig | 4,200 CR | +95 | 28% | 96% |

Each armor has eight levels of **Layered plating** (+2 percentage points damage
reduction, total capped at 60%), **Life support** (+12 health) and **Powered servos**
(+2% movement). The sets visibly change your third-person character's armor, visor,
shoulders, pouches and backpack. Recon has a sensor visor, heavy armor has thicker plates,
ARC has a kama and communications pack, and Beskar carries an operational jetpack.

Credits come from personal and allied eliminations, captured posts, operation completion
and four contracts that reset for each battle: 10 personal eliminations, 3 captures,
35 army eliminations, and five recovered supply caches (+300 CR). Retreat records a
defeat and retains earned credits. Each two
victories unlock another threat tier, up to eight, with stronger opponents and larger
payouts. Defeat consolation requires at least 30 seconds of battle and an army kill or
captured post; immediately retreating awards no credits or reputation. Army upgrades
affect all selected factions. Army capacity grows from 12 to 36.

| Upgrade | Per level |
| --- | --- |
| Blaster amplifiers | +4 base weapon damage |
| Composite armor | +20 base maximum health |
| Thermal regulators | +2 rounds per power cell |
| Servo assistance | +4% movement speed |
| Ion detonators | +18 grenade damage |
| Bacta injector | +8 ability healing |
| Weapon supply | +8% squad damage |
| Reinforced plating | +10% squad health |
| Transport capacity | +2 soldier berths |
| Reserve garrison | +12 starting tickets |
| Tactical academy | +10% squad capture weight |
| Supply network | +5% elimination credits |

## Worlds and rendering

Both **448 x 448m** playable theaters have a continuous triangular height field, four
times the previous playable area. Outer ridges, uplands and valleys have additional
elevation, with graded approaches to every building and the raised village stairs. Movement,
ballistics and ground shadows use the same terrain surface. Cover blocks shooting and
movement. Geonosis has a crater, ridges, rock spires, a foundry, gunship landing zone,
armored crawler and wreckage. Endor has large redwoods, roots, forest undergrowth,
raised village huts, an Imperial bunker, a shuttle and a walker. Ships, the crawler and
walker are scenery; they are not pilotable vehicles.

**Every building is enterable: 12 on Geonosis and 14 on Endor.** Geonosis has two
factory complexes, command and observation posts, medical/repair stations, armories,
barracks and two power facilities. Endor has four elevated forest lodges, two bunkers,
communications/listening posts, two medical sites, a power station, barracks and an armory.
All have front and back
doors, solid floors/ceilings, window apertures and furnished interiors. Both doors of the
forest lodges have full staircases. The foundry has an accessible mezzanine and internal
stairs, conveyors and overhead equipment. Interior details include structural beams,
console screens, workstations, shelves, medical beds, holotables, lights and supply crates.
Additional details include weapon racks, bunk beds, reactor stacks, roof vents, pitched
wooden roofs, wall seams, door controls, conduits, gable supports and entrance beacons.
Maintenance yards have generators, cargo, antennas, vaporators or fallen timber. Trails
connect the central battlefield to the surrounding districts, with denser vegetation,
rock formations and additional wrecks and vehicles.

Local lights illuminate occupied interiors. Press **E** beside a glowing indoor supply
cache to earn 85 credits, reload and restore health, once per building per operation.
Each of the nine outlying districts has two enemy patrol troops. Patrols remain near
their district and normally return after 45 seconds; defeated guards stay down once
that location's supply cache has been recovered. The main battle still revolves around
three command posts, keeping both central combat and optional exploration available.

### Deployment and capture layout

The three objectives form a broad triangle on each map, roughly 225-260m apart.
The two army bases sit near opposite edges, more than 400m apart, with clear deployment
areas and approach trails. Capture circles have graded terrain and clear interiors;
cover sits around their perimeter. Your character initially faces the home objective.

| Theater | A | B | C |
| --- | --- | --- | --- |
| Geonosis | Dustfall relay, southwest | Petranaki crossroads, east | Northern foundry approach, northwest |
| Endor | Ranger landing, southeast | Fern valley crossing, west | Shield relay clearing, northeast |

In Conquest and Supremacy, AI groups defend their home post, attack the outer route,
and contest the middle objective. Distant orders use ground route planning. Breakthrough
retains sequential capture from the attacking faction's end of the map. The battle's
seven-minute limit and existing reward rules are unchanged.

Press **M** for the tactical atlas. It shows elevation, all buildings, command posts,
troops, deployment bases (triangle icons), your position and the current waypoint. Click the map or a location name to
mark a reachable point, then select **Rally army to marker** to issue a hold order there
and resume the operation. The map also tracks visited locations and the cache contract.

The renderer caches terrain heights for movement and rays, and divides terrain/scenery
into 48m sections, drawing only nearby sections. It also uses directional lighting, soft ground/contact
shadows, distance fog, glowing capture rings, blaster trails, particles and supersampling
when framebuffers are available. Shadows are approximations, not a shadow-map pipeline.
Troops use articulated meshes and role-specific silhouettes/equipment. The three bundled
sound effects are synthesized originals; `tools/generate-battlefront-audio.py` reproduces
them with NumPy and SoundFile.

## Saves

`vibe/battlefront3-progress.json` stores credits, upgrades, army composition, rank XP and
service records, owned/equipped weapons and armor, each item's modifications, doctrine,
formation, per-role gear/training, and all six appearance profiles.
Version-1 and version-2 saves migrate to version 3 and retain existing progression and
equipment. New battalion settings receive defaults.
A `.bak` recovery copy accompanies it. Purchases and army edits save
immediately, battles save every 25 seconds, and completion and exit also save. Active
battles are not resumed after closing the game. Local saves use validated JSON and atomic
file replacement, not GTA7's encrypted save format.

If the primary save is damaged, a valid backup is recovered. If both are unreadable,
the files are preserved and saving/purchases are disabled for that session. Restore a
valid backup and reopen the module. Save errors appear along the bottom of the screen.

## Verification

```powershell
.\build.bat test --tests "dev.vibe.game.battlefront.*" verifyBattlefrontRendering
```

The unit suite covers terrain, spawns, movement, shooting, cover, rewards, upgrades,
save recovery/migration, equipment economics, jetpack limits, entry into every building,
stairs, indoor floors, camera collision, respawns, AI battle completion and objective order.
Expansion checks cover routes to both approaches of all 26 buildings, outer boundaries,
rally travel, patrol clearance, doctrine/role stats, training limits, customization copying,
version-2 migration and invalid version-3 preservation. Additional tests
validate wide objective spacing, safe deployment slots, routes from both bases
to every post, AI engagement across all three objectives, hit classifications and the SVG
symbol sheet. Combat tests cover jump clearance and ceilings, reload/fire timing,
damage aggregation and expiry, incoming directions, camera projection across aspect
ratios, reload pose continuity and terrain-map footprints. The native
OpenGL fixture checks UI purchases, army edits, controls, resize continuity, close/save,
army equipment/training, appearance editing, tactical-map markers and rally orders,
matrix/texture restoration and framebuffer fallback. Additional captures show both
combat HUDs, radar ranges, aimed views, reload stages, and 4:3 and wide-screen layouts.
It writes hub, battlefield and
interior, equipment and troop-sheet screenshots to `build/battlefront-render-check/` without opening Minecraft
or reading an existing player profile.
