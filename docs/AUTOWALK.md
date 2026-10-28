# AutoWalk

Enable **Movement → AutoWalk**, or use `.toggle AutoWalk`.

- **Forward** supplies the same walking input as holding W. Mouse steering,
  strafing, sneaking and holding S still behave normally.
- **Path** follows the main opponent route from **Visual → Path**. It uses Path's
  target type, range, Friends/Teams/entity filters, collision-aware planning and
  tick budgets. Path does not need to be enabled: AutoWalk requests its route
  without changing its toggle or saved settings. **Show Path** displays that same
  route. A separately enabled Path module keeps its own line visible.

**Yaw Speed** and **Pitch Speed** are degrees per second at the normal 20-tick
rate. **Rotate Pitch** is optional. The camera turns toward each waypoint while
forward stays held and sideways input helps the curve. The controller looks ahead
through corners and rejoins the route while running, allowing small deviations
from the drawn line. At close range, AutoWalk follows the opponent's
actual position, including within the same block, and keeps pushing toward them.
There is no Stop Distance. Dead or removed targets are replaced by Path's next
eligible target.

**Auto Jump** handles one-block steps. Turning it off also disables jumps in the
requested route; it does not change Path's saved Allow Jumps setting. **Sprint**
uses vanilla food, sneak, item-use and collision conditions. No speed, motion or
position packets are fabricated. Input enters before MoveFix correction, so other
modules' silent rotations can correct it normally.

Both modes hold normal jump input in water to swim upward and stay afloat. This
works with Auto Jump off, and Path mode still surfaces while waiting for a route
or target. Routes can enter water, cross it, ascend a submerged column and leave
onto a bank. Swimming uses vanilla buoyancy and collision, without changing motion
or teleporting the player.

Menus, paused games, death, Freecam and another producer's forced movement pause
AutoWalk. With an eligible target, missing or stale routes no longer release forward:
AutoWalk keeps steering while the bounded planner refreshes the route. Searches remain on the client tick; rendering only
draws the cached route. AutoWalk never attacks targets and never holds physical
key bindings, so disabling it cannot leave W or jump pressed.
