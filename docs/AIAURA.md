# AIAura

AIAura is a personal behavior-cloning module in Combat. It uses a small local neural network trained from manual play in its recorder. It ships without a trained profile. Enabling an untrained module does not aim or click.

## Start here

1. Open AIAura's settings and press **Open Recorder**. The module can stay disabled while recording.
2. Select **Record manually**. Move, jump, attack and change perspective with your configured Minecraft bindings and sensitivity. The dark arena has jumpable blocks, stair routes, elevated platforms and increasingly large waves. The player cannot die or be teleported by damage; enemy defeats still advance waves. Hits retain their normal reactions and knockback.
3. Record encounters at different distances. Include approaching enemies, close tracking, misses, pauses, jumps and target changes. Press **Esc** to pause and save. Losing focus or a long frame also pauses recording.
4. The dashboard shows what is still missing. **Train model** becomes available after 600 frames, 15 physical clicks, 3 rotation reactions, 3 first-click reactions, 10 repeated-click intervals, 10 button releases, 20 valid crosshair aimpoints and 20 frames in each of the near/edge/outside-reach bands. These are minimum data checks, not a model-quality guarantee. Several varied manual sessions are more useful than repeatedly performing one motion.
5. Select **Train model**. Training runs on a background CPU thread. The dashboard shows epochs and training/validation loss. You can leave the menu while training finishes.
6. Use **Preview model** to inspect learned aiming and clicking in the arena. You still control movement. Preview actions never become demonstrations.
7. Return to Minecraft and enable AIAura. KillAura and AIAura disable each other. Shared Targets/Friends rules still apply. AIAura operates on visible targets and stops in menus, Freecam, block-breaking conflicts and other situations excluded by the shared combat controller.

## Settings

| Setting | Default | Behavior |
| --- | --- | --- |
| Open Recorder | Off | One-shot menu action; works without enabling combat. |
| Reach | 3.0 | Actual ray intersection limit in blocks, including the entity's native collision border. The default adds no extra reach. Range: 1.0–6.5. |
| Booster Clicking (x) | 1.0 | Scales the learned click rate, 0.8–4.0. |
| Booster Aiming (x) | 1.0 | Scales learned mouse-count output, 0.8–4.0. |
| LockFOV | Off | The visible camera and target-selection FOV follow the spoofed perspective. Rendering interpolates between rotation ticks. With this off, aiming stays silent. |
| Reset | Off | Opens a confirmation dialog. Only confirming deletes the recordings and model. Cancelling preserves them. |

The boosters change the demonstrated distribution. In particular, multiplying speed or CPS by four does not preserve the original player's behavior. Reach above 3.0 is extended reach; a trained model does not change server reach rules.

## What is learned

Each 50 ms demonstration row contains 40 contextual features and ten masked labels. Four successive rows form a 160-input window. The network has 32 and 24 tanh hidden units, ten output heads and 6,194 trainable parameters. Training uses CPU backpropagation, Adam, masked task losses and validation-based early stopping, for up to 60 epochs. At most 10,000 training rows are sampled per run, preserving scarce reaction/interval/hold/aim labels before filling with evenly spaced rows. Required output labels must occur in the training portion as well as meeting the overall coverage checks.

Context includes angular error, apparent target size, distance and reach band, target motion, player motion and vertical speed, grounded/jumping/sprinting state, mouse sensitivity, previous counts and acceleration, acquisition time, time since clicking, button-held state, preferred aimpoint, target changes, health, hurt state, time since an observed target hit and target facing. Movement and jump inputs condition the combat policy; AIAura does not take over walking.

The outputs are yaw/pitch mouse counts, click-burst intent, conditional repeat-click interval, separate first-rotation and first-click delays, three normalized aimpoint coordinates and button-hold duration. Missing observations are masked rather than treated as zero-delay/zero-hold demonstrations. Aimpoints are labeled only when the crosshair intersects the target body. The prior valid point supplies the next frame's preferred aim location. Repeat-click intervals are labeled within one target encounter. Multiple physical presses in a tick are counted, while the row retains the most recent observed interval; the raw log retains every edge.

The checkpoint measures nine distance/angular-phase cells, including observed count magnitudes, log click-interval variation and reaction-delay bounds. Angular covariance/correlation metadata from previous checkpoints remains readable, but sampled angular noise is no longer injected. Neural rotation outputs pass through a causal velocity filter, bounded acceleration, smooth aimpoint updates and a settling zone for stationary aligned targets. Quantization happens after filtering. There is no model output without a valid visible target, and a lost target causes a monotonic return to the camera without neural noise. This is a temporal feed-forward model, not a GAN, an LSTM or a model trained against an anticheat.

Pitch also has closed-loop geometric feedback toward the learned body aimpoint, including the next tick's relative vertical motion. This is an explicit motor-control correction, rather than a claim that an untrained vertical head learned missing examples. Its response/speed metadata is calibrated from active manual pitch samples, or transferred from recorded horizontal motion when vertical samples are sparse. Legacy checkpoints have a conservative fallback. It does not use a zero per-cell pitch cap, so a recording dominated by level tracking cannot permanently pin the camera to the horizon. Vertical actions and height encounters receive dedicated sampling priority during training, and nonzero pitch actions receive more loss weight. Raw mouse events are retained, while processed pitch labels reflect the actual clamped camera movement.

