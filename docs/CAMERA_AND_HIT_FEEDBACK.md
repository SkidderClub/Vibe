# Camera and combat feedback

## SmoothF5 (Visuals)

SmoothF5 animates between first person, rear third person and front third person.
Speed is transitions per second (default 4, approximately 250 ms). Easing options
are Linear, Sine, Cubic and Quintic. Top travels over the head; Left and Right
orbit the corresponding side. A second F5 press retargets the current camera pose
instead of restarting from a perspective endpoint.

The transition changes only the view matrix. Player position, mouse input,
rotations and packets are unchanged. Eight camera rays clip the orbit against
blocks. First-person hands are hidden while the camera is outside the body.
Native perspective rendering resumes at the endpoint. Freecam has priority;
sleeping and the debug camera retain their native behavior.

## Reconnect and chest controls

The pause menu includes Reconnect and the existing Random username action.
The latter generates a different temporary offline identity. Both actions first
disconnect and unload the current remote world, then optionally switch identity
and reconnect through the existing connection/Miniblox path. They are disabled in
singleplayer. The disconnect screen keeps its original reconnect controls.

Chest screens have Disable Killaura and Disable Cheststealer controls at the top
right. Each appears only while its module is enabled; the remaining button moves
up when the other disappears. Disabling follows the module's normal cleanup,
notifications, sound and saved configuration. The raw click is consumed before
GuiContainer can also handle a slot under the button.

## Hitmarker: Hologram

Select Hologram in Hitmarker's Modes. A successful local attack feedback event
freezes the last native player-model pose at the impact location. If the model
has not appeared yet, its next rendered pose is captured within 500 ms. Players
keep moving while their hit holograms remain frozen.

The RenderPlayer layer captures all twelve native body/clothing parts, including
angles, pivots, offsets and visibility. The native joint calculation handles
sneaking, riding, walking, attacking, item poses and normal/slim arms. The saved
root matrix includes the renderer's scale, body yaw and sneak translation. It is
converted out of the camera matrix so camera movement does not drag the hologram.
The world origin remains double precision for players far from spawn.

Options include lifetime, spawn delay, color, an optional end-color fade,
transparency, opacity fading with easing, glow strength, through-wall rendering,
original skin opacity and a brightness-only skin mode. Brightness-only uses skin
luminance with the chosen hologram color, discarding the skin's hue. Outer skin
layers retain their actual texture cutouts.

The native geometry is reused rather than creating a new player or display-list
mesh for every hit. At most twelve holograms, sixty-four recent player poses and
thirty-two pending captures are retained. Glow uses the existing batched Chams
silhouette blur, so overlapping ghosts share a blur pass. Disabling Hitmarker or
disconnecting clears the history. No fake entities or network actions are used
for camera/hologram effects.

`verifyCameraHologramRendering` renders native model poses, skin UVs, luminance
material, glow, transparency and the new menu buttons offscreen. Screenshots are
under `build/camera-hologram-check/`. `verifyFreecamProduction` also checks the new
camera hooks and native hologram model types through Forge/OptiFine on Java 8.
