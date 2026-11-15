# November 15 client changes

Inventory and chest actions retain native controller window clicks and a shared
one-click-per-player-tick limit. Their configured delays now govern timing
without the former 125 ms floor or two-tick waiting period. The start and close
delays are sampled once per window/empty state. Different automation owners do
not inherit each other's long delays. Empty armor slots use the native armor
shift-click; occupied slots still use validated pickup/swap steps.

Inventory Manager **Vanilla** uses a hidden player-inventory session. **OpenInv**
still requires the inventory screen. Sessions release after the plan completes
or a different screen/world takes over. **Drop Duplicates** now includes bows
and fishing rods, favoring enchantments and durability.

Rewound Pit accepts prestige-prefixed levels, spacing and invisible sidebar
separators, and checks alternate color-specific sidebar slots. NPC labels allow
the server's hologram offsets and do not require a second description line.
Navigation chooses a reachable, visible interaction cell within spawn, rather
than requiring the villager's exact feet to be a walkable spawn node. The final
approach continues until within interaction reach. Rate-limited local messages
explain sidebar, NPC and configured-area waiting states.

BedAura **Own Bed** defaults off. When enabled, the player's name color is read
at the actual name (after rank prefixes), and compared with the nearest wool's
metadata. BedAura and BedESP share the same nearest-first 13×7×13 search and tie
ordering. The check also protects an already selected bed when preparing a dig.

Speed remains active while walking with Scaffold. Horizontal boosts use the
finalized input and MoveFix movement yaw, so Scaffold's silent turns do not
rotate the travel direction. Speed still yields to stationary NCP towers.
Firefly defaults increase from 32 in 14 blocks to
128 in 36 blocks, with more vertical spread and weaker attraction toward the
player. Profiles using exactly the previous defaults are migrated in memory;
custom densities/ranges remain unchanged.

The new **Keybind List** HUD/editor widget supports Vibe, Skeet, LiquidGlass and
Lavalamp themes, width, colors, maximum rows, status visibility, and the existing
HUD scale and positioning controls. **Only Enabled** defaults on. It lists only
bound modules; disabling the filter also shows inactive bindings. Temporarily
suspended modules retain their configured binding and display Paused.

Radar's **Map Corner Radius** ranges from 0 to 100 and defaults to 15. Zero is
rectangular, and 100 uses a square viewport and circular map. Terrain UVs retain
their world orientation; markers outside rounded corners are omitted. Void
pixels remain transparent. Notifications retain the original antialiased
check/minus/envelope icons, with circular backgrounds tinted from the selected
status and background colors. Compact On/Off indicators fit each theme.

**Phase** (Movement) ports both Exire modes:

- **Watchdog** recognizes the cage countdown, blinks client packets after tick
  30, exempts chat/keepalive/transaction packets, removes glass collision while
  in the cage, and releases on the start banner or disable.
- **NCP** retains the source's entry nudge, collision/push handling, report
  forcing, sneak sink, inside-block step distances and every-fifth-C03 offset.
  World/collision decisions run on the client thread; packet handling uses its
  prepared values. Owned no-clip and jump-factor changes are restored on disable,
  mode change or movement suspension, and old-world packet buffers are discarded.

Regression tests cover native inventory flow and delay boundaries, duplicate
ranking, own-bed colors, NPC navigation with boundary-height differences, every
Speed mode with Scaffold, expanded firefly distribution, HUD binding filters,
and Phase's packet/collision lifecycle. OpenGL render checks cover the keybind
list, notifications and radar radii in all four themes. Native Phase hooks are
also checked in mapped, obfuscated and final Forge/OptiFine classes.
