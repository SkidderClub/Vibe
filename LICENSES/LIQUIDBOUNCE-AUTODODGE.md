# LiquidBounce AutoDodge port

Copyright (c) 2015–2026 CCBlueX and contributors. Licensed under **GPL-3.0-or-later**;
the complete [GPLv3 text](GPL-3.0.txt) is bundled with Vibe.

Pinned upstream revision: `df0f10eba33d085f8a829737eb95a65f8f1e2ab2`.

- [ModuleAutoDodge.kt](https://github.com/CCBlueX/LiquidBounce/blob/df0f10eba33d085f8a829737eb95a65f8f1e2ab2/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/autododge/ModuleAutoDodge.kt)
- [DodgePlanner.kt](https://github.com/CCBlueX/LiquidBounce/blob/df0f10eba33d085f8a829737eb95a65f8f1e2ab2/src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/autododge/DodgePlanner.kt)
- [SimulatedArrow.kt](https://github.com/CCBlueX/LiquidBounce/blob/df0f10eba33d085f8a829737eb95a65f8f1e2ab2/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/SimulatedArrow.kt)
- [MovementUtils.kt](https://github.com/CCBlueX/LiquidBounce/blob/df0f10eba33d085f8a829737eb95a65f8f1e2ab2/src/main/kotlin/net/ccbluex/liquidbounce/utils/movement/MovementUtils.kt)

The Java adaptation is in `features/dodge/DodgePlanner.java`,
`features/dodge/DodgeTrajectory.java` and `module/impl/combat/ProjectileDodgeModule.java`.
It retains the upstream safety distances, perpendicular border selection, two-tick
momentum adjustment, two-height obstacle rays, directional dead zones and rotation/jump
escalation conditions. Vibe supplies 1.8.9 movement/projectile physics, MoveFix rotation
ownership, full-body collision/void validation and supported stopping paths. Spectral
arrows/tridents are absent in 1.8.9; snowballs, eggs and potions use their native gravity.

The matching Vibe source distribution includes these modified sources and this notice.
