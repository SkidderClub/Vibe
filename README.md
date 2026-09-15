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

In **ESP → ESP Modes**, select **Chams** on its own or alongside 2D/3D.
**Invisible** controls the parts of a model hidden behind geometry; **Visible**
controls the exposed parts. Each has independent **Armor**, **Show Skin**,
**Flat / Glow / Metallic**, and color settings. Show Skin retains the texture
under the material tint. The color's alpha controls opacity in all three
materials, including fully transparent at zero. Chams uses the Targets filters.

NeverLose uses two columns of settings cards (one in small windows). Click a
card title to collapse it, use its switch to enable the module, and click the
three dots or middle-click the card header to assign a key. **Ctrl+F** searches
modules and settings across categories. The top-left config selector loads a
saved profile; dropdowns and the module area scroll independently.
Drag the bottom-right corner to resize the window; its size is remembered when
you close and reopen ClickGUI.

Xanax uses category tabs, a module list and a separate settings panel with red
sliders. Left-click a module to toggle it, right-click to select its settings,
and middle-click to assign a key. Both panels scroll independently; drag either
window by its top border and resize it at the bottom-right corner. Both window
sizes are remembered when you reopen ClickGUI. The config window loads, saves, creates and
deletes local profiles (click Delete twice to confirm). Its Keybinds checkbox
controls whether loading a profile replaces bindings. On narrow screens, use
**Configs** at the bottom right to open that window.

See the [GTA7 guide](docs/GTA7.md) for the expanded map, controls, upgrades, skins and save backups.
See [Music and visual effects](docs/MUSIC_AND_EFFECTS.md) for Fog, Torus, radio,
the media HUD and audio-reactive waves. Fog and CustomCrosshair also work in GTA7.

## Build and run

Use **JDK 21** to build. The mod targets Java 8; launching Minecraft also requires
a Java 8 runtime.

On Windows, the launchers in the project root find JDK 21 automatically.
Run these commands from the project root, or double-click the scripts:

```powershell
.\build.bat
```

The installable JAR is written to `build/libs/Vibe-1.8.9-<version>.jar`.

`./gradlew verifyNeverLoseRendering` checks NeverLose controls and clipping in an
offscreen OpenGL context, writing screenshots to `build/neverlose-render-check/`.
`./gradlew verifyXanaxRendering` checks Xanax rendering, controls, profiles and
small-screen layouts, writing screenshots to `build/xanax-render-check/`.
`./gradlew verifyChamsRendering` checks Chams materials, transparency, partial
cover, armor/skin toggles and OpenGL state, with a preview in `build/chams-render-check/`.

| Command | Purpose |
| --- | --- |
| `.\run.bat` | Build and launch with OptiFine; keep data in `run/client/`. |
| `.\run-fresh.bat` | Launch with a new profile in `run/first-start/session-*/`. |

With `JAVA_HOME` set to JDK 21, use `./gradlew build` on Linux/macOS or
`.\gradlew.bat build` on Windows. The first build or launch downloads dependencies.

## Project layout

| Path | Contents |
| --- | --- |
| `src/main/java/` | Java source code. |
| `src/main/resources/` | Mod metadata and bundled resources. |
| `src/main/resources/assets/vibe/` | Vibe assets, including cosmetics, Girlfriend sounds, menu shaders and Waifu presets. |
| `src/main/resources/assets/minecraft/` | Assets loaded through the Minecraft resource namespace. |
| `src/test/` | Tests and test resources. |
| `tools/` | Helper scripts and additional Gradle tasks. |
| `docs/` | Changelog and feature guides. |
| `LICENSES/` | License texts and attribution. |
| `gradle/` | Gradle wrapper files. |
| `build/`, `.gradle/`, `run/` | Generated build output, local caches and game data; ignored by Git. |

The root contains the Windows launchers (`build.bat`, `run.bat`, `run-fresh.bat`),
the README, Git ignore rules and standard Gradle build configuration
and launchers (`build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`).

Add bundled assets beneath `src/main/resources/assets/vibe/`. Gradle packages them
automatically and generates preset lists for `shader/`, `waifu/` and the MP3 event
folders in `girlfriend/`. The Cosmetica download helper writes to `cosmetica/` here too.

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
