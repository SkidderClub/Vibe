# Rewound Pit

Enable **Rewound Pit** in **Meme**. Both **Auto Upgrade** and **Auto Prestige**
are selected by default. It reads Level, Gold and Prestige from the server's
sidebar, including team prefixes/suffixes. It requires a Rewound connection or
the `rewound.cc` sidebar footer, and does not run purchases on unrelated servers.

Configure the combat cuboid in **Targets** (or use `.pos1` and `.pos2`). Spawn
is the complement of that region; **Invert Target Area** reverses both combat
and spawn. **Always Use TargetArea** defaults on and makes the configured area
effective for combat and its optional highlight without changing the saved
Target Area toggle. Disabling Rewound Pit removes that temporary enforcement.

When an affordable perk is missing, the module finds the villager with the
**Upgrades / Permanent** armor-stand labels. It temporarily owns AutoWalk's
shared route and walking input, even if AutoWalk itself is off. Routes stay
inside spawn. While interacting or using menus, walking and combat targeting
pause; ChestStealer yields the menu. Completion, cancellation, timeout and
disabling the module all release ownership. AutoWalk resumes its previous mode
and enabled state.

Each **Perk Slot** setting offers all 14 listed perks and **None**. Defaults:

| Slot | Default | Gold | Slot unlock |
| --- | --- | ---: | --- |
| 1 | Golden Heads | 500 | Level 10 |
| 2 | Strength-Chaining | 2,000 | Level 35 |
| 3 | Lucky Diamond | 4,000 | Level 70 |
| 4 | Safety First | 3,000 | Prestige III and the server-confirmed Extra Perk Slot unlock |

Perks also retain their individual minimum levels. The module reads the actual
slot contents, skips equipped perks, avoids duplicates, and verifies the new
equipped item after the chooser returns to Permanent upgrades. It uses ordinary
controller window clicks through the shared inventory dispatcher, with at least
125 ms and two ticks between actions. It closes its own menu afterwards. Failed
or incomplete transactions time out and retry later; they are not counted as
successful purchases.

Level **120** gives prestige priority and suspends perk spending. At spawn,
the module finds **Prestige / Reset & Renown**, checks the diamond's server
requirements (including gold earned this prestige and cooldown), opens
**Confirm prestige**, and clicks **Confirm** on the emerald. It closes the menu
and waits for the sidebar's level/prestige reset before releasing the hold.
Renown upgrades themselves are not purchased automatically.

**Filter Messages** defaults on. It hides the uppercase server notifications
for kills/multikills, assists, streaks/megastreaks, bounties and Pit level-ups,
while preserving ordinary player chat, command responses and action-bar text.

**Auto Unstuck** defaults on, with **Unstuck Delay (s)** set to **60**. Outside
spawn, no player attack or received hurt for that interval triggers `/spawn`.
The timer restarts after sending, on combat activity, on changing worlds, and
while at spawn; it never sends the command every tick.

Regression coverage includes the sidebar format, perk and slot eligibility,
server-acknowledged purchases, rejected purchases/timeouts, prestige priority
and reset confirmation, inversion, temporary area enforcement, manual menus,
native interaction ordering and collision-based navigation around combat.
