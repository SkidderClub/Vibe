# ESP and ESP Editor

ESP has four independent modes: **2D, 3D, Skeletal and Chams**. The editor's top row enables each mode; its settings appear in a separate collapsible section. The ESP ON/OFF button controls the module itself. The preview remains available while ESP is off. In GTA7, open **ESP Editor** from the pause menu; Escape returns to the paused run without resetting it.

The editor uses the shared Skeet window, panels and buttons. **Players / Friends / Targets** select independent appearance settings for all four modes. Friends includes teammates detected by Targets; a named Target takes priority. Turn off **Use player defaults** to customize a Friend or Target profile. Inheritance applies to layout and materials; legacy 3D profile color overrides remain supported.

The preview renders an actual Minecraft `EntityOtherPlayerMP` through `RenderPlayer`, including the current skin's standard/slim model, skin layers, held item and optional armor. It also works without joining a world. **Visible / Occluded** selects the corresponding Chams material; **Healthy / Hurt** demonstrates damage overrides; **Armor** toggles the preview's equipped armor models. Skeleton lines follow the same model's pose.

## 2D elements

The preview's buttons enable the box, health bar, armor bar, name, distance, item name, item icon, numeric health and armor equipment display. Disabled elements have no settings section. Click a section to expand it, or middle-click the corresponding preview element.

| Action | Result |
| --- | --- |
| Drag an element | Move it to an edge and change its place in the stack |
| Drag its selected corner handle | Scale the element |
| Wheel over an element, including while dragging | Adjust scale in small steps |
| Middle-click an element | Select it and scroll to its settings |
| Drag empty preview space | Rotate the reference model |
| Wheel over the settings panel | Scroll settings |
| Shift + wheel over a numeric setting | Adjust by one increment |
| Drag a numeric control | Adjust its value |
| Right-click a mode setting | Cycle backwards |

Bars can use left, right, top or bottom. Text and item tags use top, bottom, left up, left down, right up or right down. Armor equipment automatically switches between a vertical column and a horizontal row. Stack order, along-edge offset and gap are saved. Collision spacing includes outlines and text shadows; crowded elements move outwards instead of covering one another. This applies within each actor's layout.

Widths, scales, offsets and spacing use fractional coordinates. Distance scaling follows projected actor height; set its strength to zero for fixed-size attachments. Bars fill according to actual maximum health, including GTA7's larger health pools. Distance units are meters or feet. Armor equipment and item icons appear only when the actor has that equipment; GTA7 police use a native pistol icon and have no armor inventory.

Distance scaling defaults to 1, keeping attachments proportional to the projected player. There is no minimum scale that leaves distant icons oversized. Along-edge offsets follow the player's projected height independently of the text scale setting. Backgrounds are **off by default** and have explicit enable switches. Text backgrounds use measured glyph height, including descenders, instead of the font's full line height. Corner outlines include caps around every open end.

The box supports full edges, corners, corner length, a distance threshold for switching to corners, rounded edges, line width, outline width/color and an optional translucent fill. Bar backgrounds and outlines have independent alpha. Each text element can inherit the default font, size, shadow and color or override them. Fonts are Minecraft, Sans and Sans Bold. The box's scale changes its stroke and rounding while its bounds continue to follow the actor.

## Colors

Choose **Static**, **Team**, **Global Gradient**, **Custom Gradient** or **Rainbow**. A global gradient samples the same screen coordinates for every element that uses it, including text glyphs. It is visible only inside those elements. Custom gradients span the individual element.

Each ESP color also has a **Source: Static / Team**, including outlines, backgrounds, gradient stops and Chams. Team follows the Targets module's **Teammate Detection** setting: scoreboard, formatted name color, or leather chestplate color. It preserves the configured alpha and falls back to the configured color when a team color is unavailable. **On damage** enables a separate hurt color while Minecraft's `hurtTime` is active; this takes priority over Team, Rainbow and gradients. Hurt colors can themselves use Team.

Gradients support two to eight color stops. Click a stop to edit it, drag it along the strip to change its position, and use `+` / `-` to change the stop count. The picker includes saturation/brightness, hue, alpha and editable `#RRGGBBAA` input. Stops remain saved when hidden by a lower count. Direction accepts degrees; signed speed controls animation direction, and zero freezes it. Animation moves back and forth continuously. Rainbow has separate speed and saturation controls.

The global gradient section appears when an enabled element uses it. Text overrides appear only when that element stops using the default text style.

## Chams Glow

Glow has an emissive surface and a blurred halo outside the actual model silhouette. Visible and occluded surfaces keep their own colors, alpha, skin and armor settings. The world renderer collects depth-tested masks, then blurs them once for the entity pass. Transparent skin cutouts remain transparent. The effect preserves the scene's depth buffer and restores the caller's graphics state.

## Compatibility

Minecraft, GTA7 and the editor share the same 2D renderer and layout. GTA7 retains its police actor selection and supports the four mode toggles. Minecraft's skeletal pose capture and limb rendering are unchanged; their settings now belong to ESP. Old standalone Skeletal profiles migrate their settings and enabled state to ESP. Legacy 2D toggles, colors, widths and positions migrate when loading an older profile. Editor changes save on close.

## Verification

With the project's Gradle build configured, run:

```text
gradle test --tests "dev.vibe.ui.Esp*Test" --tests "dev.vibe.config.EspConfigMigrationTest"
gradle verifyEspRendering
gradle verifyChamsRendering verifyGta7Rendering
```

The native checks use an offscreen OpenGL context, leave player profiles untouched, and write screenshots to `build/esp-render-check/`, `build/chams-render-check/` and `build/gta7-render-check/`. They check corner caps, text background centering, distance anchors, Team/hurt shader colors, gradient masks, bar orientations, health normalization, real player/equipment previews, profile independence, visibility toggles, editor interactions, Chams halos/alpha/occlusion/state restoration and combined GTA7 modes. The old optional ESP init script remains compatible.
