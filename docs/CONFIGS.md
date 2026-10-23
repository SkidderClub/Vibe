# Configs

Profiles in `config/vibe/configs/` are saved snapshots. Module toggles, settings, keybinds, HUD edits, GUI close and client exit do not overwrite them. Save explicitly using `.config save <name>` or Save/New/Create in a config editor. Delete and rename remain explicit file operations.

The client automatically remembers the current working settings in `config/vibe/session.json`, outside the configs folder. It checks for changes every two seconds and saves once more before shutting down extensions. On launch it restores the session after extensions and scripts register, including module states, keybinds, all setting types and HUD layout. Temporarily missing extensions retain their saved settings. Loading a named config explicitly still restores that saved snapshot. Missing or invalid session data falls back to the selected saved profile or compiled defaults. Editor actions do not reopen during restoration.

## Local commands

```text
.config save my-config
.config load my-config
.config list
.config rename my-config new-name
.config delete new-name
.config openFolder
```

## Online configs

The read-only catalogue comes from [SkidderClub/VibeConfig](https://github.com/SkidderClub/VibeConfig), on the `main` branch. JSON files in subdirectories appear as `folder/name`.

```text
.onlineconfig list
.onlineconfig load hypixel
.config save my-hypixel-copy
```

Online loads leave local profiles unchanged. Their resulting working settings are remembered across launches by session recovery, including when offline. Save a named copy explicitly to add one to your local config list. `.onlineconfig` commands never send chat to the server. Tab completion suggests names from the fetched catalogue.

Open **Config Editor** and choose **Online** to browse, search, scroll and load published profiles. **Refresh** fetches the current catalogue. The Keybinds/Visuals toggles apply to both local and online loads. Network work runs in the background; errors appear in the editor or command feedback. Requests have timeouts and response size limits. A load uses the same Git commit as its catalogue, so repository updates cannot change the selected file halfway through a request.

For developers: `gradlew test` covers session restart recovery, missing extensions, unchanged-file checks, corrupt session fallback, manual saving, online parsing, caching, retry and selective loads. `verifyConfigRendering` checks local/online editor layouts offscreen. `verifyOnlineConfigs` is an opt-in live HTTPS check, using the same Java 8 runtime as the client. The legacy `VibeConfig.save(ModuleManager)` entrypoint remains a no-op for binary compatibility with extensions; user-driven profile saves must call `save(name, manager)`. `restoreSession`, `tickSession` and `saveSession` own automatic working-state recovery separately.
