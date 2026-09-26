# Gothaj Scaffold provenance and port notes

Source repository: [bipasisnotavailable/CuteKittenPonnyStarwarsMEGAKawaii](https://github.com/bipasisnotavailable/CuteKittenPonnyStarwarsMEGAKawaii).
Revision: `8aa9e1c056bc5b6393b29dc03bcb06b529b40a07`.
Retrieved through the user's authenticated GitHub access on 2026-09-25/26.
The repository is private. Its decompiled Java headers identify CFR 0.152;
no license file was present in the retrieved repository tree. This attribution
does not assign Vibe's license to the upstream source or font asset.

## Source mapping

Paths below are relative to `gothaj-decompiled/team/gothaj/client/` upstream,
and `src/main/java/dev/vibe/` in Vibe.

| Upstream | Vibe |
| --- | --- |
| `impl/module/world/Scaffold.java` | `module/impl/ScaffoldModule.java` |
| `api/util/game/InventoryUtil.java` | `module/impl/scaffold/InventoryUtil.java`: block slot, count, item lookup and full invalid-block list |
| `api/util/game/MovementUtil.java` | `module/impl/scaffold/MovementUtil.java`: physical movement keys, direction and diagonal-yaw detection |
| `api/util/player/MoveUtil.java` | `module/impl/scaffold/MoveUtil.java`: `correctMovement` |
| `api/util/rotation/RotationUtil.java` | `module/impl/scaffold/RotationUtil.java`: all methods reached by Scaffold and its rotation component |
| `impl/component/RotationComponent.java` | `module/impl/scaffold/RotationComponent.java`: rotation state, smoothing, random noise, normalization, GCD correction and return lifecycle |
| `impl/event/player/move/MoveInputEvent.java` | `module/impl/scaffold/MoveInputEvent.java` |
| `api/util/math/TimerUtil.java`, `MathUtil.java` | Corresponding files in `module/impl/scaffold/`; only the random-number method is needed from MathUtil |
| `impl/module/visual/widget/BlockCountWidget.java` | `ui/ScaffoldBlockCountRenderer.java` |
| `api/util/font/CFont.java`, `IFontRenderer.java` | `ui/scaffold/` |
| `api/util/render/RoundedUtil.java`, `render/shader/ShaderUtil.java` | `ui/scaffold/CounterRender.java` and the original two fragment shaders |
| `api/util/Logger.java` | `module/impl/scaffold/Logger.java`: host logging/chat adapter |

The font `assets/minecraft/gothaj/render/font/nunito-semibold.ttf` and vertex
shader `assets/minecraft/gothaj/render/shader/vertex.vert` were extracted from
the repository's `Gothaj.jar` into `src/main/resources/assets/vibe/scaffold/`.
The counter uses the original Nunito font, font renderer, dimensions, map-color
gradient, animation factor, item scale and singular/plural labels.

## Minecraft hooks verified against the original JAR

The original archive was inspected with CFR, without running its client.
`core/ScaffoldTransformer.java`, the existing MoveFix transformer and the module's
adapter methods implement these sites:

| Gothaj site | Vibe equivalent |
| --- | --- |
| `Minecraft.runTick`, MouseClickEvent immediately before `isUsingItem` | `clickHook`, next to the existing input hook |
| `EntityRenderer.renderWorld`, MouseOverEvent before `getMouseOver` | `mouseOverHook` |
| `EntityPlayerSP.onUpdate`, NormalUpdateEvent before superclass update | `normalUpdateHook` |
| `MovementInputFromOptions`, mutable keys then recompute and sneak slowdown | MoveFix input wrapper; Gothaj rotation correction runs before Scaffold's module listener |
| `onUpdateWalkingPlayer`, MotionEvent / PostMotionEvent | before/after MoveFix's packet scope, including the pre-tower position snapshot |
| `EntityLivingBase.jump`, motion before potion bonus and corrected yaw | jump-motion hook plus MoveFix's existing yaw hook |
| `EntityPlayerSP.onLivingUpdate`, `omniSprint` branches | scoped sprint-forward and sprint-state hooks |
| `ItemRenderer.renderItemInFirstPerson`, SpoofClientItemEvent | held-item reads use the originally selected item |
| `NetworkManager.sendPacketSilentEvent` | one-packet bypass of Vibe's packet listeners |

## Required host adaptations

- Gothaj modules/properties/events are registered through Vibe's module/settings
  API and Forge hooks. Every Scaffold setting, default, bound, visibility rule,
  algorithm branch and constant is retained. Java 21 `getFirst()` becomes Java 8
  `get(0)`; custom `MathHelper.clamp`, `equalsBlockPos` and distance methods use
  their matching 1.8.9 operations.
- MoveFix is the single owner of packet, look-vector, raycast and third-person
  transport. Scaffold supplies its original correction policy for its owned
  rotation, without changing the user's saved MoveFix mode or applying a second
  keyboard correction. Third-person display follows Vibe's existing display option.
- Private vanilla click/timer members use mapped accessors. World changes clear
  temporary state. A missing block slot is checked before changing the inventory
  index, preventing an invalid `-1` hotbar index if inventory changes between hooks.
- Upstream diagnostic messages remain at their original call sites. The prefix is
  Vibe; debug-only output goes to Vibe's debug logger instead of requiring Gothaj's
  unrelated Debug module.
- Counter drawing uses the source font and GLSL with Vibe's rendering lifecycle.
  Gothaj's global HUD background opacity default is 100. The global shader pass
  is adapted to Vibe's Blur service instead of installing a second postprocessor.
  The counter is movable/scalable through Vibe's HUD editor and its placement is
  saved in the existing HUD layout. Its visibility remains Scaffold's Render Count setting.
- The decompiled font's invalid `'^@'` character literal and missing loop-variable
  declaration are repaired. Its unrelated AntiCrash chat-text filter is omitted;
  the counter draws only its numeric count and `block` / `blocks`.

## Deliberately retained source behavior

- `RotationUtil.lookingAtBlock` reads `objectMouseOver` and does not ray-trace the
  yaw/pitch parameters. Its pitch-search behavior is retained.
- `getRandomNumber(7, 8)` has an exclusive upper bound, so the source threshold is 7.
- The invalid-block list includes gold blocks, slabs and sand, and held stacks
  with more than one block are preferred before searching for the largest stack.
- The source's `getPlaceSide` diagnostic chat messages, null fallback, timer-tower
  lifetime, and unconditional first-person original-item spoof are retained.

The Forge port is not a byte-for-byte copy of Gothaj's patched Minecraft client.
The adaptations above are explicit; no additional Scaffold modes or replacement
placement algorithm have been introduced.
