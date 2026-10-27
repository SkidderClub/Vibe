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
normal forward, backward and strafe input keeps movement directed along the route.
Turning no longer pauses walking. At close range, AutoWalk follows the opponent's
actual position, including within the same block, and keeps pushing toward them.
There is no Stop Distance. Dead or removed targets are replaced by Path's next
eligible target.

**Auto Jump** handles one-block steps. Turning it off also disables jumps in the
requested route; it does not change Path's saved Allow Jumps setting. **Sprint**
uses vanilla food, sneak, item-use and collision conditions. No speed, motion or
position packets are fabricated. Input enters before MoveFix correction, so other
modules' silent rotations can correct it normally.

Menus, paused games, death, Freecam and another producer's forced movement pause
AutoWalk. Missing, stale or newly blocked routes stop movement until a usable
route arrives. Searches remain bounded and on the client tick; rendering only
draws the cached route. AutoWalk never attacks targets and never holds physical
key bindings, so disabling it cannot leave W or jump pressed.
