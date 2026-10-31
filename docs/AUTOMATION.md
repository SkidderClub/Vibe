# Target areas and inventory automation

## Drop inventory commands

**.vomit**, **.clear**, **.clearinv** and **.clearinventory** are aliases for dropping every inventory stack, including equipped armor and items in the inventory crafting grid. They use normal whole-stack inventory drop clicks with short spacing. The operation stops if you change worlds or open another container. Each selected slot is visited once, so picked-up drops do not create an endless loop.

## Target Area

In **Combat → Targets**, enable **Target Area** to restrict automated combat targets to a 3D selection. It is off by default.

- **.pos1** and **.pos2** select the block at your feet. Both endpoint blocks are included; corner order does not matter.
- **.pos1 x y z** and **.pos2 x y z** accept explicit whole-block coordinates.
- The six **Pos1/Pos2 X/Y/Z** fields in ClickGUI accept exact typed coordinates.
- **Invert Target Area** accepts targets outside the selection.
- **Display Target Area** highlights the complete selection with translucent faces and an outline. Blue marks a normal area; amber marks an inverted exclusion area.

The existing player, mob, invisibility, friend and teammate filters continue to apply. ESP visibility stays independent of the combat area.

## AutoRespawn

**World → AutoRespawn** uses the normal respawn action once per death after its configurable delay. It waits for the death screen and does not attempt to respawn in hardcore worlds.

## AutoBuff

**Combat → AutoBuff** has independent health thresholds and delay ranges for **Auto Pot**, **Auto Soup**, **Auto Head**, **Auto Eat** and **Auto Gapple**. Health is measured in Minecraft health points: 20 points is 10 hearts. Auto Eat also has a hunger threshold. Absorption can optionally count toward the health threshold.

Potions must have beneficial effects. Instant healing is used when health is missing; timed effects are refreshed when absent, weaker or nearly expired. Splash potions wait for downward MoveFix rotation to reach the normal movement update before use. Grounded-only throwing is enabled by default.

Soup recognizes mushroom stew. Heads recognize player-head items and golden apples named as heads. The server supplies custom head/soup healing behavior; food, gapples and drinkable potions retain their normal use duration.

Slot selection, use and restoration have delays. Vanilla right-button input handles the actual use and held-slot synchronization. A manual slot change or right click interrupts automation. Unresponsive items get a temporary backoff so other modes can run.

**Auto Eat Walk Backwards** and **Auto Gapple Walk Backwards** are independent options, both off by default. They move backwards while the item is actually being eaten and stop when use ends or is interrupted. Movement goes through the normal MoveFix input path.

**Auto Refill** supports:

- **Open Inventory:** open the normal inventory, perform delayed transfers and close the inventory opened by the module.
- **Spoof:** send the vanilla inventory-open notification, transfer through the normal player controller and close the session without displaying the inventory.
- **Inventory Only:** refill while the player has opened their inventory; leave that screen open.

The Inventory Editor now includes **Soup**, **Head** and **Buff Potion** placeholders. Supply categories support several assignments: for example, put **Sword** in slot 1 and **Soup** in slots 2–9. Correctly filled assignments are preserved instead of being shuffled between slots or discarded by the general food limit. Ignore slots stay protected. When no relevant hotbar layout exists, refill uses an empty unassigned slot.

Refill holds a shared inventory session while it fills the requested slots. Inventory Manager waits for that session, and a refill waits for Inventory Manager's pending pickup operation. Every transfer uses a normal controller click with the shared minimum interval and tick spacing.
Refill waits while moving by default. **Refill While Moving** changes that policy. A manually carried stack pauses transfers, and disabling the module leaves a manually carried item and its inventory screen intact.

## Crafter

**World → Crafter** acts only in an open crafting table. Select the equipment and either a specific material or **Best Available**. Existing AutoCrafter profiles and keybinds migrate automatically.

1. A necessary sword takes priority. Missing sticks are crafted from planks; logs can first be converted to planks.
2. Armor is considered from both the inventory and equipped slots. An equal or superior material prevents a duplicate craft.
3. The next armor piece is the affordable upgrade that gains the most armor points. Equal gains prefer the cheaper recipe.

Start, ingredient clicks, output collection and recipe changes have separate delays. Ingredients are placed one at a time, with normal pickup/right-click/return interactions, then the verified vanilla recipe output is collected. Full inventories pause output collection. A manually populated grid is left alone, and manual clicks interrupt the current plan.
