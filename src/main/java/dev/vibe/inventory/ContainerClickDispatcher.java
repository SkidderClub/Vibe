package dev.vibe.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import org.lwjgl.input.Mouse;

/**
 * One normal PlayerController window interaction at a time.
 *
 * This deliberately routes every action through {@code windowClick}, the
 * same controller method used by a real inventory click. It validates the
 * live window/slot before every send, and permits no more than one controller
 * interaction per client tick. Modules retain their existing action choice;
 * this class only serialises the resulting vanilla UI interaction.
 */
public final class ContainerClickDispatcher {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private long nextAllowed;
    private int activeWindow = -1;
    private int lastDispatchTick = Integer.MIN_VALUE;
    private int settleUntilTick = Integer.MIN_VALUE;

    public boolean click(Container container, int slot, int button, int mode, long delayMs) {
        if (container == null || minecraft.thePlayer == null || minecraft.playerController == null
                || slot < 0 || slot >= container.inventorySlots.size()
                || !(minecraft.currentScreen instanceof GuiContainer)
                || minecraft.thePlayer.openContainer != container
                || container.windowId != minecraft.thePlayer.openContainer.windowId) {
            reset();
            return false;
        }
        // Do not interleave an automated controller call with a real mouse
        // click.  Vanilla itself serializes GUI actions on the input pass;
        // waiting here lets the user's click fully settle first.
        if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) {
            return false;
        }
        if (!isSupportedVanillaClick(button, mode)) {
            return false;
        }
        long now = System.currentTimeMillis();
        int tick = minecraft.thePlayer.ticksExisted;
        if (activeWindow != -1 && activeWindow != container.windowId) {
            // The carried stack/transaction state belongs to the previous GUI.
            reset();
        }
        if (now < nextAllowed || lastDispatchTick == tick || tick < settleUntilTick) return false;
        activeWindow = container.windowId;
        minecraft.playerController.windowClick(container.windowId, slot, button, mode, minecraft.thePlayer);
        lastDispatchTick = tick;
        // Let the normal GUI/container state settle before a subsequent
        // click. This does not synthesize packets: it simply spaces calls to
        // the same PlayerController method vanilla uses for UI interaction.
        nextAllowed = now + Math.max(125L, delayMs);
        settleUntilTick = tick + 2;
        return true;
    }

    private boolean isSupportedVanillaClick(int button, int mode) {
        if (mode == 0 || mode == 1) {
            return button == 0 || button == 1;
        }
        if (mode == 2) {
            return button >= 0 && button <= 8;
        }
        return mode == 4 && (button == 0 || button == 1);
    }

    public void reset() {
        activeWindow = -1;
        nextAllowed = 0L;
        lastDispatchTick = Integer.MIN_VALUE;
        settleUntilTick = Integer.MIN_VALUE;
    }
}
