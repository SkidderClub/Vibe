# Cosmetica extension

Install `Vibe-Cosmetica-0.0.6.jar` beside the updated core Vibe JAR in `mods/`, then restart. It provides **Cosmetics** (Visual), **Cosmetics Editor** (Client), friend preset assignments, skin previews and the bundled 629-entry Cosmetica catalog.

**CustomCosmetics remains in core Vibe** with its existing settings and rendering. This extension contains no CustomCosmetics code.

Existing saves keep their paths: presets in `config/vibe/cosmetics.json`, friend assignments in `config/vibe/friends.json`, and downloaded model/texture caches under `vibe/cosmetica/`. Removing the extension leaves these files intact and hides its modules and friend preset controls. Vibe profiles retain the removed modules' settings for reinstallation.

Build from the repository root with `gradlew cosmeticaExtensionJar`, or build all JARs with `gradlew build`. The catalog dump/thumbnail tools now write to this extension's resources folder. The original catalog metadata, source models, textures and preview images are preserved. Upstream UI notices are in `LICENSES/`; creator asset provenance remains in the catalog and the repository's third-party notices.
