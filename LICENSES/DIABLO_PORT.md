# Diablo 2.1 source port

The reference is the user-supplied `context/Diablo2.1` checkout. Only the
requested designs, mechanics, message list and font assets were adapted;
Diablo's launcher, account implementation and other runtime code were not
imported. Vibe retains its categories, modules, settings, rotation and input
ownership, packet routing, native item renderer and shared kill journal.

| Vibe feature | Supplied reference under `src/main/java/wtf/diablo/` |
| --- | --- |
| Diablo ClickGUI | `gui/clickGui/ClickGui.java`, `impl/Panel.java`, `impl/Button.java`, `impl/settings/{Slider,CheckBox,Mode,Keybind}.java` and `module/data/Category.java` |
| RewoundDiablo Criticals | `module/impl/Combat/Criticals.java`, Watchdog packet handler |
| Killsay's Diablo pool | `module/impl/Player/KillSay.java`, all seven message strings |
| Diablo TargetESP | `module/impl/Combat/KillAura.java`, `onRender`/Visualise, with `utils/animations/Animation.java` |
| Diablo TargetHUD | `module/impl/Combat/KillAura.java`, `onOverlay`, Diablo case (not Material) |
| RewoundDiablo LongJump | `module/impl/Movement/LongJump.java`, Watchdog case, and `utils/player/MoveUtil.java` |
| Diablo/ExihiDiablo blocking | The Diablo and Exhi cases in the supplied `net/minecraft/client/renderer/ItemRenderer.java` |

Adaptations:

- Diablo Criticals alternates the original 0.01/0.007 offsets on native movement
  packets while grounded with an aura target. It does not turn ground/look-only
  packets into position packets. Main-thread snapshots avoid reading the world
  from Vibe's Netty handler; NoFall retains its existing final ground policy.
- LongJump retains the 49 silent packet pairs, final grounded packet, .70F
  launch and base-speed-times-1.14F post-update motion. The movement formula
  uses Vibe's effective movement yaw. Mode/world changes release owned state.
- Killsay uses the same session journal as Kill Feed, rather than Diablo's
  tab-list-removal heuristic. Additional death/player evidence prevents mob
  kills and ordinary removals from triggering messages. Duplicate journal
  confirmations, old history and other players' kills are excluded. None of
  the seven supplied message strings contains a racial slur; all are retained,
  with the target name appended as in Diablo. The original module description
  is not copied. Vibe's own pool uses `%name%` for the victim.
- TargetESP retains the source's radius-two, 45-vertex GL_LINES sweep and
  12.75 line width. Its camera translation is applied once, correcting the
  source's duplicate camera-relative translation; the current Vibe camera and
  GL state are preserved. The source's static purple is the default color.
- The ClickGUI retains Vibe's scroll persistence, clipping, sorting and color
  editor. Source category colors/glyphs are mapped onto Vibe's categories.
  Very tall settings panels finish expanding within a bounded duration.
- The two blocking poses use the source's default scale/speed (1), preserving
  Minecraft's swing, item model, lighting and glint passes. Exhi is exposed as
  **ExihiDiablo**, as requested. Vibe's positional offsets remain available.

Fonts copied from `assets/minecraft/diablo/fonts/` into
`assets/vibe/diablo/`: `sfregular.ttf`, `guiicons.ttf`, `checkmark.ttf`.

The supplied checkout's attribution file only identifies a separate GPLv3
account-manager port from Vibe. It does not declare the original Diablo code
or font licenses. No license grant, authorship or clearance is inferred here;
retain the available source provenance and notices when distributing.
