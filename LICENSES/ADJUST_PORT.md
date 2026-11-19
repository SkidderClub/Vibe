# Supplied OpenAdjust source port

Reference: the user-supplied `context/Gothaj-SRC/openadjust-main` tree, identifying
itself as the OpenAdjust restoration of Adjust's November 8 build (version 99).
Project credit: Adjust Client, https://adjust.wtf/.

| Vibe feature | Reference relative to `src/main/java/com/adjustclient/` |
| --- | --- |
| Adjust ClickGUI | `ui/clickgui/`, including CategoryTab, TabHeader, ModuleEntry, VisibilityButton, setting controls and `setting/color/` |
| RewoundAdjust Jesus | `module/impl/movement/jesus/RewoundJesusMode.java` |
| Join Claim | `module/impl/exploit/JoinClaimModule.java` |
| RewoundAdjust Step | `module/impl/movement/step/NcpStepMode.java` |
| Adjust TargetHUD | `module/impl/render/hud/target/TargetHud.java` and TargetHudEntry |
| Chat Copy/Remove | `ui/chat/ChatLineActions.java`, plus the original/wrapped message association in the restored GuiPersistentChat |
| RewoundAdjust NoFall | `module/impl/player/nofall/mode/RewoundNoFallMode.java`, NoFallModule and WorldUtil.safeFallDistance |
| AntiAim | `module/impl/player/AntiAimModule.java` |
| QOL Drop confirm | `module/impl/player/DropConfirmModule.java` |
| OOF projectile indicators | `module/impl/render/indicators/IndicatorsModule.java` and IndicatorSettings |
| Adjust sword blocking styles | `module/impl/render/SwordAnimation.java` and the BLOCK switch in `src/mc/java/net/minecraft/client/renderer/ItemRenderer.java` |

The port uses Vibe's categories, typed settings, persisted arraylist selector,
MoveFix rotation ownership, walking/collision hooks, native item renderer and
inventory click dispatcher. AntiAim is a fallback producer: other rotation
modules win. Drop confirmation runs before local item removal, and Inventory
Manager excludes protected stacks from its drop planning. NoFall retains the
original accumulated-fall-distance and jump-potion threshold. Step retains the
two-path collision measurement, packet offsets, random per-run adjustment,
100ms cooldown and 0.7/0.35/0.25 timers; its source height setting is capped at
two blocks by the original implementation.

Join Claim contacts only the official Minecraft session service, at most one
request at a time, while connected to multiplayer with an authenticated session.
It retains the source's 250ms cadence and five-minute reminder. Adjust's account,
vendor restriction/reporting, launcher, Gradle scripts, LWJGL3/NanoVG framework
and restored game executable are not imported.

Copied assets from `src/main/resources/assets/adjust/`:
`textures/chat/copy.png`, `textures/chat/delete.png`, `font/Tahoma.ttf` and
`font/TahomaBD.otf`. They are stored under Vibe's resource namespace. The source
does not provide a root license granting rights to the original Adjust code or
these font/image assets; no license grant, authorship or clearance is inferred.
Keep this provenance with distributions and obtain any required permissions.
