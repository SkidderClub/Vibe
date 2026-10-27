# Path (Visual)

**Movement → AutoWalk → Path** can follow this route and display its line without
changing the Path toggle. See [AutoWalk](AUTOWALK.md) for walking, rotation speed
and autojump settings. Path itself still only calculates and renders the route.

Path draws a thin world-space walking route around blocks to one nearby entity. It is only a visual guide: it never moves, rotates, attacks, changes perspective or sends packets. It also renders in Freecam, independently of Hide HUD.

## Targets

- **Nearest Player** (default): the closest eligible player, with a small switching margin to avoid flicker.
- **Nearest Target**: the closest entity allowed by the shared Targets module, including enabled mob/animal types.
- **Combat Target**: KillAura's current target, or AimAssist's current target. This does not enable either module.
- **Priority Target**: the nearest eligible player in your `.target add <ign>` roster.

All modes respect the shared Targets filters, friends, teammate exclusions, invisibility and Max Range. There is no route when no eligible entity exists. The origin is your real player's feet, including while viewing from Freecam.

## Appearance

The default is a slim green solid line just above the floor, matching the supplied references. Color includes alpha/opacity and optional team/hurt overrides. Gradient enables a separate End Color. Line Width, Solid/Dashed style, Dash Length, Height Offset, Through Walls and a Square/Box endpoint marker are independently editable. Settings use the existing ClickGUI and profile system.

Complete routes use the normal color. If a target is unreachable within the search limits, Show Partial Path can show the closest safe progress in Partial Color (amber by default); its endpoint marks where that partial route stops, not the target. Turning this off hides incomplete routes. There is never a direct-line fallback through blocked or unloaded terrain.

Routes follow real block collision shapes, including slab heights, one-block jumps if Allow Jumps is enabled, and drops up to Max Drop (0–3 blocks). Lava, water, fire, cactus, blocked headroom and unloaded terrain are excluded. Airborne entities are routed toward supported ground up to three blocks below them. Swimming, ladders, parkour gaps, opening doors, breaking blocks and flight routes are not simulated.

**Diagonal** (on by default) enables shorter diagonal steps wherever the player's body can fit. Both neighbouring side cells and their transitions must also be walkable: the line cannot cut a blocked corner or skip unsupported ground. The planner uses diagonal distances and costs, rather than treating a diagonal as a full two-block detour. Disable this for cardinal-only routes.

**Reuse Path** (on by default) retains unsimplified walking nodes from the previous route. Every refresh checks the relevant section against fresh block geometry, trims nodes already walked and searches the connectors for moved endpoints. If a middle section becomes blocked, it keeps the valid ends and repairs the gap. An unreachable retained section falls back to a fresh search. Reversed routes are checked in their new direction, including jump/drop limits. Distant, overly long or unaffordable old sections are not reused.

Reuse is now a starting candidate, not a permanent commitment to the old detour. Periodic bounded A* checks look for worthwhile shortcuts from the actual current start to the actual current target, without forcing the new route through old anchors. This also discovers shorter routes when a blocked passage opens.

- **Path Stability**: required percentage saving before replacing a valid reused route (default 8%, range 0–25%). A replacement must also save more than half a block of walking cost to suppress small geometric changes. Set 0 for any strictly shorter route, without that minimum. Higher values retain more of the old shape; lower values favour optimality. Invalid sections are repaired regardless of this setting.
- **Optimize Interval**: minimum interval between shortcut-check attempts (default 750 ms), independent of the faster Update Interval used to validate/repair the route. Both settings are visible when Reuse Path is on. Disabling Reuse Path performs fresh A* every refresh instead.

The shortcut search only explores routes that could beat the required saving. Already near-optimal routes can be certified against the obstacle-free distance without another search. Equal-cost alternatives and insignificant savings keep the existing route, rather than repeatedly switching sides of an obstacle. Until a complete better route is found, the validated complete route remains the candidate; a time/node limit never replaces it with a partial optimization. This balances stability and quality within the configured search limits, not a guarantee of a globally optimal route in arbitrary terrain.

## Performance

Pathfinding is incremental A* on the client tick, never on a render callback or a world-reading background thread. A per-search geometry cache, target scans at most five times per second, cached immutable routes, collinear-point removal and a reusable render buffer keep repeated work small. Search state is dropped on disable, disconnect, world/target changes and geometry-setting changes.

- **Update Interval**: delay before refreshing a finished search, in milliseconds (default 250).
- **Search Budget**: total work units per refresh, shared by old-node validation and all repair/fallback/optimization queue expansions (default 2,048).
- **Nodes per Tick**: maximum work units in one tick (default 64).
- **Time Budget ms**: time slice per tick (default 1.5 ms), checked between work units. A single node's collision checks cannot be interrupted.

Reuse validates at most 512 old nodes and at most one third of Search Budget, reserving room for repairs. No previous collision cache is carried into the next refresh, and route repair does not perform block reads during rendering.

A search also stops after one second. Long render paths are capped at 512 vertices and treated as partial if truncated; dashed-line density is capped on exceptionally long paths. All searches use only already-loaded terrain. For lower overhead, reduce range/nodes/search budget or increase Update Interval. Target movement may take a few ticks to produce a new route; old routes expire rather than staying indefinitely.

Verification: `./gradlew test verifyPathRendering` checks search budgets, diagonals/corner clearance, old-route reuse/repair/fallback, shortcut adoption, switching margins, repeated equal-cost routes, moving-endpoint quality, optimization time/budget exhaustion, wall detours, jump/drop restrictions, target selection, lifecycle cleanup, collision geometry, real-driver line pixels and OpenGL state restoration. Render-check fixtures and timings are saved under `build/path-render-check/`.
