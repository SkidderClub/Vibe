# Block Counter, Dynamic Island and text appearance

Enable the new elements in HUD Editor. Both support Vibe, Skeet, LiquidGlass and Lavalamp, with the usual position, scale, background, text, accent and outline controls.

## Block Counter

- Slot counts the effective held stack, including blocks selected silently by Scaffold. Total counts block items across the nine hotbar slots, excluding the main inventory.
- While Scaffold and While Eagle are optional visibility conditions. Selecting both shows the counter when either module is enabled; turning both off keeps it visible.
- Display Block shows the actual block icon. The label, text shadow and depletion bar can be disabled independently.
- Warning changes the text and bar to configurable yellow/red colors at the chosen thresholds. The default thresholds are 32 and 8.
- The depletion bar starts full when the counter becomes visible. Placing blocks drains it; acquiring more blocks can refill it. Changing calculation mode or starting another visible session establishes a new baseline.
- Counts, visibility and bar fill share configurable animation duration and easing.

The new counter takes precedence over Scaffold's older built-in count panel when enabled.

## Dynamic Island

The Island starts centered at the top of the screen with the Vibe ghost and editable client text. Choose its active views with the Views multiselect. Selected views replace their standalone HUD widgets and do not require those widgets to be enabled separately.

Priority is held/toggled Tab, then the latest active notification, then the most recent attacked Target HUD, then the idle logo. Tab uses Custom Tab's player rows, heads, server header/footer, scores and ping settings, including the toggle-with-key preference. Target HUD uses its target history, health colors, absorption and smoothing. Notifications use the existing event queue, duration and enabled/disabled colors.

The Island owns one background surface. It interpolates width/height from the current size, clips both views to that surface and fades in the incoming content after expansion has started. Animation, easing, duration, idle dimensions, text, logo, surface colors and corners are configurable. Editor Preview lets you inspect each view without joining a server or toggling modules.

## Watermark and NameProtect

Watermark client text supports Static, Fade and Rainbow colors, with speed, saturation, secondary color and soft text glow. Its optional tags include version, FPS, server TPS, horizontal speed in blocks per second, coordinates, local date/time and the protected username. Tag and separator colors are independent. Separators include bar, dot, bullet, dash, colon, diamond, none and custom text. Tags wrap to the available width at small GUI scales.

Server TPS is estimated from server time-update packets. It shows `--` until a usable interval exists or after updates become stale.

NameProtect's Override Color exposes Static, Fade and Rainbow modes and text-glow controls. Names animate when drawn, including old chat history, native HUD text and Vibe's smooth GUI font. Glyph spacing and surrounding chat formatting remain unchanged; these effects apply to the local protected name.

Settings are part of normal profiles. The new elements' position, scale and theme are stored in the HUD layout.
