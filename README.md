# Vibe

A client-side utility mod for **Minecraft Forge 1.8.9**, with combat and movement
modules, ESP, a customizable HUD, cosmetics, an account manager and local Java scripts.

[Discord](https://dsc.gg/vibe-skidder-club) · [Changelog](docs/CHANGELOG.md)

## Install

1. Install **Java 8** and **Forge 1.8.9-11.15.1.2318**.
2. Copy the built `Vibe-1.8.9-<version>.jar` into your game directory's `mods/` folder.
3. Launch the Forge profile.

Use modules only where permitted by the server's rules. Vibe is client-only.

## Quick start

- **Right Shift** opens ClickGUI; choose Skeet, Futuristic, NeverLose, Augustus or Xanax.
- **H** opens the HUD editor. Both default keybinds can be changed.
- **`.help`** lists chat commands; **Tab** completes them.
- **Alt Manager** in the main menu or server list manages accounts.

See the [GTA7 guide](docs/GTA7.md) for the expanded map, controls, upgrades, skins and save backups.
See the [GTA8 guide](docs/GTA8.md) for Los Vibes, a realistic open city with driving physics, traffic, police, weather and its own shader renderer.
See the [Battlefront 3 guide](docs/BATTLEFRONT3.md) for Star Wars battles, faction armies, credits and upgrades in the Meme category.
See [Custom models](docs/CUSTOM_MODELS.md) for CustomModelRenderer: knives in place of swords, characters
in place of the player model, Sketchfab downloads and your own glTF/GLB/OBJ files.
See [Music and visual effects](docs/MUSIC_AND_EFFECTS.md) for Fog, Torus, radio,
the media HUD and audio-reactive waves. Fog and CustomCrosshair also work in GTA7.

## Build and run

Use **JDK 21** to build. The mod targets Java 8; launching Minecraft also requires
a Java 8 runtime.

On Windows, the launchers in the project root find JDK 21 automatically.
Run these commands from the project root, or double-click the scripts:

| Command | Purpose |
| --- | --- |
| `.\build.bat` | Build the installable JAR, `build/libs/Vibe-1.8.9-<version>.jar`, and run the tests. |
| `.\run.bat` | Build and launch with OptiFine; close the startup console when Minecraft's first menu appears. Keep data in `run/client/`. |
| `.\run.bat --keep-console --debug` | Keep the console open for the game session and include Gradle's full debug output. |
| `.\run-fresh.bat` | Launch with a new profile in `run/first-start/session-*/`. |

`run.bat` shows output during startup and closes its console after the Forge
loading screen, once Minecraft has drawn a menu. Logging continues in a timestamped
UTF-8 file in `logs/`. Startup failures keep the console open and include stack
traces. Minecraft also writes `run/client/logs/latest.log`. When invoked from an
existing terminal, the script returns to the prompt. Set `VIBE_NO_PAUSE=1` when invoking the batch files
from another script. `run.bat --dry-run` checks the Gradle task setup without
launching the game.

The launchers use the actual project path without assigning a drive letter.
On its first start, `run.bat` copies `options*.txt` and resource packs from
`%APPDATA%\.minecraft` into `run/client/` once, without overwriting existing files.
After that, Vibe keeps its own settings in its game directory.

With `JAVA_HOME` set to JDK 21, use `./gradlew build` on Linux/macOS or
`.\gradlew.bat build` on Windows. The first build or launch downloads dependencies.
`runClient` accepts `-PvibePersistentRun`, `-PvibeFreshRun`, `-PvibeOptifine` and
`-PvibeMaxMemory=<MB>`; see [gradle/client-run.gradle](gradle/client-run.gradle).

## Checks

`build` runs the unit tests. Rendering checks are separate because they need a real
OpenGL driver: they draw offscreen, open no Minecraft window, leave player profiles
untouched and write screenshots to `build/<check>/`. `./gradlew tasks --group verification`
lists all of them, for example:

| Task | Checks |
| --- | --- |
| `verifyNeverLoseRendering` | NeverLose controls and clipping. |
| `verifyXanaxRendering` | Xanax rendering, controls, profiles and small-screen layouts. |
| `verifyChamsRendering` | Chams materials, transparency, partial cover, armor/skin toggles and OpenGL state. |
| `verifyEspRendering` | ESP rendering and the ESP editor. |
| `verifyCustomModelRendering` | CustomModelRenderer knives in first person and characters in several poses. |
| `verifyGta8Rendering` | GTA8 scenes, actors, HUD and a vehicle/character gallery, with frame timings. |
| `verifyRavenScripts -PvibeScriptDir=<folder>` | Compiles external Raven scripts against Vibe's script API. |

## Desktop launcher

`launcher/VibeLauncher.jar` is the easiest way to play: start it and press **Play**.
It downloads Vibe from `SkidderClub/Vibe` (later updates fetch only changed files),
installs private Java 8/21 runtimes, builds Vibe and starts the persistent Forge 1.8.9
profile with OptiFine. It picks the account from Vibe's encrypted vault, opens the
Alt Manager for new sign-ins, manages custom mods, shares the menu themes, can start
straight into GTA7 or GTA8, and shows the build and game output in its console.
Rebuild it with `launcher\buildLauncher.bat` (Windows) or `launcher/buildLauncher.sh`.
See [launcher/README.md](launcher/README.md) for details, data locations and releases.

## Project layout

| Path | Contents |
| --- | --- |
| `src/main/java/dev/vibe/` | The mod; see the package overview below. |
| `src/main/java/keystrokesmod/` | Raven-compatible script API. The package name is part of the API that scripts compile against. |
| `src/main/resources/assets/vibe/` | Vibe assets, including cosmetics, Girlfriend sounds, menu shaders and Waifu presets. |
| `src/main/resources/assets/minecraft/` | Assets loaded through the Minecraft resource namespace. |
| `src/test/` | Unit tests and the offscreen rendering checks. |
| `launcher/` | The standalone Vibe Launcher, a separate Java 8 Swing application. |
| `gradle/` | Build logic applied by `build.gradle` (client runs, OptiFine, licenses, checks) and the Gradle wrapper. |
| `tools/` | Helper scripts: `run.bat` launch workers, asset and language catalog generators. |
| `docs/` | Changelog and feature guides. |
| `LICENSES/` | License texts and attribution. |
| `build/`, `.gradle/`, `run/`, `logs/` | Generated build output, local caches and game data; ignored by Git. |

| Package (`dev.vibe.`) | Contents |
| --- | --- |
| `Vibe`, `BuildInfo` | Forge entry point; `BuildInfo` is generated from the version in `build.gradle`. |
| `module`, `module.impl.<category>` | Module base classes and the modules, one package per ClickGUI category: `combat`, `visual`, `movement`, `world`, `meme`, `client`. |
| `setting` | Module setting types. |
| `ui` | Shared rendering and GUI helpers. |
| `ui.clickgui` | The ClickGUI and its themes (Skeet, NeverLose, Xanax, Augustus). |
| `ui.screen` | Editor and tool screens opened by modules (ESP, config, keybinds, cosmetics, scripts, ...). |
| `ui.menu`, `ui.account` | Main menu, first-start and license screens; Alt Manager screens and skin heads. |
| `ui.render`, `ui.render.esp`, `ui.effect` | In-world and overlay renderers, ESP/Chams, and shader effects. |
| `ui.game` | Screens of the Meme games (GTA7, GTA8, Battlefront 3, arcade, NES). |
| `game.*` | Game logic of the Meme games, independent of the screens. |
| `hud` | HUD elements and the HUD editor. |
| `combat`, `movement`, `input`, `inventory`, `network`, `target` | Logic shared by modules: rotations, ranges, pathfinding, clicks, packets. |
| `model` | CustomModelRenderer's glTF/GLB/OBJ loaders, knife and character fitting, GPU upload and Sketchfab downloads. |
| `core` | Forge core plugin and ASM transformers. They reference hook classes by name, so keep those strings in sync when moving classes. |
| `account`, `config`, `command`, `event`, `language`, `media`, `script`, ... | Services created by `Vibe` at startup. |

The root contains the Windows launchers (`build.bat`, `run.bat`, `run-fresh.bat`),
the README and the standard Gradle files (`build.gradle`, `settings.gradle`,
`gradle.properties`, `gradlew`, `gradlew.bat`).

Add bundled assets beneath `src/main/resources/assets/vibe/`. Gradle packages them
automatically and generates preset lists for `shader/`, `waifu/` and the MP3 event
folders in `girlfriend/`. The Cosmetica download helper writes to `cosmetica/` here too.
Libraries shaded into the JAR must be Java 8 bytecode: Minecraft 1.8.9 runs on Java 8,
and Forge cannot read newer class files.

## License and credits

Vibe combines code under [GPLv3](LICENSES/GPL-3.0.txt) with Schizoid-derived Fog, Torus and
media HUD components under AGPLv3. The [license and attribution guide](LICENSES/THIRD_PARTY_NOTICES.md)
explains the component terms, source-distribution duties and open release issues.
License texts and provenance are collected in `LICENSES/`; known gaps are listed
directly in the guide. `build` checks local license-document links and preserves
bundled libraries' embedded notices separately, with an inventory of the resolved
JARs under `META-INF/vibe/dependencies/INDEX.md`.

Open **Licenses & credits** in the main menu or pause menu to read the credits
and license texts offline. `.source` identifies the matching source archive;
distribute the build's `-sources.zip` alongside the JAR.
