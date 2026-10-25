# Vibe Launcher

`VibeLauncher.jar` downloads, manages and starts Vibe. It needs nothing besides a
Java runtime that can open the JAR (Java 8 or newer): Minecraft 1.8.9, Forge,
OptiFine, the libraries, natives and assets, plus the private Java 8 runtime, are
all prepared on the first start. Vibe itself is downloaded as a ready-made JAR
from the GitHub releases of `SkidderClub/Vibe`; no source checkout or Gradle is involved.

## Features

- **Play bar on every page** with the selected account, the game mode, live
  progress (version download, Minecraft setup, game start) and a Stop button.
- **Versions page**: installed builds, every release on GitHub, switching,
  removing, release notes and one **Update to latest** action. The chosen JAR is
  the only Vibe build in `mods/`, so version changes are instant.
- **Game modes**: Vibe (Forge 1.8.9 + OptiFine), GTA7 and GTA8: Los Vibes. The
  city modes open straight after the main menu has loaded.
- **Accounts** from Vibe's encrypted vault, with skin heads. The launcher only
  reads names and UUIDs; refresh tokens are skipped while parsing and the
  decrypted buffer is wiped. *Add account* starts Vibe directly in its Alt
  Manager for Microsoft or offline sign-in. *Automatic* leaves the choice to
  Vibe's Auto Login.
- **Home** with the account's real skin and cape as a rotating 3D model, framed
  by Vibe's default 2D ESP, plus the release notes of the selected version.
- **Mods**: drag `.jar` files anywhere onto the window. Names, versions and
  authors come from `mcmod.info`; mods can be switched off (`.disabled`) or
  removed. OptiFine, Vibe itself, Fabric and newer-Forge mods are refused, mods
  for other Minecraft versions are flagged.
- **Appearance**: the six menu themes are shared with Vibe in both directions.
- **Extensions**: Cosmetica is enabled by default; the example Test module is optional.
  Use **Add JAR** or drag a custom extension JAR onto the window to install it.
  Installed extensions have enable/disable switches and can be removed. Their
  settings and progress remain in the persistent profile when disabled.
- **Settings**: memory, what happens to the window when Minecraft starts,
  automatic version updates, Java 8, folders, import of `options.txt` and
  resource packs from `.minecraft`, repair of the game files and the active
  version.
- **Console** with Minecraft output, highlighted errors and the log files.
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
| `versions/<tag>/` | One downloaded release: `Vibe-1.8.9-<version>.jar`, `release.properties`, `changelog.md`. |
| `minecraft/` | Minecraft 1.8.9, Forge, OptiFine, libraries, natives and assets, prepared once. |
| `minecraft/.stamp` | Marks the prepared game files and their platform/asset index. |
| `source/run/client/` | Vibe's persistent game profile: worlds, options, accounts, mods, crash reports. |
| `source/run/client/mods/` | Your custom mods plus the selected Vibe JAR. |
| `source/run/client/vibe/accounts/` | Account vault (`accounts.vault`) and key (`accounts.key`). |
| `runtime/temurin-8` | Private Java 8 runtime, SHA-256 verified, started with Minecraft. |
| `cache/releases.json` | The last GitHub release list, so versions stay visible offline. |
| `cache/skins/` | Cached skins and capes. |
| `logs/launcher.log`, `logs/game/` | Launcher log and one log per game session. |
| `launcher.properties` | Launcher settings, including the selected version. |

The old `source/` checkout is no longer downloaded or updated; only its
`run/client` folder is reused so worlds, settings and mods stay where they are.
A leftover checkout can be deleted without losing the game profile.

**Versions.** The launcher asks the GitHub API for the releases of
`SkidderClub/Vibe`. A release counts when it has a
`Vibe-1.8.9-*.jar` asset; launcher releases in the same repository are skipped.
Every commit on `main` is published as a pre-release such as `v0.0.6-dev.42`,
which the Versions page lists with a *pre-release* label; *Update to latest*
only picks full releases. The JAR is verified against the asset's SHA-256
(`digest`, otherwise a matching `.sha256` file) and opened to check for
`mcmod.info`, then stored under `versions/<tag>/`. Switching versions removes
the other managed `Vibe-1.8.9-*.jar` files from `mods/` and hard-links (or
copies) the chosen one. Release bodies are shown as notes on the Versions and
Home pages and saved as `changelog.md`.

