# Vibe ghost artwork

Runtime assets live in `src/main/resources/assets/vibe/branding/`:

- `ghost-static.png`: trimmed, transparent mascot, padded 512px square.
- `ghost-animation.png`: 15 original poses in a padded 4x4 atlas, 256px per cell, 12fps by default. The unused last cell is transparent. Every pose uses the same crop and scale to prevent animation size jitter.
- `icon-16.png`, `icon-32.png`, `icon-48.png`, `icon-256.png`: native window/taskbar icon resolutions.
- `ghost-animation.properties`: animation dimensions and frame rate.

The uploaded originals were moved and renamed, without discarding their full resolution:

- `Cheerful Floating Ghost Mascot.png` → `ghost-mascot-original.png`
- `Cheerful Ghost Cloth Animation Sprites.png` → `ghost-animation-original.png`

The static upload already had transparency. The animation upload had an opaque checkerboard. The built-in imagegen tool was used in transparent background editing mode to extract a mask, saved as `ghost-animation-cutout.png`. Its instruction was to remove the checkerboard, preserve all 15 supplied poses in their original 5x3 positions and size, and not redesign or add frames. The deterministic preparation tool then used that mask as a guide, recovered the original painted pixels and dark outlines, removed detached edge artifacts, trimmed and packed the artwork. No newly invented animation poses are used.

Regenerate with JDK 21 (from the project root):

```powershell
java -Djava.awt.headless=true tools/PrepareGhostAssets.java artwork/ghost/ghost-mascot-original.png artwork/ghost/ghost-animation-cutout.png src/main/resources/assets/vibe/branding artwork/ghost/ghost-animation-original.png
```

The loading renderer uses the real `ProgressManager` stage names, steps and messages. It replaces artwork inside Forge's splash loop, preserving Forge's display semaphore and context ownership. When that splash is disabled, the main-thread fallback presents during real progress updates instead. It does not force Forge/OptiFine splash compatibility or alter the user's splash configuration. [Forge 1.8.9 splash implementation](https://github.com/MinecraftForge/MinecraftForge/blob/1.8.9/src/main/java/net/minecraftforge/fml/client/SplashProgress.java).
