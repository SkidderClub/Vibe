# AntiAFK

Movement module. Select any combination of Move, Switch Items, Jump, Rotate and Left Click. Rotate is selected initially. Actions cycle through the selected modes, one at a time.

- **Idle Delay (s)**: time since genuine keyboard or mouse activity before the first action. Holding a key or mouse button also counts as activity.
- **Action Delay (ms)**: minimum and maximum interval between subsequent actions while idle.
- **Only With Freecam**: limits actions to an active Freecam camera. Defaults to false, preserving normal AntiAFK behavior. Closing Freecam cancels pending actions and restarts the idle wait.
- **Move Duration (ticks)** and **Rotation Angle** control the short sideways steps and view turns. Steps alternate direction to limit drift. Movement and jumps require the player to be on the ground.
- **Rotation Speed**: maximum yaw speed in degrees per second at the normal 20-tick rate. Turns ease in and out over multiple vanilla look updates. The next action's delay starts when a turn finishes; user activity cancels an unfinished turn.

Move and Jump use the same input wrapper as AutoWalk, before MoveFix correction and vanilla player physics. Switch Items queues a vanilla hotbar key press, with the vanilla inventory scroll method as a fallback for unbound keys. Rotate uses the player's normal look method. Left Click enters the existing vanilla attack-key dispatch and keeps Minecraft's hit/dig logic and cooldowns. AntiAFK does not construct movement, attack or held-slot packets.

With Freecam active, camera movement and mouse look do not reset the body's idle timer. AntiAFK supplies Move and Jump after Freecam diverts camera input; MoveFix corrects that body input once. Rotations affect the real player without moving the lens. Only a scheduled AntiAFK left click can pass Freecam's interaction guard; manual camera clicks stay blocked. Vanilla's dig-release pass still runs to finish or abort mining normally. Hotbar input, interaction keys, mouse buttons and scrolling still count as body activity.

Menus, pausing, death, riding and sleeping suspend actions and restart the idle delay. AutoWalk, Scaffold and other forced movement owners take priority. Left Click yields to AutoClicker and KillAura. Disabling or leaving the world releases owned actions. Server-specific idle policies still apply.
