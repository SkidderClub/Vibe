package dev.vibe.inventory;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Vanilla left-click on an occupied slot swaps when the cursor already holds
 * a different stack. That second take is what desynchronises chest looting
 * and inventory sorting, so only a real place or merge is allowed while held.
 */
public final class CursorClicks {
    private CursorClicks() {}

    /** Clicking outside the window drops the carried stack and does not touch another slot. */
    public static boolean dropsCarried(int slot, int button, int mode, ItemStack carried) {
        return slot == -999 && button == 0 && mode == 0 && carried != null && carried.stackSize > 0;
    }

    public static boolean allowed(Container container, int slot, int button, int mode, ItemStack carried) {
        if (carried == null) return true;
        if (dropsCarried(slot, button, mode, carried)) return true;
        if (container == null || slot < 0 || slot >= container.inventorySlots.size()) return false;
        if (mode != 0 || (button != 0 && button != 1)) return false;
        return canPlace(container.getSlot(slot), carried);
    }

    /** Empty slot, or the same item with room left. Anything else would pick the slot stack up. */
    public static boolean canPlace(Slot slot, ItemStack carried) {
        if (slot == null || carried == null || carried.stackSize <= 0 || !slot.isItemValid(carried)) return false;
        ItemStack existing = slot.getStack();
        if (existing == null) return true;
        if (!ItemStack.areItemsEqual(existing, carried) || !ItemStack.areItemStackTagsEqual(existing, carried)) return false;
        return existing.stackSize < Math.min(slot.getSlotStackLimit(), existing.getMaxStackSize());
    }
}
