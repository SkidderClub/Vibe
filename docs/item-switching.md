# Automatic hotbar selection

All native held-item selectors share `ItemSwitching` settings and `HotbarSwitches` ownership:

| Module | Default |
| --- | --- |
| AutoTool | Basic |
| PickenSwitch | Basic |
| AutoBuff | Basic |
| Projectile Aura | Basic |
| Scaffold | Silent |
| Hypixel Murder Mystery Auto Weapon | Silent |
| BedAura | Basic |
| Chatter Interact | Basic |
| AntiAFK Switch Items | Basic |

**Basic** changes the selected hotbar slot normally. **Silent** keeps the original visible slot and hand while native actions and player item-use logic use the server-selected stack. Modules retain their existing selection, delay, use and return timing. Basic AntiAFK and Chatter selections remain selected; Silent selections restore their source. AutoTool and BedAura keep their Switch Back preferences in Basic mode.

Each module has Slot Highlight and Slot Highlight Color controls. The same state draws over the vanilla hotbar and every Custom Hotbar theme, including moved or resized HUD elements. Very short switches leave a 240 ms highlight so an instantaneous projectile or attack is still visible.

The service owns overlapping selections. Scaffold, AutoBuff, Murder Mystery, Projectile Aura, Chatter, BedAura, PickenSwitch, AutoTool and AntiAFK have descending priority. Releasing a temporary action resumes the previous owner. Explicit user selection cancels automatic leases; disconnects, respawns, profile loads and module disable clear stale ownership. Lower-priority users must wait when another module owns their action.

`PlayerControllerMP.syncCurrentPlayItem` remains the sole native C09 writer. Controller use/attack/mining methods, the input pass and local-player update use exception-safe scopes. They expose the actual action stack, including captured right-click arguments and continuous food use, then restore the visible slot before rendering. A module must acquire its lease before using vanilla input/controller actions and close it when finished; it must not write `inventory.currentItem` or manufacture held-slot packets.

Container operations such as InventoryManager, Crafter, refill and ChestStealer continue using the inventory transaction system: moving inventory contents is separate from selecting the held item. The scripting inventory selection API uses the shared Basic selector; its explicit raw-packet API remains available.

Old AutoTool/PickenSwitch Mode, Scaffold Spoof Slot, Silent Slot Color and Hypixel Silent murderer preferences migrate when profiles load. Existing explicit Item Switching and Slot Highlight Color values take precedence.
