# Vibe extensions

Extensions are optional Java 8 JARs for Forge 1.8.9. Put core Vibe and whichever extensions you want in the same game directory's `mods/` folder, then restart Minecraft. Vibe also checks `mods/1.8.9/`. Extensions need Vibe; they are not standalone Forge mods.

| JAR | Contents |
| --- | --- |
| `Vibe-1.8.9-0.0.6.jar` | Core client and extension API; no LEGO code/assets or Test module. |
| `Vibe-Lego-0.0.6.jar` | All three LEGO modules, 989 characters/costumes, portraits, HUD, sounds, studs and progression. |
| `Vibe-Example-0.0.6.jar` | The existing Test movement module, with yaw/pitch settings and a MoveFix rotation producer. |
| `Vibe-Cosmetica-0.0.6.jar` | Cosmetics, Cosmetics Editor, 629 bundled Cosmetica accessories, previews and friend presets. CustomCosmetics stays in core. |

Install any combination of extensions. Remove the LEGO JAR to remove all three LEGO modules. LEGO progress remains in `vibe/lego/progress.json`; model caches remain in `vibe/lego/models`. Cosmetica presets remain in `config/vibe/cosmetics.json`, friend assignments in `config/vibe/friends.json`, and its caches under `vibe/cosmetica`. Removing Cosmetica hides its editor, modules and friend preset controls; CustomCosmetics and NameProtect still work in core. Extension settings and keybinds use normal Vibe profiles. Saving a profile while an extension is absent preserves its previous entries for reinstallation. No extension files are downloaded automatically.

Use `.extensions` to see loaded extensions and errors; full diagnostics are also in the client log. Keep one copy of each extension, including across both mods folders. Duplicate IDs/entrypoints, incompatible API/client versions, missing entry classes and conflicting module IDs are rejected. Failed initialization removes its modules, listeners, model providers and resource pack, then loading continues. Managed tick callbacks that throw are disabled; direct Forge listeners follow Forge's exception handling.

Extensions run Java code with the client's permissions. Install JARs from authors you trust. Add/remove JARs while Minecraft is closed; extensions load once per launch. Launcher switches can be changed while Minecraft runs; the choice is saved and the JAR is enabled or disabled before the next launch.

## Build the supplied extensions

From the repository root, with JDK 21:

```powershell
.\gradlew.bat build
```

Core, Cosmetica and example JARs are in `build/libs/`, beside the matching public source archive. `exampleExtensionJar` and `cosmeticaExtensionJar` build individual release extensions. LEGO is private: `extensions/lego/` is ignored by Git and excluded from the public source archive. If that folder is present locally, `legoExtensionJar` also builds the separately supplied LEGO JAR; public clones work without it. `-PvibePublicBuild` checks the public build on a developer checkout. Development JARs are in `build/extensions/dev/`; their MCP names are for development launches only. Release JARs are remapped to SRG names for normal Forge installations.

The launcher's **Extensions** page enables Cosmetica by default on a fresh profile; the example Test module starts disabled. Explicit choices and disabled JARs are preserved. Selected public release JARs are built and placed in the profile before the next launch. Use **Add JAR** or drag-and-drop to install a custom extension. Imported JARs appear under their descriptor's name; there are no placeholder cards for private extensions, and they are never provisioned automatically. Toggle an installed extension to rename its JAR with a `.disabled` suffix, then restart the game. Choices and imported JARs live in `run/client` and survive launcher source updates.

The extensions are separate source sets. Core's compile/runtime/test classpaths exclude them. `verifyExtensionPackaging` checks release JAR boundaries and that CustomCosmetics stays in core. With private LEGO sources it also checks the 989 LEGO bundles. `verifyExtensionLoading` checks four public installations, or six installations when LEGO is available, through Forge's classloader and checks event subscriptions, model loading, friend assignments, accessory layers and texture access after resource reloads. It uses offscreen OpenGL. `testCosmeticaExtension` validates all 629 catalog entries and existing friend saves. The optional `verifyLegoRendering` retains the full roster, collection/pass and HUD render checks.

