# Targetstrafe, Auto Head, NCP tower and pause menus

## Targetstrafe

- **Only While Killaura** and **Only While Speed** independently require those
  modules to be enabled. Both default off. The aura check includes AIAura,
  which uses the shared Killaura controller.
- **Target Range** selects candidates; **Radius** controls where orbiting starts.
  Targets outside the configured radius do not pull the player toward them.
  An existing orbit has a 0.35-block exit margin to avoid toggling at its edge.
- Steering keeps a continuous radial correction instead of rounding it to eight
  keyboard directions. The same world vector is retained with silent rotations
  and when Rewound Speed supplies horizontal speed.
- **Automatic** direction reverses once on a new horizontal wall contact, or
  before a predicted wall/unsupported path. A held strafe key can choose a
  direction when newly pressed; it does not continuously undo an automatic
  reversal. The forecast uses native player collision physics. If neither side
  is safe, the module requests stopped input.

## Auto Head

**Auto Head Delay (ms)** is the preparation delay before switching and issuing
the native use edge. Heads do not additionally wait for the shared slot-switch
delay or a two-tick selection window.

Once the native controller queues the head's use packet, the head lease and
input are released at the end of that input pass. The previous hotbar/tool
selection is restored without waiting for server health or stack updates, or
the shared restore delay. Any local eating state from a named Golden Head is
ended through the native stop-use controller.

**Auto Head Pause (ms)** independently waits after that interaction before
starting another head's preparation delay. It defaults to 500–750 ms. Other
AutoBuff modes retain their own delays. Shared switch/restore controls are
hidden when Auto Head is the only selected mode.

## Scaffold NCP tower

NCP adjustments apply only while holding jump, standing still horizontally,
and grounded or moving upward. Walking, strafing, residual horizontal movement,
and falling do not use NCP tower adjustments. Rewound Speed yields its jump
control during a stationary NCP tower, including the normal-jump fallback.

**Stop tower on flag** defaults on and is visible with NCP selected. A server
position correction while towering suppresses NCP adjustments for three seconds;
the held jump key continues through normal jumping. Further corrections during
that fallback restart the three-second interval. Correction handling runs on
the client thread and does not require Flag Detector to be enabled.

## No ESC Menu

The new World module blocks automatic pause-menu requests, including focus loss
when tabbing away. **Allow manual** defaults on, allowing the real Escape-key
branch to open the menu. Turning it off blocks that branch too. The distinction
uses a scoped native call path, not a possibly stale keyboard event. The module
does not rewrite `pauseOnLostFocus` or interfere with closing other screens.

## Verification

Regression tests exercise radius/dependency gates, wall contact latching, future
void reversal, fractional steering through MoveFix and Rewound Speed, instant
head C09/C08/restore ordering, unchanged server-side head stacks, named-food
head cleanup, silent tool restoration, stationary/upward tower guards, the
exact three-second fallback, and native pause-menu behavior.

Mapped and obfuscated Minecraft/controller bytecode is checked for input/use
hook placement and valid frames. The production Forge/OptiFine probe verifies
the final native classes contain these hooks. These checks do not imply a live
multiplayer-server test.
