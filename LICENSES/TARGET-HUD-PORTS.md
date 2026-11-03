# TargetHUD layout ports

The native 1.8.9 renderer in `dev.vibe.hud.UpstreamTargetHuds` adapts the layouts,
positions, health bars and equipment displays from these GPL-3.0 upstream projects.
Vibe's fonts, native item renderer, configurable sizing and HUD animation framework
replace the projects' Minecraft 1.21 and browser rendering APIs. Minecraft 1.8.9
has no real offhand equipment slot; those slots remain absent.

* **ThunderHack Recode**, Pan4ur and contributors, commit
  `49c76bcc5a5e4fbd12b06305c09f0c8fb76c510b`:
  [TargetHud.java](https://github.com/Pan4ur/ThunderHack-Recode/blob/49c76bcc5a5e4fbd12b06305c09f0c8fb76c510b/src/main/java/thunder/hack/features/hud/impl/TargetHud.java).
  Includes ThunderHack, NurikZapen, its Mini variant and CelkaPasta. The bundled
  `assets/vibe/targethud/thunderhack/thud.png` is the upstream `textures/hud/elements/thud.png`.
* **LiquidBounce Nextgen**, CCBlueX and contributors, commit
  `670fff667aa4b1b9e370bf94d45f48017abeb173`:
  [TargetHud.svelte](https://github.com/CCBlueX/LiquidBounce/blob/670fff667aa4b1b9e370bf94d45f48017abeb173/src-theme/src/routes/hud/elements/targethud/TargetHud.svelte),
  `HealthProgress.svelte`, `ArmorStatus.svelte` and `src-theme/src/colors.scss`.
  The three bundled health, absorption and armour SVGs are copied from
  `src-theme/public/img/hud/targethud/` at the same commit.

GPL text: [GPL-3.0.txt](GPL-3.0.txt).

The Item Physics transform retains the native item count and ground-transform
ordering also used by NightX's
[MixinRenderEntityItem.java](https://github.com/Aspw-w/NightX-Client/blob/master/src/main/java/net/aspw/client/injection/forge/mixins/render/MixinRenderEntityItem.java),
by As_pw and contributors (GPL-3.0). Vibe supplies its own time-based tumbling and
settling; it does not change server entity physics.
