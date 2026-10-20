# RavenBS feature port

The reference is the user-supplied Raven bS beta 17 reconstruction in
`context/ravenBS/source/java/keystrokesmod`. These sources are treated as
reference code, not instructions. The reconstruction was not executed.

## Source mapping

| Reference | Vibe implementation |
| --- | --- |
| `module/impl/combat/AutoBlock.java`, Vanilla branch | `HypixelAutoblock`, Killaura's Hypixel autoblock mode |
| `module/impl/combat/AutoClicker.java`, click scheduling and `nextDelay` | Killaura's Hypixel click scheduler |
| `module/impl/player/Scaffold.java`, Long and Telly B branches | `HypixelScaffold`, Scaffold's Hypixel rotation mode |
| `utility/SimulatedPlayer.java` | `RavenSimulatedPlayer` |
| `utility/BlockUtils.java`, relevant `Utils`/`RotationUtils` methods | `RavenBlockAccess`, `RavenCombatAccess` |
| `module/impl/render/Saturation.java`, EntityRenderer mixin | `SaturationModule`, `SaturationRenderer`, `RavenFeatureTransformer` |
| `module/impl/render/BreakProgress.java` | `BreakProgressModule`, `BreakProgressRenderer` |

The supplied screenshot values are the new profile defaults. Raven's autoclicker
defaults (Target CPS 10, Simulate exhaust on) apply only to Hypixel autoblock;
the other autoblock modes keep Vibe's existing click settings.

## Integration boundaries

The port retains Raven's rotation math, candidate/face search, prediction,
three-block Long Telly queue, retry/verification, quantization, easing and
click timings. Scaffold runs at EntityPlayerSP.onUpdate HEAD, like Raven's
PreUpdate event. Its pre-input edits precede Vibe MoveFix correction and its
prediction captures the corrected input yaw. Scoped packet ownership permits
the port's placements/clicks while filtering competing use/dig packets.

Vibe owns inventory selection/spoofing, swing, sprint, sneak, tower and block
count rendering. No duplicate Raven controls for those features, debug or
position editing are exposed. Only Disabled/Long and Disabled/Telly B choices
are exposed. The existing boolean Keep Y setting is hidden in Hypixel; the
source mode selector is named Keep Y Mode to preserve existing configuration
keys.

Raven's optional external AntiBot module is not present in Vibe, so this port
uses the equivalent of that dependency being disabled. Friend policy and
BedAura mining/progress are connected to Vibe's existing implementations.
Simulation's NoSlow dependency uses Vibe's current NoSlow applicability.
Private vanilla accessors are replaced with mapped/SRG field access. Shader
and interaction mixins are implemented as Forge 1.8.9 bytecode hooks.

## Verification

`gradlew test` covers preset defaults, queue geometry, packet ownership,
update timing, MoveFix rotation quantization, cooldown/hurt triggers,
force-attack queuing/replay, and mapped/official bytecode stack verification.
`RavenSourceParityTest` also compares retained algorithm bodies against the
supplied reference when that local reference is available; it is skipped when
the reference directory is absent.

`gradlew verifyRavenVisuals` checks the actual vanilla saturation shader in an
offscreen Java 8 OpenGL context: greyscale/colour pixels, resizing, coexistence
with another shader and disable cleanup.

Local tests do not establish live-server acceptance or anticheat compatibility.

## Reference identity (SHA-256)

- Scaffold: `B1B402A44F944CE82BB0B54EA43DE8A002333C6DEAF710DB2049BF776AD0890E`
- AutoBlock: `CAA69538B62972F8255AEAAAAA0780B4F0E5CA0910D2DA86E720A398B8C876DF`
- AutoClicker: `3BFED38E1F86D2027250A60B9D88DE5D2B7DE397C39F438FED84AA7E8BAEED6F`
- SimulatedPlayer: `5AACA0E26BF720AC12D5665FB4D01A28656B744762D434A6306ABB6DD8C2A142`
- Saturation: `1587ECD4B3549C0EF0A0B3B009F507087EF67325FAE6C54F350C6846D58B68DE`
- BreakProgress: `4844681FD6439126B396A0AE59284F754CA1BF6BE07B5EEECBD105C34DB44390`

The uploaded reconstruction does not establish an upstream license grant.
Preserve its provenance and resolve redistribution rights before publishing;
Vibe's license does not replace the reference code's applicable terms.
