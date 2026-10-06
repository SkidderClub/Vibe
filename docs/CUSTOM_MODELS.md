# CustomModelRenderer

**CustomModelRenderer** (Visual) replaces Minecraft models with your own 3D models. Its **Modes** setting selects one or both:

| Mode | Replaces |
| --- | --- |
| **Swords** | Every sword in a player's hand, in first person and third person. The model is held exactly like the sword: vanilla's first-person, third-person and blocking transforms (and Animations) still apply. |
| **Player Model** | The player body. Head, body, arms and legs follow vanilla's pose, so walking, sneaking, swinging, blocking, bow aiming and head turning stay animated. |

Models load in the background. Until a model is ready, and whenever it cannot be loaded, Minecraft's own model is shown.

## Default models

Out of the box the lists contain two Sketchfab collections:

- **Sword Model**: [CS KNIFES by quift](https://sketchfab.com/quift/collections/cs-knifes-2e4d663e1556469f95ca2203a069e081), default *Karambit Knife Freehand*.
- **Character Model**: [Star Wars Characters by doctormordi](https://sketchfab.com/doctormordi/collections/star-wars-characters-d60caf23f8604cef9bfe39bbe24c0b31), default *First order trooper Rigged and textured*.

The models belong to their authors and are not bundled with Vibe. Selecting one downloads it once from Sketchfab, which requires your personal Sketchfab API token:

1. Sign in on [sketchfab.com](https://sketchfab.com) (a free account is enough).
2. Open **Settings → Password & API** and copy the **API Token**.
3. In chat, run `.models token <token>`.

The token is stored in `.minecraft/vibe/models/.sketchfab-token`, outside config profiles, so sharing a profile never shares it. `.models token clear` removes it.

Sketchfab only allows downloading models whose author enabled it. In the knife collection that is 6 of 21 models (*Karambit Knife Freehand*, *M9 Bayonet Knife*, *M9 Bayonet Freehand*, *Butterfly Knife | Freehand*, *Hunting knife*, *knife CS GO*); the other 15 are marked "not downloadable" or sold in the Sketchfab store. They appear in `.models list swords` as `[locked]`. If you bought one in the Sketchfab store, `.models download <name>` fetches it with your token; if you have a file of one of them, put it in the swords folder (see below) and it works like any other model. In the Star Wars collection 124 of 131 models are downloadable.

`.models downloadAll swords` downloads every downloadable knife at once. For characters, models download when you select them.

## Own models

Models live in `.minecraft/vibe/models/`:

| Folder | Used for |
| --- | --- |
| `swords/` | Sword Model |
| `players/` | Character Model |

Each entry is one of:

- a `.glb` file,
- a `.gltf` file with its `.bin` and textures next to it,
- an `.obj` file with its `.mtl` and textures,
- a `.zip` archive containing one of the above, such as a Sketchfab "glTF" download,
- a folder containing one of the above (searched up to four levels deep).

New entries appear in the setting's list within a few seconds; **Open Folder** opens the folder and **Reload Models** reloads every model after you replaced a file. The entry's file or folder name is its name in the list.

To add any other Sketchfab model, use its link:

```
.models import https://sketchfab.com/3d-models/<name>-<id> [swords|players]
.models import https://sketchfab.com/<user>/collections/<name>-<id> [swords|players]
```

A model link downloads the model (with a token set). A collection link adds all its models to the list; each downloads when selected. Without `swords` or `players`, names containing knife, sword, karambit and similar words go to swords, everything else to players.

## Fitting

**Swords.** The knife's longest axis becomes the blade, its flattest axis the side of the blade, and the thinner end the tip. The knife is then laid along the sword sprite's diagonal with the handle in the hand. If a model ends up wrong:

| Setting | Effect |
| --- | --- |
| Reverse Blade | Swaps tip and handle. |
| Flip Blade | Turns the knife over around its length, e.g. to point a karambit's hook the other way. |
| Sword Rotate X/Y/Z | Rotates around the grip, in degrees. |
| Sword Offset X/Y/Z | Moves the model, in sprite widths. |
| Sword Scale | Scales around the grip. |

**Characters.** The model is stood upright, turned to face forward, scaled to the player's height and split into head, body, arms and legs.

- Rigged models (Mixamo, Unreal, 3ds Max Biped, Blender and Cesium-style bone names) are split by their skin weights and turn around their real joints.
- Static models are split by measured proportions: the neck, the crotch and the gap between torso and arms.
- **Fix T-Pose** lowers arms modelled in a T-pose or A-pose so they hang like vanilla's.
- Facing comes from the skeleton's shoulders or, for static models, from the direction the toes point. If a character faces sideways or backwards, set **Player Rotation**.

Non-humanoid characters (droids, creatures) still render; their limbs simply swing where the split puts them. Turn off **Animate Limbs** to keep them rigid.

| Setting | Effect |
| --- | --- |
| Player Targets | Self, Friends and/or every other player. |
| Player Scale | Scales the whole player render, including held item, armor and name tag. |
| Hide Armor | Hides vanilla armor, which is shaped for the vanilla body. |

Held items stay at the vanilla hand position, which is close to the model's hand for humanoids of normal proportions.

## Performance

- Parsing, fitting and simplification run on one low-priority background thread; the game thread only uploads the finished model, at most one per 50 ms.
- Each body part and material is one display list with mipmapped textures, so the CPU cost of drawing a character is close to a vanilla player's, whatever its polygon count.
- **Detail Limit** (default 60k triangles) simplifies larger models when they load. Some Sketchfab characters have several hundred thousand or millions of triangles; lower it on weak GPUs or when many players use custom models.
- Characters with more than 1,500 triangles also get a version with a fifth of the triangles (at least 1,500), drawn beyond **LOD Distance** blocks.
- **Texture Size** (default 1024) shrinks textures when they are decoded, so 4K textures never reach the GPU.
- Models that are not drawn for 20 seconds are removed from video memory; disabling the module frees everything.
- Files with more than 3 million triangles, or single files above 768 MB, are rejected with a chat message.

## Commands

| Command | Action |
| --- | --- |
| `.models` | Status, selected models and their credits. |
| `.models list [swords\|players]` | Local models and how many catalogue models are downloadable; with a folder name, every catalogue model. |
| `.models token <token\|clear>` | Set or remove the Sketchfab API token. |
| `.models select <swords\|players> <name>` | Select a model (Tab completes names). |
| `.models download <name>` | Download a catalogue model, including store models your account bought. |
| `.models downloadAll <swords\|players>` | Download every downloadable catalogue model of that kind. |
| `.models import <link> [swords\|players]` | Import a Sketchfab model or collection. |
| `.models reload` | Reload all models and the catalogue. |
| `.models openFolder [swords\|players]` | Open a model folder. |

`.custommodelrenderer <setting> <value>` changes settings like any other module.

## Licenses

Downloaded models keep their Sketchfab license; most in the default collections are CC Attribution, some are CC Attribution-NonCommercial. Vibe writes the name, author, license and source link of each download to `vibe-model.json` in its folder, and `.models` shows the credit of the selected models. Credit the authors when you publish screenshots or videos.

## Checks

`./gradlew verifyCustomModelRendering` renders synthetic knives and characters offscreen with the client's drawing code and writes screenshots to `build/custom-model-render-check/`: the knife over the sword sprite, both in vanilla's first-person transform, and characters in rest, walking, sneaking and bow poses next to the vanilla body. Add your own files with `-PvibeModels="sword:path/knife.glb;player:path/hero.gltf"`.