Sessions remain chronological for validation. A whole final session is used when practical; otherwise the final 20% is reserved. An eight-row gap separates training from validation. Early stopping and residual calibration both use validation data, so the displayed validation loss is **not an independent final test score**, a human-likeness percentage or an anticheat result.

## Input integration

AIAura specializes KillAura's policy hooks. Target selection and neural prediction run at the same pre-movement stage. The chosen rotation is published to MoveFix under `aiaura`. During vanilla's existing input pass, the shared controller validates the rotated ray and queues attack-key press edges using `VanillaClicks`. Minecraft handles swings, attacks, hotbar synchronization, item-use gating and movement packets. There is no separate AIAura packet sender.

Native mouse gain and floating-point arithmetic are preserved. Outputs become integer mouse counts with bounded fractional carry before applying Minecraft's sensitivity conversion. Pitch is clamped. Measured angular behavior is converted when sensitivity changes. Learned click deadlines are bounded; stale deadlines do not replay an arbitrarily long backlog. The shared default ray range, occlusion checks and intervening-entity obstruction checks remain in use.

## Local files

All files live under `<Minecraft data directory>/vibe/aiaura/`:

| File | Contents |
| --- | --- |
| `demonstrations.bin.gz` | Versioned processed manual rows; at most 60,000, oldest batches removed when full. |
| `model.json` | Network weights, feature-compatible checkpoint metadata, measured residual statistics and losses. |
| `session-<time>-<part>.events.gz` | High-resolution manual keyboard/mouse/configuration event stream. A new part starts after one million events. |

Nothing is uploaded. Pause/close saves processed rows atomically. A crash before pause can lose the current unsaved rows. Model files are published atomically only after training completes. Reset cancels training using a generation token, so a cancelled worker cannot restore a deleted model. Reset deletes only AIAura's known files, preserving unrelated files in the directory.

The raw stream starts with magic `0x56414945`, version `1` and a session timestamp. Events contain relative nanoseconds, type byte, code, pressed flag and three integer fields. Type 0 records initial configuration: invert flag, sensitivity float bits, FOV float bits and reach in thousandths. Type 1 contains mouse code (button minus 100), raw dx/dy and wheel; type 2 contains keyboard codes and down/up state. Type 3 records the local perspective and configured perspective binding. Mouse inversion is applied when creating pitch labels. Raw configuration/event logs are retained for inspection; training currently uses the processed rows.

The arena uses Minecraft 1.8 dry-land acceleration and friction, air control, diagonal normalization, sprint impulses, the held-jump cooldown, gravity and native AABB clipping with 0.6-block stepping. Both the player and opponents use the same integrator; opponents accelerate and retain knockback velocity rather than replacing their position each tick. Sprint can be held or started by double-tapping forward. The local world does not emulate a complete server, network latency, water, ladders, every block or custom server knockback.

Opponents acquire a facing direction and use independent attack timers, a 3-block attack ray and obstacle checks. Damage follows the 20-tick resistance timer with its 10-tick equal-damage window. Base unarmed damage is 1, descending critical damage is 1.5, normal knockback halves existing velocity before adding the native 0.4 impulse, and sprint attacks add their extra impulse. Player health stays full, without death or respawn. Hit reactions, damage and enemy defeats are tracked separately from raw physical click counts. These are local practice mechanics, not packets sent to a server.

Opponents can spawn on raised surfaces and use bounded local height-map routing with one-block jump transitions to reach the player. Routes are throttled between updates. The map includes progressive jumps, wider elevated fighting areas and routes around tall obstacles.

The configured perspective key cycles first person, rear view and front view locally. It never changes the outer game's perspective setting. Third-person cameras collide with the arena geometry. The first-person arm uses the player's skin (Steve fallback), native player-model geometry and Minecraft 1.8's bare-hand swing transforms/timing. Third-person views show the animated player model. The arena has no keybind overlay.

## Statistics

The Skeet-themed dashboard shows recorded duration, sessions, physical clicks, active clicking rate, average aim/first-click reaction times, valid aimpoint frames, mean target-relative aim error and height-training sample counts. These values come from manual rows. The most recent practice shows wave, eliminations, hit accuracy and damage dealt/received. Preview actions remain excluded from the training statistics. Recording statistics describe the demonstrations and are not anticheat scores.

Existing recordings and models remain compatible. Recording fresh encounters is useful after the physics update because old demonstrations came from the earlier simplified arena. See [the research and detection assessment](AIAURA_RESEARCH.md) for the evidence and limits.

## Verification

`test` includes neural training/serialization, malformed data, timing, reach contexts, sensitivity conversion, cancellation, no-target/settled-aim stability, LockFOV camera interpolation and real native-input integration. Arena acceleration, drag, jump and knockback are compared against the actual Minecraft methods; steps and collision behavior are checked separately. Test-generated demonstrations live only in temporary test folders. `verifyAIAuraRendering` draws the recorder, fonts, player rig and reset confirmation in a hidden OpenGL context at 960×600, 427×240 and 320×200. It checks manual persistence, preview isolation and low scene brightness. Production linkage is included in `verifyFreecamProduction` alongside the existing camera checks.
