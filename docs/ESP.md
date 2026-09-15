# ESP and ESP Editor

ESP has four independent modes: **2D, 3D, Skeletal and Chams**. The editor's top row enables each mode; its settings appear in a separate collapsible section. The ESP ON/OFF button controls the module itself. The preview remains available while ESP is off. In GTA7, open **ESP Editor** from the pause menu; Escape returns to the paused run without resetting it.

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

The box supports full edges, corners, corner length, a distance threshold for switching to corners, rounded edges, line width, outline width/color and an optional translucent fill. Bar backgrounds and outlines have independent alpha. Each text element can inherit the default font, size, shadow and color or override them. Fonts are Minecraft, Sans and Sans Bold. The box's scale changes its stroke and rounding while its bounds continue to follow the actor.

## Colors

Choose **Static**, **Global Gradient**, **Custom Gradient** or **Rainbow**. A global gradient samples the same screen coordinates for every element that uses it, including text glyphs. It is visible only inside those elements. Custom gradients span the individual element.

Gradients support two to eight color stops. Click a stop to edit it, drag it along the strip to change its position, and use `+` / `-` to change the stop count. The picker includes saturation/brightness, hue, alpha and editable `#RRGGBBAA` input. Stops remain saved when hidden by a lower count. Direction accepts degrees; signed speed controls animation direction, and zero freezes it. Animation moves back and forth continuously. Rainbow has separate speed and saturation controls.

The global gradient section appears when an enabled element uses it. Text overrides appear only when that element stops using the default text style.

## Compatibility

Minecraft, GTA7 and the editor share the same 2D renderer and layout. GTA7 retains its police actor selection and supports the four mode toggles. Minecraft's skeletal pose capture and limb rendering are unchanged; their settings now belong to ESP. Old standalone Skeletal profiles migrate their settings and enabled state to ESP. Legacy 2D toggles, colors, widths and positions migrate when loading an older profile. Editor changes save on close.

## Verification

With the project's Gradle build configured, run:

```text
gradle test --tests "dev.vibe.ui.Esp*Test" --tests "dev.vibe.config.EspConfigMigrationTest"
gradle -I tools/esp-render-check.gradle verifyEspRendering
gradle verifyChamsRendering verifyGta7Rendering
```

The native checks use an offscreen OpenGL context, leave player profiles untouched, and write screenshots to `build/esp-render-check/` and `build/gta7-render-check/`. They cover gradient masks, all four bar orientations, GTA7 health normalization, fonts, editor middle-click/resize behavior, small GUI scaling, Chams and combined GTA7 modes.
