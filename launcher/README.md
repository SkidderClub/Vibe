# Vibe Launcher

`buildLauncher.bat` produces `VibeLauncher.jar`, a self-contained Java 8-compatible desktop launcher. It has no source-folder setup flow: starting the JAR downloads the Vibe source it needs into `%APPDATA%\VibeLauncher`, then later starts reuse and update that managed copy.

## What it manages

- Vibe's persistent Forge 1.8.9 profile at `run/client`, always launching with the repository's OptiFine build option.
- Drag-and-drop custom Forge mods into the profile `mods` directory.
- The same six Vibe menu themes, persisted in `run/client/vibe/menu.properties`.
- Saved Vibe accounts: the launcher reads only display names and UUIDs from Vibe's AES-GCM account vault and writes a no-secret selection bridge. **Manage in Vibe** opens Vibe's native Alt Manager directly, where Microsoft/offline sign-in remains handled by the client.
- A rotating, animated player preview with cosmetics and Vibe's default 2D ESP layout: white outlined corner box, green left health bar, name, distance, and held item.
- A separate GTA7 launch entry: it opens world selection, then starts Vibe's existing GTA7 mode immediately after a world is entered. The managed source is checked against [`SkidderClub/Vibe`](https://github.com/SkidderClub/Vibe) before every launch. Self-updates are checksum-gated and rolling logs live in `%APPDATA%\VibeLauncher\logs`.

## Build

Run `launcher\buildLauncher.bat` with JDK 8 or newer. It uses `--release 8` and has no external dependencies.

After `VibeLauncher.jar` itself has started, no other manual preparation is needed. It retrieves the Vibe source, preserves the profile's `run` data across source updates, downloads a private Java 8 runtime and JDK 21 if missing, and builds Vibe immediately before launch. The only platform limitation is that Windows must have a Java runtime associated with `.jar` files to start a Java JAR in the first place; the launcher manages the runtimes used for Vibe after that first start.

## Update releases

The default launcher-update repository is `https://github.com/SkidderClub/Vibe`. A GitHub release must have a `.jar` asset and a matching `.sha256` asset. The launcher asks before downloading an update and will not replace its running JAR without the checksum.
