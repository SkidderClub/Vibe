# Configs

Profiles in `config/vibe/configs/` are saved snapshots. Module toggles, settings, keybinds, HUD edits, GUI close and client exit do not overwrite them. Save explicitly using `.config save <name>` or Save/New/Create in a config editor. Unsaved edits last for the current session. Loading an existing local profile remembers which saved snapshot to restore next time; it does not rewrite that snapshot. Delete and rename remain explicit file operations.

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

Online loads apply in memory and leave local profiles unchanged. They do not install or update a local config. Save a copy explicitly to keep it across launches. `.onlineconfig` commands never send chat to the server. Tab completion suggests names from the fetched catalogue.

Open **Config Editor** and choose **Online** to browse, search, scroll and load published profiles. **Refresh** fetches the current catalogue. The Keybinds/Visuals toggles apply to both local and online loads. Network work runs in the background; errors appear in the editor or command feedback. Requests have timeouts and response size limits. A load uses the same Git commit as its catalogue, so repository updates cannot change the selected file halfway through a request.

For developers: `gradlew test` covers manual saving, online parsing, caching, retry and selective loads. `verifyConfigRendering` checks local/online editor layouts offscreen. `verifyOnlineConfigs` is an opt-in live HTTPS check, using the same Java 8 runtime as the client. The legacy `VibeConfig.save(ModuleManager)` entrypoint remains a no-op for binary compatibility with extensions; user-driven saves must call `save(name, manager)`.
