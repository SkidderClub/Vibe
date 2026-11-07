# Projectile Aura

Combat's Projectile Aura uses snowballs, eggs and fishing rods from the hotbar.
Select any combination in **Modes**, set the **Distance** interval, the minimum
**Delay (ms)** between casts and **Rotation Speed** in degrees per game tick.
It uses the shared Targets filters, including friends and target areas, and
prefers the current melee aura opponent when one is available.

**Activation Mode** defaults to **Always**, which keeps independent aiming and
the Rotation Speed setting. **Only While Killaura** and **Only While AIAura**
require the corresponding aura to be enabled. They use that aura's selected
opponent and supply a temporary ballistic aimpoint to its own rotation policy.
Projectile Aura's Rotation Speed is hidden in these modes; AIAura uses its
existing learned policy, reaction timing and mouse sensitivity handling.
The aura can acquire opponents within the projectile distance interval while
its melee attack reach stays unchanged. Its own target filters still apply.

**FOV** is the horizontal angle allowed on each side of the visible facing
direction. The default and maximum, **180**, allow the full 360 degrees.

Enabled projectile types alternate when stocked. An unreachable rod trajectory
can fall back to an enabled snowball or egg. Prediction includes target motion,
terrain collisions, launch offset, projectile drag and gravity. It checks a
small set of body points and validates the limited rotation before firing;
blocked trajectories and unloaded terrain are rejected. Submerged trajectories
are not used. Vanilla's random throw spread remains, so prediction cannot
guarantee every hit.

Rotations go through MoveFix. Item use runs after the walking rotation packet,
through the native controller and shared AutoTool hotbar transaction. Snowballs
and eggs immediately return to the original slot. A rod remains equipped while
the hook travels because vanilla 1.8.9 removes hooks whose angler stops holding
a rod. It reels on a caught entity or timeout, then restores the previous slot.
Manual slot changes cancel rod ownership without undoing the user's selection.

Melee clicks pause during projectile aiming and rod flight. In Always mode,
Projectile Aura owns rotation; in the aura-only modes, the matched aura keeps
its target and continues rotating. The temporary aimpoint is released after a
throw or when the requirements stop being met. Inventory actions, eating,
bow drawing, block breaking, Scaffold, Freecam and fireball defence take
priority. Independent target work is bounded to three candidates and a 40-tick forecast;
cooldowns and empty hotbars skip trajectory work.

The physics were checked against the locally cached Minecraft/Forge 1.8.9
`EntityThrowable`, `EntityFishHook` and their item-use implementations. Tests
compare predicted impacts with the real projectile update methods and execute
the native controller to check rotation, slot selection and item-use ordering.
