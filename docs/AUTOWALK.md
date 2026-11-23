# AutoWalk

Enable **Movement → AutoWalk**, or use `.toggle AutoWalk`.

- **Forward** supplies the same walking input as holding W. Mouse steering,
  strafing, sneaking and holding S still behave normally.
- **Path → Player** (default) follows the main opponent route from **Visual → Path**. It uses Path's
  target type, range, Friends/Teams/entity filters, collision-aware planning and
  tick budgets. Path does not need to be enabled: AutoWalk requests its route
  without changing its toggle or saved settings. **Show Path** displays that same
  route. A separately enabled Path module keeps its own line visible.
- **Path → Position** walks to **Position X/Y/Z** and stops on arrival. Set these
  coordinates in ClickGUI, use **`.pos x y z`**, or use **`.pos`** to save your
  current block. This is independent of Targets' `.pos1` and `.pos2` commands.
  **Patrol** circles that position continuously at **Patrol Radius**, using the
  same collision-aware planner for the successive points. Coordinates are saved
  in normal profiles; the command does not enable AutoWalk or change its mode.

Position navigation searches in bounded segments toward distant coordinates.
It waits for a safe route when the destination or the next segment is unavailable;
it does not load distant chunks or steer blindly toward unsupported ground.

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

Menus pause AutoWalk unless **GUI Move → Allow AutoWalk** is enabled for that
screen's selected GUI category. GUI Move also offers **Allow Killaura**, which
permits the normal combat rotation and click pipeline in its selected GUIs.
Both permissions default off and pause while typing or binding keys. Paused games,
death, Freecam and another producer's forced movement still pause AutoWalk.
With an eligible player target, missing or stale routes no longer release forward:
AutoWalk keeps steering while the bounded planner refreshes the route. Searches remain on the client tick; rendering only
draws the cached route. AutoWalk never attacks targets and never holds physical
key bindings, so disabling it cannot leave W or jump pressed.
