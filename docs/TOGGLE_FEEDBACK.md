# Camera and module feedback

- **Visual → NoHurtCam** removes the camera tilt when taking damage. It can be
  enabled through ClickGUI, a keybind, or `.toggle NoHurtCam`.
- **Client → ToggleSound** adds separate enable and disable tones. Enable it,
  choose **Soft**, **Pop**, or **Chime**, and adjust **Volume** from 0 to 100.
  These original, short sound effects also respect Minecraft's master volume.
- **HUD → HUD Elements → Notifications** shows module names and their new state
  in a small animated stack. It is on by default, including when upgrading an old
  profile. Turning it off and saving keeps it off on later launches.

Press **H** to open the HUD editor. Select **Notifications** to move it, drag its
resize handle to scale it, or choose its appearance. The editor shows sample
notifications, so it can be positioned without toggling modules.

Select **Notifications** in the editor to customize duration (1–10 seconds), maximum
cards (1–8), width, height, spacing, font weight, animations, icons, status labels,
progress bars, shadows, borders, corner radius, background opacity and colors.
All controls are also registered on the HUD module for ClickGUI and chat commands,
and save with your profile. Layout, scale and the selected theme save in the HUD layout.

The editor's **Vibe / Skeet / Glass** selector controls the notification theme:
Vibe uses rounded cards and status badges; Skeet uses flat rectangular frames,
an accent line and plain status text; LiquidGlass uses the refractive glass shader.
Glass blur, refraction, opacity and tint appear in the inspector when Glass is
selected. Glass Tint controls the glass surface color; Background Color controls
the Vibe and Skeet surfaces. Corners apply to Vibe and Glass. The preview updates
as you edit. Large stacks automatically show the newest cards that fit on screen.

Notifications work with keybinds, chat commands, ClickGUI, scripts and extensions.
By default the stack keeps the most recent four changes for 3.2 seconds each, with the newest
at the bottom. Every toggle gets a separate card, including rapid enable/disable
changes to the same module. Older cards slide upward with consistent spacing.
Centered check/minus icons and state pills distinguish enabled and disabled cards.
Profile restoration and startup do not produce notification/sound
bursts. Feedback can appear over ClickGUI and continues to acknowledge toggles
when the general HUD module is off; its own Notifications selector controls it.

Verification: `gradlew test verifyToggleFeedback` checks camera hooks in the real
development and installed game bytecode, decodes every sound with Minecraft's
codec, and renders the notification widget and HUD-editor layouts with OpenGL.
Screenshots are in `build/feedback-render-check/`.
