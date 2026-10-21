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

Notifications work with keybinds, chat commands, ClickGUI, scripts and extensions.
The stack keeps the most recent four changes for 3.2 seconds each, with the newest
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
