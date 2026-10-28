# Seasonal Easter eggs

The core event service uses the machine's local date and time. It has no module,
announcement, toggle notification or entry in the in-game help. Effects run only
with a world loaded. Nothing changes server weather, blocks or packets.

| Date | Effect |
| --- | --- |
| October 31 | Persistent vanilla pumpkin mask in first person, regardless of helmet, including hidden HUD. Hidden in both third-person views. |
| December 24–25 | Native snow precipitation in every biome, even if the server is clear. |
| December 31–January 1, Chinese New Year's Day | Native Minecraft rockets every 6–12 seconds. A 32-rocket display starts at local midnight on January 1 and Chinese New Year's Day. |
| April 1 | Creeper priming sound three blocks behind the player every 10–20 minutes; brief in-game command prompt every 20 minutes; five-second fake Windows crash screen every 30 minutes when fullscreen. |

Chinese New Year uses bundled Gregorian dates for 1901–2100 generated from the
[.NET Chinese lunisolar calendar](https://source.dot.net/System.Private.CoreLib/src/runtime/src/libraries/System.Private.CoreLib/src/System/Globalization/ChineseLunisolarCalendar.cs.html).
The 2027 boundary was checked against the [Hong Kong Observatory](https://www.weather.gov.hk/en/gts/astron2027/files/2027cal02.pdf).
Outside that range, Minecraft's ICU astronomy engine calculates the date with
leap-month handling. This avoids missing ICU locale bundles and the old engine's
near-midnight approximation error in 2027. No runtime network or Windows API is used.
Midnight celebrations are deduplicated by local date. Timers use monotonic time;
disconnects clear transient effects and client-owned rockets.

## Hidden test command

`.e` is absent from command-root completion and `.help`. Explicitly typing `.e `
enables normal Minecraft Tab completion/cycling for the following arguments:

| Argument | Result |
| --- | --- |
| `halloween` | Force the pumpkin mask. |
| `christmas` | Force snow. |
| `newyear`, `chinesenewyear` | Force regular rockets. |
| `midnight` | Start the large firework display immediately. |
| `aprilfools` | Force April behavior and preview a creeper sound immediately, a prompt after two seconds, and a fullscreen-only crash screen after 4.5 seconds. Normal intervals follow. |
| `creeper`, `console`, `crash` | Preview one effect immediately. `crash` requires fullscreen and lasts five seconds. |
| `auto` | Clear previews and return to the actual date. |
| `off` | Clear previews and disable seasonal effects for this session. |

Overrides are session-only. Commands produce no chat output. Individual previews
do not change the calendar override.

## Rendering and lifecycle

- The pumpkin image is read through a fresh vanilla `DefaultResourcePack`, not the
  active resource manager, and uploaded to an owned texture. Resource packs cannot
  substitute the seasonal overlay.
- Only precipitation rendering receives rain/biome/temperature overrides. World
  weather and biome temperatures retain their actual values.
- Fireworks use native `EntityFireworkRocket`, item NBT, trails, sounds and status-17
  bursts. The service supplies the lifetime normally enforced by the server and
  caps active rockets at 12. It removes only its own entities.
- The fake prompt and crash image are rendered into the game framebuffer. The
  suspicious command lines are inert text. No shell, process, OS window, actual
  crash, network request or desktop action is involved.
- Overlay textures are cached and disposed on disconnect. Raw GL state is saved
  and restored without altering Minecraft's cached render state.

`test --tests 'dev.vibe.easter.*'` checks calendar boundaries, scheduling,
fullscreen duration, mapped/release weather bytecode, hidden commands and native
rocket explosions. `verifyHolidayRendering` produces offscreen screenshots in
`build/holiday-render-check/` and verifies pack isolation, texture reuse and GL state.