**Launch.** The launcher prepares Minecraft once: the Mojang version manifest
and 1.8.9 metadata, the client JAR, vanilla libraries, natives and assets, the
Forge installer profile and universal JAR, and OptiFine. It then starts
`net.minecraft.launchwrapper.Launch` directly with Java 8, using Forge's
argument template and `optifine.OptiFineForgeTweaker`. Vibe JARs never enter the
classpath; the selected one is loaded from `mods/` like any Forge mod. Vibe
writes `VIBE_LAUNCH_READY_FILE` when its first screen is drawn, which marks the
game as running. On Java 9+ a restarted launcher reconnects to a running game;
Stop ends Minecraft on every Java version.

**Bridge.** Before each launch the launcher writes
`run/client/vibe/launcher.properties` (`mode` = `vibe`, `gta7`, `gta8` or
`accounts`, plus `selectedUuid`/`selectedName`) and the theme in
`run/client/vibe/menu.properties`. No credentials are ever written.

## Publishing

`.github/workflows/release.yml` publishes everything as releases of `SkidderClub/Vibe`:

| Trigger | Release |
| --- | --- |
| Every push to `main` | Pre-release `v<version>-dev.<run>` (e.g. `v0.0.6-dev.42`) with `Vibe-1.8.9-<version>-dev.<run>.jar` and its `.sha256`. Only the newest 15 are kept. |
| Tag `v<version>` matching `version` in `build.gradle` | Full release; notes come from the `## <version>` (or `## Unreleased`) section of `docs/CHANGELOG.md`. |
| Tag `launcher-v<version>` matching `VibeLauncher.VERSION` | Launcher release with `VibeLauncher.jar` and `VibeLauncher.jar.sha256`. |

To release Vibe, set `version` in `build.gradle`, merge to `main`, then push the
tag (`git tag v0.0.7 && git push origin v0.0.7`). To release the launcher, raise
`VERSION` in `VibeLauncher.java`, merge, then push `launcher-v<version>`.

## Launcher releases

The launcher checks the GitHub releases of `SkidderClub/Vibe` for a newer build.
A release counts when it has an asset named `VibeLauncher.jar` (or
`VibeLauncher-<version>.jar`) and the matching `.sha256` file from
`launcher/build/`; the version comes from the asset name or a tag such as
`launcher-v2.1.0`. Mod releases without these assets are ignored. The update is
downloaded, checked against the checksum and installed by a small helper once the
launcher has exited; the launcher then starts again, even if the replacement failed.

## Troubleshooting

Every error has a code such as `VL-101` (no internet connection) or `VL-610`
(Forge could not be downloaded). It appears in the toast, in the play bar and in
the console, together with a one-line fix; *How to fix* opens its section in
[docs/LAUNCHER_ERRORS.md](../docs/LAUNCHER_ERRORS.md), the full guide to every
code. The hundreds group the cause: 1xx internet, 2xx files, 3xx Java, 4xx Vibe
download, 5xx the older Gradle build, 6xx Minecraft, 7xx launcher, 8xx mods,
900 unexpected.

Crashes are diagnosed from Minecraft's output and the crash report, so they name
the actual cause (out of memory, graphics driver, missing natives, mod conflict,
...). Downloads check the free disk space first, and read timeouts are retried
instead of being treated as a cancel.

- **Crashes or broken game files:** Settings → Troubleshooting → *Repair game files*
  downloads Minecraft, Forge and OptiFine again and keeps versions and the profile.
- **Broken version JAR:** *Reinstall Vibe* downloads the active version again.
- **Java problems:** Settings → Java → *Reinstall*.
- The console's *Launcher log* and *Game log* buttons open the files to attach to a bug report.

New codes are added to `ErrorCode` with an English title and fix, their German translation in `I18n`
and a `### VL-xxx` section in the guide; the launcher tests check all three.
