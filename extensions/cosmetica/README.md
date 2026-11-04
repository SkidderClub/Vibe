# Cosmetica extension

Install `Vibe-Cosmetica-0.0.6.jar` beside the updated core Vibe JAR in `mods/`, then restart. It provides **Cosmetics** (Visual), **Cosmetics Editor** (Client), friend preset assignments, skin previews and an offline catalog of 1,318 accessories and 419 capes as of 3 November 2026.

**CustomCosmetics remains in core Vibe** with its existing settings and rendering. This extension contains no CustomCosmetics code.

Existing saves keep their paths: presets in `config/vibe/cosmetics.json`, friend assignments in `config/vibe/friends.json`, and downloaded model/texture caches under `vibe/cosmetica/`. Removing the extension leaves these files intact and hides its modules and friend preset controls. Vibe profiles retain the removed modules' settings for reinstallation.

Accessories follow the local Minecraft 1.8.9 model, including its sneaking translation and slim arm pivots. Capes use 1.8.9's cape model and respect the Cape toggle under Skin Customization. The catalog includes entries that supply a cloak texture. Elytra-only entries require a newer Minecraft version.

Build from the repository root with `gradlew cosmeticaExtensionJar`, or build all JARs with `gradlew build`. Run `gradlew testCosmeticaExtension verifyExtensionLoading` to check assets and the native extension layer. Authored models and texture sheets remain in the extension. Large sheets use resized render copies that retain the original frame count, and newer catalog previews are stored as compact PNGs. Upstream UI notices are in `LICENSES/`. Creator asset provenance remains in the catalog and the repository's third-party notices.

To refresh the offline catalog, run `pwsh -File tools/dump-cosmetica-assets.ps1 -RefreshCatalog` from the repository root with Python and Pillow available. The importer merges the public results into the existing snapshot.
