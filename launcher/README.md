# Vibe Launcher

`VibeLauncher.jar` installs, updates and starts Vibe. It needs nothing besides a
Java runtime that can open the JAR (Java 8 or newer): the Vibe source, the Java
runtimes for Minecraft and Gradle, Forge and OptiFine are all set up on the first
start.

## Features

- **Play bar on every page** with the selected account, the game mode, live
  progress (download, Gradle stages, Minecraft start) and a Stop button.
- **Game modes**: Vibe (Forge 1.8.9 + OptiFine), GTA7 and GTA8: Los Vibes. The
  city modes open straight after the main menu has loaded.
- **Accounts** from Vibe's encrypted vault, with skin heads. The launcher only
  reads names and UUIDs; refresh tokens are skipped while parsing and the
  decrypted buffer is wiped. *Add
  account* starts Vibe directly in its Alt Manager for Microsoft or offline
  sign-in. *Automatic* leaves the choice to Vibe's Auto Login.
- **Home** with the account's real skin and cape as a rotating 3D model, framed
  by Vibe's default 2D ESP, plus the Vibe changelog and the latest commits.
- **Mods**: drag `.jar` files anywhere onto the window. Names, versions and
  authors come from `mcmod.info`; mods can be switched off (`.disabled`) or
  removed. OptiFine, Vibe itself, Fabric and newer-Forge mods are refused, mods
  for other Minecraft versions are flagged.
- **Appearance**: the six menu themes are shared with Vibe in both directions.
- **Settings**: memory, what happens to the window when Minecraft starts,
  automatic updates, Java runtimes, folders, import of `options.txt` and resource
  packs from `.minecraft`, build-cache and source repair.
- **Console** with Gradle and Minecraft output, highlighted errors and the log files.
- English and German interface (follows Vibe's language, then the system).

## Build

Run `launcher\buildLauncher.bat` on Windows or `launcher/buildLauncher.sh` on
Linux and macOS with JDK 11 or newer (JDK 21 recommended). Both compile the
sources for Java 8, run the tests in `test/` and write:

- `launcher/VibeLauncher.jar`
- `launcher/build/VibeLauncher.jar.sha256`

Pass `--skip-tests` to package without testing. There are no external dependencies.

## How it works

Everything lives in the launcher's data folder: `%APPDATA%\VibeLauncher` on
Windows, `~/Library/Application Support/VibeLauncher` on macOS and
`~/.local/share/VibeLauncher` on Linux.

| Path | Contents |
| --- | --- |
| `source/` | Managed checkout of `SkidderClub/Vibe` (`main`). |
| `source/run/client/` | Vibe's persistent game profile: worlds, options, accounts, mods. |
| `runtime/temurin-8`, `runtime/temurin-21` | Private Java runtimes, SHA-256 verified. |
| `logs/launcher.log`, `logs/game/` | Launcher log and one log per game session. |
| `cache/skins/` | Cached skins and capes. |
| `launcher.properties` | Launcher settings. |

**Updates.** Before a launch (or on start, with automatic updates enabled) the
launcher asks GitHub for the newest commit. Small updates download only the
changed files; large or rewritten histories download the full source archive,
which replaces the checkout in one move. `run/` and the cached OptiFine JAR are
carried over, so the profile is never touched.

**Launch.** The launcher runs `gradlew runClient -PvibeOptifine -PvibePersistentRun
-PvibeMaxMemory=<MB>` with JDK 21 for Gradle and Java 8 for Minecraft, passed as
toolchains through environment variables. Output goes to a log file that the
console follows, so Minecraft keeps running if the launcher is closed. Vibe writes
`VIBE_LAUNCH_READY_FILE` when its first screen is drawn, which marks the game as
running. On Java 9+ a restarted launcher reconnects to a running game.

**Bridge.** Before each launch the launcher writes
`run/client/vibe/launcher.properties` (`mode` = `vibe`, `gta7`, `gta8` or
`accounts`, plus `selectedUuid`/`selectedName`) and the theme in
`run/client/vibe/menu.properties`. No credentials are ever written.

## Launcher releases

The launcher checks the GitHub releases of `SkidderClub/Vibe` for a newer build.
A release counts when it has an asset named `VibeLauncher.jar` (or
`VibeLauncher-<version>.jar`) and the matching `.sha256` file from
`launcher/build/`; the version comes from the asset name or a tag such as
`launcher-v2.1.0`. Mod releases without these assets are ignored. The update is
downloaded, checked against the checksum and installed after the launcher exits.

## Troubleshooting

- **The build fails after an update:** Settings → Troubleshooting → *Clear build cache*.
- **Broken or edited source:** *Reinstall Vibe* downloads it again and keeps the profile.
- **Java problems:** Settings → Java → *Reinstall*.
- The console's *Launcher log* and *Game log* buttons open the files to attach to a bug report.
