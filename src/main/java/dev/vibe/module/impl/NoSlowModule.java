package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import org.lwjgl.input.Keyboard;

/** Restores the local movement input lost while using selected vanilla items. */
public final class NoSlowModule extends Module {

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Vanilla", "Vanilla"));
    private final MultiSelectSetting items = addSetting(new MultiSelectSetting("Items",
            Arrays.asList("Swords", "Bow", "Consumables", "Food", "Potions", "Other"),
            Arrays.asList("Swords", "Bow", "Consumables", "Food", "Potions")));
    private EntityPlayerSP hookedPlayer;

    public NoSlowModule() {
        super("NoSlow", "Preserve movement while using selected items", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    public boolean applies() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isEnabled() || !mode.is("Vanilla") || minecraft.thePlayer == null || !minecraft.thePlayer.isUsingItem()
                || minecraft.thePlayer.getItemInUse() == null) {
            return false;
        }
        Item item = minecraft.thePlayer.getItemInUse().getItem();
        if (item instanceof ItemSword) return items.isSelected("Swords");
        if (item instanceof ItemBow) return items.isSelected("Bow");
        if (item instanceof ItemFood) return items.isSelected("Food") || items.isSelected("Consumables");
        if (item instanceof ItemPotion) return items.isSelected("Potions") || items.isSelected("Consumables");
        return items.isSelected("Other");
    }

    /**
     * Forge 1.8.9 has no InputUpdateEvent.  Wrapping the player's normal
     * MovementInput lets us adjust the values at the exact point Minecraft
     * reads the keyboard, immediately before vanilla applies its 0.2 use-item
     * multiplier.  The original MovementInput remains the sole owner of key
     * state, so this does not synthesize movement or packets.
     */
    public void installInputHook() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || player == hookedPlayer || player.movementInput instanceof VibeMovementInput) {
            return;
        }
        player.movementInput = new VibeMovementInput(player.movementInput, this);
        hookedPlayer = player;
    }

    private static final class VibeMovementInput extends MovementInput {

        private final MovementInput vanillaInput;
        private final NoSlowModule module;

        private VibeMovementInput(MovementInput vanillaInput, NoSlowModule module) {
            this.vanillaInput = vanillaInput;
            this.module = module;
        }

        @Override
        public void updatePlayerMoveState() {
            vanillaInput.updatePlayerMoveState();
            moveStrafe = vanillaInput.moveStrafe;
            moveForward = vanillaInput.moveForward;
            jump = vanillaInput.jump;
            sneak = vanillaInput.sneak;
            if (module.applies()) {
                // EntityPlayerSP multiplies these by 0.2 later in this same
                // update. Five times the legitimate key input preserves the
                // normal walk vector without touching packet ordering.
                moveForward *= 5.0F;
                moveStrafe *= 5.0F;
            }
        }
    }

    public ModeSetting getMode() { return mode; }
    public MultiSelectSetting getItems() { return items; }
}
