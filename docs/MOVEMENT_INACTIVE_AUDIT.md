# Movement with modules disabled

The Raven Scaffold integration was reviewed together with its shared movement hooks. The local regression checks found these problems:

- **Idle walking-packet transport changed yaw.** `beginPacketRotation` ran without an active fake rotation. Its remembered yaw unwrapped later camera angles through `RotationMath.nearest`, including after a producer had stopped. For example, the old idle scope changed `270` to `-90`. The camera/movement calculation still used the original yaw. Minecraft's float trigonometry can differ for angles separated by a full turn: the native lookup gives sine `-0.0001917476` for `-179.99` and `-0.0000958738` for `180.01`. The idle packet hook now returns without opening a scope, and an idle `getRotationYaw` returns the original camera value directly.
- **Input wrapper installation erased existing input.** MoveFix and NoSlow installed zero-initialized wrappers, losing the previous forward/strafe/jump/sneak values that vanilla can inspect before its next input update. Both wrappers now copy those fields at installation; disabled updates continue to delegate to the original input.
- **Explicit disable left correction running.** Releasing Scaffold/KillAura ownership initiated smooth rotate-back even after the module was disabled. The rotation service now tracks the source through a return, and module disable discards that source's rotation and forced movement. An unrelated producer's state remains intact. Smooth return still works when an enabled producer temporarily releases its target. Hypixel's named bot owners are also discarded on disable.
- **Autoblock reset released unowned use input.** KillAura's Raven reset could force the use-item key off even if autoblock had never acquired it. Reset now releases only owned input.

## Other Raven entry points reviewed

| Area | Disabled behavior |
| --- | --- |
| Scaffold update, input, mouse and placement hooks | Guarded by Scaffold enabled state; placement also checks the current player/world/slot. |
| Outgoing Scaffold packet filter | Allows all packets unchanged while disabled. |
| Jump/tower hooks | Return the original jump value or skip tower work while disabled. Timer and slot cleanup restore only previously owned state. |
| Raven autoblock use/right-click/mouse hooks | Guarded by enabled KillAura and its Hypixel branch. |
| Raven prediction and block helpers | Prediction copies movement input, capabilities and food state; its position, bounding box, motion, collisions and block overrides belong to the simulation. The shared attribute map is read only. No live player velocity or timer writes were found there. |
| Shared yaw/sprint/look hooks | Return their original arguments without an active fake rotation. |
| Velocity update guard | Disabled handling clears internal freeze bookkeeping and leaves the vanilla update enabled. |
| Raven saturation/hurt-camera injections | Rendering only. |

## Verification

`InactiveMovementTest` first reproduced the old idle yaw rewrite, lost input state, changed packet trace and unowned use-key release. After the fixes it compares native `MovementInputFromOptions`, `Entity.moveFlying`, and the real `EntityPlayerSP.onUpdateWalkingPlayer` packet writer against the hooked path for 1,024 steps over all four movement policies. It covers every combination of the six movement keys, sprint transitions, large/repeated turns and outgoing hooks. Packet payloads, input and horizontal motion match bit for bit. Additional checks cover pending return cleanup, unrelated owners and wrapper installation.

The existing Scaffold/rotation/BedAura tests and the transformer checks for development and obfuscated Minecraft classes also run. Evidence is written to `build/movement-inactive-audit/`. This is local behavioral verification; no live-server acceptance claim is made.
