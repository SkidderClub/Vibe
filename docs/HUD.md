# HUD and editor

Open **HUD Editor**, enable an element with its switch, select it to edit its settings, and drag or resize it in the preview. Layout positions and scales are stored with the other HUD elements. Current settings survive restarts; named configs still change only when explicitly saved.

The canvas uses the current game's resolution and a game image captured after vanilla's bars and before Vibe's widgets. Its native-size render includes the same fonts, items, blur and LiquidGlass shader as the in-game HUD, then scales the complete image into the editor. In the main menu Minecraft's panorama supplies the background. Without framebuffer support, the editor uses a direct drawing fallback and a tinted glass surface. Widgets without a target or live data use preview content; Target HUD uses your own player when available.

## Array list

Background opacity stays uniform across rows, independently of **Bottom Fade**, which fades text and accents. **Accent Rail → Full** draws a continuous perimeter around the list, including the steps between different row widths and any row gaps. **Outer**, **Inner** and **Both** keep rails on the chosen sides. **Outline** separately supports Steps, Rectangle and individual Rows, with thickness, rounding, color and glow controls.

## OOF arrows

Enable **OOF Arrows** in the Visual category. **Display** chooses **Out of FOV**, **In FOV**, or **All directions**. All directions keeps indicators around the full circle, including targets currently on screen. Directions follow the actual camera matrix, including third person and Freecam. Choose Circle or screen-aspect Oval placement, radius, arrow style, size, length scale, width, entity types, range and maximum count. The default arrow proportions are compact.

**Visibility** chooses All, Visible only, or Occluded only using a block ray from the camera to the target. **Invisibility** independently includes, excludes or shows only entities with Minecraft invisibility. Visible and occluded targets have separate opacity controls. Friends are excluded by default. Existing Invisible Only and Occluded Only config choices migrate to the equivalent modes when loaded.

Static, Fade and Rainbow colors support opacity and applicable speed, second-color and saturation controls. Distance fading, minimum opacity, glow, outline, names and distance labels are optional. Placement clamps to the available screen space.

## Scoreboard domain

Enable **Replace Scoreboard Server** to show `bipas.gay`. **Domain Color Mode** offers Static, Fade and Rainbow, with a base color, fade color, animation speed and rainbow saturation. These settings are available in both scoreboard inspectors.

## Item rendering

Inventory, Armor, Stalker, Scaffold count and ESP item icons reuse a bounded GPU cache of native immutable item geometry. Native transformations, lighting, animated textures, enchantment glint, stack counts and durability overlays remain live. Resource reloads invalidate the cache. Dynamic tint providers, custom model/quad implementations, expanded vertex formats and systems without VBO support retain the native drawing path.

## Clock

Select **Clock → Display → Analog** for the clock face, or keep **Digital**. Customize diameter, face/marker/hand/text colors, cardinal numbers, the second hand and its smooth motion, an optional digital readout, date, and 12/24-hour time. The clock uses the computer's local time zone. Element scale applies to either display.

## Target HUD

Enable **Target HUD** in the element list. A card appears only after your player attacks another player; looking at someone or selecting them with AimAssist/KillAura does not show a card. Cancelled attacks, incoming attacks, mobs, and your own player do not add cards. **Keep visible** sets each player's timeout after your last attack on them. Dead or unloaded players also expire, and changing worlds clears the stack.

Cards pop in and out individually. **Animations** toggles that transition, and **Animation duration** controls its speed. **Max targets** defaults to four and supports up to eight, with **Card spacing** between them; the stack is limited to what fits on screen at the current HUD scale. Each card keeps its own health interpolation and particles. Expiring cards animate out while the remaining cards close the gap. The editor keeps a full-size preview even when no combat is happening.

| Style | Display |
| --- | --- |
| **Novoline** | Square skin portrait, name, configurable health bar and centered percentage. |
| **Tenacity** | Rounded portrait/panel, name, percentage and slim configurable gradient bar. |
| **Zeroday** | Live character model, health/hurt-time/relative-facing gauges, optional ping, verdict and lower health rail. Hurt time is the Minecraft hurt counter in ticks; angle is target yaw relative to player yaw, normalized to 0–360°. |
| **Rise** | Rounded portrait, verdict/name, health value/bar and animated particles emitted from the portrait on hurt or health loss. |
| **LiquidGlass** | Compact 46-pixel-high refractive card with a 32-pixel portrait, name, health, verdict, optional ping and configurable gradient. Width remains configurable; the name reserves room for ping. Hit particles emit from the portrait. |

Health percentages use the target's actual maximum health. Winning/Draw/Losing compares the player's effective health with the target's; the Absorption option includes target absorption in that comparison and health text. Smooth Health interpolates the bar while numeric values remain current. Width, colors, rounding and applicable details are available in the inspector. Rise and LiquidGlass expose particle color, count, size and lifetime; disable Particles to clear the effect immediately. Glass uses the shared blur, refraction, opacity and tint controls.
