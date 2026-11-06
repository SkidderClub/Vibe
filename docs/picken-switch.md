# Picken Switch and Minecraft 1.8.9

The timing was checked against the locally cached Minecraft/Forge 1.8.9 classes,
decompiled for inspection into `build/picken-vanilla-source`. Minecraft source is
not bundled into Vibe.

The relevant vanilla paths are:

- `EntityLivingBase.onUpdate`: detects equipment changes and removes/applies
  the old/new stacks' attribute modifiers. This supplies cached attack damage.
- `NetHandlerPlayServer.processHeldItemChange`: C09 changes
  `inventory.currentItem` and marks the player active. It does not refresh those
  equipment attributes.
- `EntityPlayer.attackTargetEntityWithCurrentItem`: reads cached attack damage,
  then reads damage enchantments, Knockback and Fire Aspect from the current
  stack. Critical damage multiplies the cached base before enchantment damage
  is added.
- `PlayerControllerMP.attackEntity`: synchronizes the selected slot, sends C02
  ATTACK, then performs local prediction.

Vibe keeps the damage weapon equipped between hits. When an enchanted utility
item is held, **Auto Equip Damage Weapon** can prepare a stronger hotbar sword
or tool first. The warmup allows the server's equipment update to run.

For each eligible hit, Picken selects the utility immediately before vanilla's
slot synchronization. It releases the override and synchronizes the damage
weapon again before returning from the controller, including on local prediction
failure. The outgoing sequence is **C09 utility, C02 attack, C09 weapon**.
The former restore delay is removed because holding the utility across equipment
updates can replace the cached damage with its weaker attributes.

Basic changes the local stack during that call. Silent leaves the displayed
stack alone and uses the same server-side transaction. Neither retains a utility
slot override between ticks. The optional cooldown defaults to zero.

Only cached attribute damage is carried over. Sharpness, Smite and Bane of
Arthropods come from the attack item; enchantments from both stacks are not
combined. Server implementations that refresh weapon attributes on a slot change
or recalculate damage themselves can remove this vanilla behavior. A client also
cannot guarantee which server tick receives a packet group.

Tests execute the actual transformed controller's slot cache/C02 writer and feed
the resulting selections into vanilla's real attack method. A diamond sword
with Sharpness V plus a utility with Sharpness II, Knockback II and Fire Aspect II
produces 8 cached base damage + 2.5 utility enchantment damage, Knockback II and
8 seconds of fire. Returning to the sword restores its ordinary 14.25 damage.
