# Supplied Novoline source port

These additions were adapted from the user's `context/novoline` checkout, whose
README identifies it as a Minecraft 1.8.8 client. Vibe runs Minecraft 1.8.9.
The original source remains the reference for the designs, transforms and
movement constants; Vibe supplies its module registry, settings, rendering
state guards, event hooks, native attack/placement controllers, MoveFix and
silent/basic hotbar switching.

| Feature in Vibe | Reference under `context/novoline/src/main/java/cc/novoline/` |
| --- | --- |
| Dropdown ClickGUI | `gui/screen/dropdown/{DropdownGUI,Tab,Module,Setting}.java` |
| TargetHUD | `utils/RenderUtils.java`, `renderTHUD(KillAura, EntityPlayer)` |
| Watermark and arraylist fonts | `modules/visual/HUD.java`, `drawName`, `drawTime`, `drawArrayList` |
| Notifications | `utils/notifications/NotificationRenderer.java` |
| RewoundNovoline autoblock | `modules/combat/KillAura.java`, Pre/Post `onMotions`, `sendBlock`, `sendUnBlock`, `interactAutoBlock`, outgoing use-packet filtering |
| Blocking animation names | `modules/visual/Animations.java` |
| Tower and Line | `modules/move/Scaffold.java`, `towerMove`, `onTower`, `onRender3D` |
| Step | `modules/move/Step.java` |
| Jesus | `modules/move/WaterWalk.java` |

The 16 blocking transforms come from the supplied
`net/minecraft/client/renderer/ItemRenderer.java`. Vibe replaces only the native
block-pose transforms, keeping Forge's item, glint and lighting passes.

The source's optional, module-wide KillAura Blink feature is separate from its
single autoblock implementation and was not imported as another autoblock mode.
Source tower C09 handling is replaced by Vibe's shared slot lease. Its placement
requests use Vibe's native controller and rotated ray trace rather than a second
raw placement sender. Ordinary manual use remains under Vibe's existing input
ownership. The Hypixel-specific tower movement packet triplet and Step sneak
packets retain their source server condition. Disabled modes restore native
movement and release the state they own.

Assets copied into `assets/vibe/novoline/`:

- `SF.ttf`: the supplied Client arraylist face (size 21), also used by dropdown
  labels and watermark (sizes 18/20).
- `stylesicons.ttf`: the supplied dropdown and notification icon glyphs.
- `pixel.png`: the supplied `textures/font/crack.png`. In this checkout,
  `crack.png` and `ascii.png` are byte-identical. The source arraylist's second
  choice, Vanilla, uses Minecraft's native bitmap font; it is exposed as
  **Novoline Vanilla** alongside **Novoline Client** in Vibe.

The supplied checkout does not contain a license declaration for these original
Novoline files or fonts. Its `LICENSES/VIBE-GPL-3.0.txt` applies to an unrelated
account-manager port from Vibe and is not evidence of a license grant for
Novoline's original code or assets. No additional license or authorship claim
is inferred here. Preserve this provenance when distributing the additions.