For a persistent development launch, copy the selected development extensions to `run/client/mods/`, then run `gradlew runClient -PvibePersistentRun`. The normal `run.bat` OptiFine launch uses release JARs, so use release extensions there. Never install development and release copies of the same extension together.

## Make your own extension

Copy `extensions/example` into a separate project. It includes a standalone Gradle build with Vibe's Forge/MCP mappings. Change the Java package, extension ID, entrypoint and module name to unique values. Set your extension's own `version` in `build.gradle`.

To build the example independently inside this repository:

```powershell
.\gradlew.bat jar
.\gradlew.bat -p extensions/example build
```

For a copied project, use Gradle 8.8 and the absolute path to Vibe's **development** JAR:

```powershell
gradle build -PvibeJar=C:/path/to/Vibe-1.8.9-0.0.6-dev.jar -PvibeVersion=0.0.6
```

Install the remapped JAR from that project's `build/libs/` beside core Vibe. Keep Vibe/Minecraft/Forge as compile-only dependencies; do not shade them or another copy of the extension API. Bytecode must target Java 8 even when building on JDK 21.

Include `META-INF/vibe-extension.json`:

```json
{
  "apiVersion": 1,
  "id": "my_extension",
  "name": "My extension",
  "version": "1.0.0",
  "vibeVersion": "0.0.6",
  "entrypoint": "example.MyExtension"
}
```

All fields except `vibeVersion` are required. IDs start with a lowercase letter and use lowercase letters, digits, underscores or hyphens. The public entry class implements `VibeExtension` and has a public no-argument constructor. Optional `vibeVersion` pins an exact Vibe build; use it when calling other core APIs. Extensions share Forge's classloader, so use unique package names for your classes and libraries.

```java
package example;

import dev.vibe.extension.ExtensionContext;
import dev.vibe.extension.VibeExtension;
import dev.vibe.module.Category;
import dev.vibe.module.Module;

public final class MyExtension implements VibeExtension {
    @Override public void initialize(ExtensionContext context) {
        Module module = context.registerModule(new Module(
                "My module", "Example extension module", Category.MEME, 0) {});
        context.onTickStart(() -> {
            if (!module.isEnabled()) return;
            // Client tick work goes here.
        });
    }
}
```

Registration runs on the client thread before the active profile loads. Register modules/settings first and let the profile restore their enabled state. Modules automatically appear in ClickGUI, commands, keybinds, ArrayList selection and configuration. `registerEvents(listener)` subscribes to Forge and FML; handlers check the module's enabled state. `onActivity(listener)` receives confirmed Statistics actions for jumps, placements, breaks, beds and kills. `onTickStart(callback)` runs after MoveFix starts its tick and before built-in combat/scaffold rotation producers.

`registerPlayerModel(provider)` supplies a fitted `CompiledModel` to the existing character renderer. Return `null` while loading. The first applicable provider wins, can hide vanilla armor, and uses Minecraft limb animation. Load assets asynchronously, as LEGO does.

`registerService(type, instance)` publishes an optional typed service; `Vibe.getInstance().getExtensions().getService(type)` returns it or `null` when absent. Duplicate service types are rejected, and registrations are removed on initialization failure or cleanup. Core uses `PlayerSkinProvider` for optional skins (NameProtect takes priority) and `FriendProfileProvider` for friend appearance controls. Extension GUI screens can implement `ParticleTargetScreen` to select an existing GUI particle target.

Resources under `assets/<your-domain>/` mount as a resource pack and survive F3+T. Include `pack.mcmeta` for textures. Read other data with your extension class's `getResourceAsStream`. `getDataDirectory()` returns `vibe/extensions/<id>/`; create it when needed. `onShutdown(callback)` saves data or closes audio on exit or initialization failure. Shutdown callbacks may run off the client thread, so do not use OpenGL there.
