# Rewound modes

Seven selectable modes port the branches selected by the user-supplied Exire
`ncp.json`. Existing Vibe modes and saved configurations keep their defaults.

| Vibe setting | Selected Exire implementation |
| --- | --- |
| Killaura → Autoblock Mode → Rewound | `KillAuraNcpAutoBlock`, plus `IAutoBlock.unblock` |
| Speed → Mode → Rewound | `NcpModesSpeed`, `SpeedMode.REWOUND` |
| NoSlow → Mode → Rewound | `NcpNoSlowDown`, filtered by `NoSlowDownModule.shouldNoSlowDown` |
| NoFall → Mode → Rewound | `VanillaNoFall` |
| Step → Mode → Rewound | `NcpStep` and `StepModule`'s Speed pause |
| AntiVoid → Mode → Rewound | `NcpAntiVoid` and `VoidUtil` |
| Criticals → Mode → Rewound | `UpdatedNcpCriticals` |

## Behavior and integration

Speed evaluates the original staged movement vector at the local player's
`moveEntity` boundary, before vanilla resolves collisions. The initial hop uses
`motionY = 0.407 + jumpBoost * 0.1`; stage speeds, repeated-hop reduction,
half-block stair handling, collision recovery, potion scaling and setback
cooldowns follow the selected branch. It changes the requested X/Z displacement,
as Exire's move event does, rather than overwriting vanilla horizontal velocity.
Native collision resolution remains responsible for the result.
The source's jump-action cancellation is applied at Vibe's sampled-input hook,
so holding the jump key does not add a second vanilla impulse to the owned hop.

`Use timer` defaults to **on** (1.2 while unimpeded), and `Fast fall` defaults to
**Off**, matching the uploaded config. **Snap** and **Push** retain the source's
optional stage-five falling behavior. The config's `Damage boost` belongs to the
separate Updated NCP speed branch and has no effect in Rewound; it is not added
as a misleading control here. Timer ownership and movement state are released
when switching modes, disabling Speed, changing players or leaving the world.

Killaura's Rewound branch attacks in the post-walking phase selected by the
uploaded config: native swing, native controller attack, then the source's held
item C08 block packet. Leaving block range or stopping aura releases the owned
block with C07. Vibe's existing targeting, reach, rotations, click settings and
other modules' action reservations still apply. It does not force the local use
key or queue an additional input-phase attack.

