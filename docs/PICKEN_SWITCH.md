# Picken Switch

Combat module for Minecraft 1.8.9. Hold a sword or tool with higher base damage and keep an inferior item with better Knockback or Fire Aspect in your hotbar. Your normal attack triggers the swap. The module never generates attacks of its own.

## Modes and settings

- **Basic:** visibly selects the enchanted item for the attack, then restores the weapon.
- **Silent:** keeps the displayed weapon selected while the server uses the enchanted slot. An orange hotbar outline shows that slot; its color is configurable.
- **Enchantment Priority:** Both, Knockback, or Fire Aspect. Only an improvement over the held weapon qualifies.
- **Enchanted Item:** automatically choose the best qualifying hotbar item, or restrict selection to one slot.
- **Weapon Warmup:** hold the weapon steadily for at least two ticks before a swap.
- **Cooldown:** minimum ticks between swaps; default four.
- **Restore Delay:** one to four ticks; default one. Restoring on a later tick avoids appending a slot change directly after an attack.
- **Players Only**, **Respect Targets**, **Skip Fire On Burning Targets**, and **On Ground Only** filter when to swap.

Manual slot changes take precedence over restoration. Disabling the module restores a pending selection at the next client tick. World/player changes clear stale ownership. The module waits while AutoTool or Scaffold owns a silent slot and skips swaps while using items, breaking blocks, or viewing a GUI.

## Why the swap happens before the hit

In vanilla 1.8.9, equipment attributes update during the entity tick. `EntityPlayer.attackTargetEntityWithCurrentItem` reads the cached attack-damage attribute, but reads enchantments from the currently held stack. A slot change immediately before the attack can therefore combine the previous weapon's base damage with the new item's enchantments. Changing items after a processed hit cannot change that hit.

The hook runs inside the ordinary controller attack path, before `syncCurrentPlayItem` and C02. Vanilla sends the deduplicated C09 slot change. Silent mode temporarily presents that item to the local action, restores the displayed slot in `finally`, and keeps the server selection until the next restoration tick. The old weapon's Sharpness is not retained: enchantments come from the newly held item.

The regression test executes Minecraft's actual attack method: a cached diamond sword attribute yields 8 damage while a lower-damage item supplies Knockback II and Fire Aspect II. Updating the attributes returns the inferior item's damage to 1. Hook tests cover ordering, Basic/Silent restoration, manual changes, warmup and exception cleanup.

## Grim compatibility scope

Reviewed Grim commit `8eb5f2809591c891deb4958bb2927844871e0600`. Its [PacketOrderB check](https://github.com/GrimAnticheat/Grim/blob/8eb5f2809591c891deb4958bb2927844871e0600/common/src/main/java/ac/grim/grimac/checks/impl/packetorder/PacketOrderB.java) allows one held-item change between a 1.8 swing and attack. Its experimental [PacketOrderE check](https://github.com/GrimAnticheat/Grim/blob/8eb5f2809591c891deb4958bb2927844871e0600/common/src/main/java/ac/grim/grimac/checks/impl/packetorder/PacketOrderE.java) detects slot changes during conflicting actions, including after an attack. This implementation retains vanilla swing/attack ordering and defers restoration to a subsequent tick.

These are source review and local regression results, not a live Grim-server certification. Server patches, protocol translation, latency and other enabled modules can affect both the old attribute mechanic and anti-cheat results. No universal zero-flag guarantee is made.

## Verification

```text
gradle test --tests "*PickenSwitch*" --tests "*AutoToolHooksTest" --tests "*RangeSettingTest"
```

Range controls now hold the chosen endpoint throughout a drag. Crossing an endpoint pushes the other endpoint along; overlapping handles separate according to the next drag direction. This behavior applies across the Skeet, NeverLose, Xanax and Augustus controls.
