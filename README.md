# Vibe

<img align="right" src="src/main/resources/assets/vibe/branding/ghost-static.png" alt="Vibe ghost mascot" width="120" height="120">

A client-side utility mod for **Minecraft Forge 1.8.9**, with combat and movement
modules, ESP, a customizable HUD, cosmetics, an account manager and local Java scripts.

[Download launcher](https://github.com/SkidderClub/Vibe/releases) · [Discord](https://dsc.gg/vibe-skidder-club) · [Changelog](docs/CHANGELOG.md)

## Launch with Vibe Launcher (recommended)

1. Open [Releases](https://github.com/SkidderClub/Vibe/releases) and download **`VibeLauncher.jar`** from the launcher release's **Assets**.
2. Open it with **Java 8 or newer**. You can double-click the JAR or run `java -jar VibeLauncher.jar` from its download folder.
3. Choose your account and Vibe version, then press **Play**. The launcher downloads the selected client build and prepares Minecraft 1.8.9, Forge, OptiFine and a private Java 8 runtime automatically.

Use the launcher's **Versions** page to switch builds and read their release notes.
Manage optional **Cosmetica cosmetics** and the **Test example** on its **Extensions**
page; Cosmetica is selected by default on a fresh launcher profile. **CustomCosmetics
is included in core Vibe**. See [Launcher details](#launcher-details) and
[Extensions](docs/EXTENSIONS.md) for more information.

### Manual Forge installation

1. Install **Java 8** and **Forge 1.8.9-11.15.1.2318**.
2. Download `Vibe-1.8.9-<version>.jar` from [Releases](https://github.com/SkidderClub/Vibe/releases), or build it from source, and place it in your game directory's `mods/` folder.
3. Launch the Forge profile. Optional extension JARs go in `vibe/extensions-jar/` in the same game directory.

Use modules only where permitted by the server's rules. Vibe is client-only.

## Quick start

- **Right Shift** opens ClickGUI. Change its theme in **Client → ClickGUI → Theme**;
  choices include Skeet, Breeze, Lavalamp, Novoline, Diablo and Wurst.
- **H** opens the HUD editor. Both default keybinds can be changed.
- **Targets → Target Area**, **AutoRespawn**, **AutoBuff** and **Crafter** are covered in [Target areas and inventory automation](docs/AUTOMATION.md).
- **HUD Editor → Ghosts** adds the mascot. Choose **Static** for the single image or **Animated** for the sprite animation; flipping, size, opacity and body color are adjustable.
- **`.help`** lists chat commands; **Tab** completes them.
- **Alt Manager** in the main menu or server list manages accounts.
- **Meme → Phone** raises a phone running VibeOS. See [Phone controls and apps](docs/PHONE.md).
- **Visual → NoHurtCam**, **Client → ToggleSound**, and movable **HUD notifications**
  provide camera and toggle feedback. See [Controls and settings](docs/TOGGLE_FEEDBACK.md).

See the [GTA7 guide](docs/GTA7.md) for the expanded map, controls, upgrades, skins and save backups.
See the [GTA8 guide](docs/GTA8.md) for Los Vibes, an open city game with driving physics, traffic, police, weather and its own shader renderer.
See the [Battlefront 3 guide](docs/BATTLEFRONT3.md) for Star Wars battles, faction armies, credits and upgrades in the Meme category.
See [Custom models](docs/CUSTOM_MODELS.md) for CustomModelRenderer: knives in place of swords, characters
in place of the player model, Sketchfab downloads and your own glTF/GLB/OBJ files.
See [Music and visual effects](docs/MUSIC_AND_EFFECTS.md) for fog, Torus, radio,
the media HUD and audio-reactive waves. Fog is configured under **Visual → Ambience →
Effects → Fog**. Fog and CustomCrosshair also work in GTA7.

## Launcher details

The launcher downloads ready-made Vibe JARs from this repository's
[Releases](https://github.com/SkidderClub/Vibe/releases), keeps downloaded versions
under `versions/` and starts a persistent Forge 1.8.9 profile with OptiFine.
Minecraft, Forge, OptiFine and the private Java 8 runtime are prepared on first use.
The **Versions** page lists stable releases and development pre-releases;
**Update to latest** selects the latest stable client release.

It reads accounts from Vibe's encrypted vault, opens the Alt Manager for new
sign-ins, manages custom mods and extensions, shares menu themes with the client,
can start straight into GTA7 or GTA8, and shows game output in its console.
By default on Windows, its data is stored under `%APPDATA%\VibeLauncher`, with worlds,
settings, accounts and mods in `source\run\client\` beneath that folder.

See [launcher/README.md](launcher/README.md) for features, data locations on other
platforms and release details. [Launcher error codes](docs/LAUNCHER_ERRORS.md)
explain errors such as `VL-101` and their fixes.
Developers can rebuild the launcher with `launcher\buildLauncher.bat` on Windows
or `sh launcher/buildLauncher.sh` on Linux/macOS.

## Build and run from source

Use **JDK 21** to build. The mod targets Java 8; launching Minecraft also requires
a Java 8 runtime.

On Windows, the build and development launch scripts in the project root find JDK 21 automatically.
Run these commands from the project root, or double-click the scripts:

| Command | Purpose |
| --- | --- |
| `.\build.bat` | Build the installable core and public extension JARs plus the matching source archive in `build/libs/`, and run the tests. |
| `.\run.bat` | Build and launch from source with OptiFine. The startup console closes automatically at the first menu; game data stays in `run/client/`. |
| `.\run.bat --keep-console --debug` | Keep the console open for the game session and include Gradle's full debug output. |
| `.\run-fresh.bat` | Launch with a new profile in `run/first-start/session-*/`. |

`run.bat` shows output during startup and closes its console after the Forge
loading screen, once Minecraft has drawn a menu. Logging continues in a timestamped
UTF-8 file in `logs/`. Startup failures keep the console open and include stack
traces. Minecraft also writes `run/client/logs/latest.log`. When invoked from an
existing terminal, the script returns to the prompt. Set `VIBE_NO_PAUSE=1` when invoking the batch files
from another script. `run.bat --dry-run` checks the Gradle task setup without
launching the game.

OptiFine launches use a separate client JAR for each session under `run/launch-artifacts/`.
Rebuilding or cleaning `build/` therefore cannot remove classes that the running game
has not loaded yet. The session copy is removed after a normal exit; a failed launch
can leave its copy for diagnosis. Freecam's production-loading regression check is
`.\gradlew.bat verifyFreecamProduction`.

The development launch scripts use the actual project path without assigning a drive letter.
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
untouched and write screenshots beneath `build/`. `./gradlew tasks --group verification`
lists all of them, for example:

| Task | Checks |
| --- | --- |
| `verifyNeverLoseRendering` | NeverLose controls and clipping. |
| `verifyXanaxRendering` | Xanax rendering, controls, profiles and small-screen layouts. |
| `verifyDiabloRendering` | Diablo panels, controls, fonts, TargetHUD, target marker and blocking poses. |
| `verifyClickGuiReopen` | Saved scroll positions and input cleanup across ClickGUI themes. |
| `verifyChamsRendering` | Chams materials, transparency, partial cover, armor/skin toggles and OpenGL state. |
| `verifyEspRendering` | ESP rendering and the ESP editor. |
| `verifyCustomModelRendering` | CustomModelRenderer knives in first person and characters in several poses. |
| `verifyGta8Rendering` | GTA8 scenes, actors, HUD and a vehicle/character gallery, with frame timings. |
| `verifyRavenScripts -PvibeScriptDir=<folder>` | Compiles external Raven scripts against Vibe's script API. |

## Project layout

| Path | Contents |
| --- | --- |
| `src/main/java/dev/vibe/` | The mod; see the package overview below. |
| `src/main/java/keystrokesmod/` | Raven-compatible script API. The package name is part of the API that scripts compile against. |
| `src/main/resources/assets/vibe/` | Core Vibe assets, including Girlfriend sounds, menu shaders and Waifu presets. |
| `extensions/` | Optional Cosmetica and example modules, assets and tests. |
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
| `ui.clickgui` | The ClickGUI workspaces, themes and setting controls. |
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

The root contains the Windows build and development launch scripts (`build.bat`, `run.bat`, `run-fresh.bat`),
the README and the standard Gradle files (`build.gradle`, `settings.gradle`,
`gradle.properties`, `gradlew`, `gradlew.bat`).

Add bundled assets beneath `src/main/resources/assets/vibe/`. Gradle packages them
automatically and generates preset lists for `shader/`, `waifu/` and the MP3 event
folders in `girlfriend/`. The Cosmetica download helper writes to `extensions/cosmetica/src/main/resources/assets/vibe/cosmetica/` for the optional extension.
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