NoSlow preserves normal movement input for selected items. While sword-blocking
(including the Rewound aura's server-side block), it sends C07 release before
the native walking update and C08 reblock afterward. Food and bow use preserve
input without sending sword-block packets; splash potions are excluded, as in
the source. Pending reblocks are discarded if the held stack, slot, player or
mode changes.

NoFall marks the walking update grounded only when `fallDistance > 2.0`, then
resets fall distance to zero. All native C03 variants read that scoped flag;
the player's physical `onGround` field is never modified. The flag is cleared
on both normal and exceptional exits.

The correction hook runs after Minecraft's native packet thread gate. Exire's
300 ms look-preservation cooldown is retained; relative rotation flags receive
zero offsets to preserve the current camera without adding its angle twice.
Position and relative-position flags are unchanged.

Step uses the source's fixed one-block height, **Timer off**, **Delay 150 ms**,
and **Speed check off**, matching the config. Its pre/post hooks run at the
actual collision-step sites inside `Entity.moveEntity`. The post-step C04
offsets are `(double) 0.42F` and `0.7531999805212024`, based on the original
position before native `resetPositionToBB`. Normal jumping, LongJump, and an
owned AutoBuff action prevent the elevated step. The parent Step implementation's
look-ahead and short Speed pause are also included, so Rewound Speed can yield
its hop when a full-block step is ahead. The optional timer slows to 0.5 and
restores on the following pre-update; the source's delay reset counter is
preserved, including its reset on the next pre-update as well. With Timer off,
the source measures delay from activation rather than resetting after each step.

AntiVoid exposes only the selected NCP implementation as **Rewound**, with a
default **Fall distance of 6**. It tracks vertical distance from the last
grounded Y independently of vanilla fall-distance resets. The source's loaded
chunk columns, solid/liquid support checks, 0.3-block footprint, and twenty-tick
gravity/drag prediction determine whether a fall is over void. Unknown chunks
count as supported. Once the threshold is exceeded, and flight is not allowed,
it sends a C04 at the current X/Z and **current Y + 100**, grounded false.
The exact packet bypasses Vibe's mutation/delay/script hooks, matching
`PacketUtil.sendNoEvent`; local position and velocity are not changed. State is
cleared when disabled or changing player/world.

Criticals counts grounded native pre-updates. After three grounded ticks, if
the block under the player is not air, a slab or stairs, it forces a position
packet with ground false and adds `1.7E-14` on even ticks or `1E-17` on odd ticks
to packet Y. Position is forced even when the epsilon rounds away, as it can
at ordinary world heights. Physical position, bounding box and ground state
are unchanged. Airborne ticks reset the counter. The uploaded config's Packet
delay setting belongs to a different Criticals mode and is not added here.

## Source reference

Only the needed classes and helpers were decompiled with CFR 0.152 and checked
against their bytecode. The supplied artifact retains readable class/method
names; no string decryption or runtime execution of Exire was needed. The
Exire executable and decompiler output remain in ignored local directories.

- Exire JAR SHA-256:
  `4336cae3ac816f42d47a442c35071f8d7697494d957a4f6f9e28dafdb94caed7`
- Config SHA-256:
  `4081a55b40f4081a614fcf9f5de966aef1edf55a67079b8d85831f0ad57a6df9`
- Relevant helpers: `MovementUtil.strafe`, `PlayerUtil.isOnGround`,
  `Stopwatch`, and the pre/post network, move and post-step event sites.

## Additional Exire modules

**Jesus** (Movement) and **Disabler** (Client) expose only **Rewound**, ported
from Exire's Watchdog branches. Jesus changes the native move vector while in
water, airborne, with neither jump nor sneak held: Y is `(double) 1E-5F`, and
horizontal speed is 0.12 scaled by speed/slowness potions. Ground, jump and sneak
retain native movement. Disabler queues C0F confirmations and replays them FIFO
immediately before the next C03, bypassing interception of the replay. Keepalive
packets are unchanged. Disabling flushes pending confirmations; a new
player/world or the source's zero-tick reset discards the old queue.

**KeepSprint** (Combat) ports the native successful-hit slowdown and sprint-reset
controls, both enabled by default. The source's optional setting named "Only in
air" actually returns while airborne. Vibe labels that behavior **Only on
ground**, retaining the source condition and its default off. Hooks affect only
the local player's native attack; other entities and disabled behavior retain
the original factors and sprint-reset call.

**Killaura → Target Amount** defaults to **1**, retaining the single-target ray
and native click path. Values 2–20 extend that same controller attack into a
burst of distinct eligible targets, including those outside the look direction.
Target filters, configured reach/clicking range, line-of-sight policy and target
priority still apply. The burst stays inside the original hotbar action scope,
so native attack events, utility switching and autoblock order remain in use.

Speed suspends every mode for **four player ticks after exiting liquid**, in
addition to its existing liquid suspension. Repeated hooks during the same
tick do not consume the delay. Enabling Custom Speed during that interval also
leaves velocity alone.

**Flag Detector → Stop Movement** defaults off. With it on, **Ticks** (default
20) suspends Movement-category modules and Scaffold after a detected nearby
correction. Their owned rotations, timers, input and leases are released, then
their runtime state is initialized again when the interval expires. Saved
enable states remain intact; manually disabling a paused module keeps it off.
The flag is observed once after the native packet thread gate. Repeated flags
restart the interval; disabling the detector/option or changing worlds releases
the pause.

Radar **Map** mode now draws terrain with full alpha. Void, air and unloaded
columns have zero-alpha pixels, and the map omits the filled theme underlay so
those pixels remain transparent. Its optional outline and entity markers remain.

## Additional verification

Tests exercise source water vectors/potion scaling and manual-key guards, the
four-tick exit boundary for every Speed mode, Netty transaction FIFO/reset/flush,
exact flag suspension/resume and user disable, and a native 20-target controller
burst with a single-target fallback. KeepSprint bytecode tests check the real
mapped and official Forge attack sites, stack verification and idempotence.
The production probe verifies KeepSprint and multi-aura hooks in the packaged
Forge/OptiFine classes. Radar sampling tests check opaque terrain and transparent
void/unloaded columns.

`RewoundModesTest` exercises reference hop values, potion boosts, setback
cooldowns, relative look flags, optional fast fall, collision/stair handling,
timer cleanup, item filters, and actual transformed native walking packets.
It checks post-hit swing/attack/block order and the following NoSlow sequence.
Disabled native walking traces must match the unmodified writer byte for byte.
`RewoundExtraModesTest` covers the Step offsets/delay/timer/Speed interaction,
Criticals' forced native packets and excluded block types, AntiVoid's independent
ground history and void prediction, recovery packet bypass, and config persistence.

`RewoundHooksTest` analyzes mapped and obfuscated Forge bytecode, checks native
thread-gate ordering and verifies repeat transformation does not duplicate hooks.
The headless production probe checks the final Forge/OptiFine classes as well.
These are local implementation checks; no live Rewound server test is implied.
