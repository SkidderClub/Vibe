package dev.vibe.inventory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Container;

/** A finite, player-inventory-only sequence of vanilla whole-stack drop clicks. */
public final class InventoryDropper {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Consumer<String> feedback;
    private final LongSupplier clock;
    private final ContainerClickDispatcher clicks;
    private final PlayerInventorySession session;
    private final Deque<Integer> slots = new ArrayDeque<Integer>();
    private EntityPlayerSP player;
    private Object world;
    private Container container;
    private boolean opened;
    private long requestedAt;
    private int dropped;

    public InventoryDropper(Consumer<String> feedback) { this(feedback, System::currentTimeMillis); }
    InventoryDropper(Consumer<String> feedback, LongSupplier clock) {
        this.feedback = feedback; this.clock = clock;
        clicks = new ContainerClickDispatcher(clock); session = new PlayerInventorySession(clicks);
    }

    public void start() {
        if (player != null) { feedback.accept("Inventory clearing is already running."); return; }
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                || !mc.thePlayer.isEntityAlive() || mc.thePlayer.isSpectator()) {
            feedback.accept("Join a world before clearing your inventory."); return;
        }
        if (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
                || (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat) && !(mc.currentScreen instanceof GuiInventory))) {
            feedback.accept("Close the other menu before clearing your inventory."); return;
        }
        if (mc.thePlayer.inventory.getItemStack() != null) {
            feedback.accept("Put the item on your cursor back into a slot first."); return;
        }
        if (PlayerInventorySession.isBusy()) { feedback.accept("Another inventory action is running. Try again when it finishes."); return; }
        Container inventory = mc.thePlayer.inventoryContainer;
        // Slot zero is the crafting result: its ingredients are dropped from slots 1–4 instead.
        // Slots 5–8 are equipped armor, 9–35 storage, and 36–44 the hotbar.
        for (int slot = 1; slot < inventory.inventorySlots.size(); slot++)
            if (inventory.getSlot(slot).getHasStack()) slots.addLast(slot);
        if (slots.isEmpty()) { feedback.accept("Your inventory is already empty."); return; }
        player = mc.thePlayer; world = mc.theWorld; container = inventory;
        requestedAt = clock.getAsLong(); dropped = 0;
        feedback.accept("Dropping " + slots.size() + " inventory stacks, including equipped armor.");
    }

    public void tick() {
        if (player == null) return;
        if (player != mc.thePlayer || world != mc.theWorld || mc.playerController == null
                || !player.isEntityAlive() || player.isSpectator() || player.openContainer != container
                || player.inventory.getItemStack() != null) {
            stop("Inventory clearing cancelled because your inventory or world changed."); return;
        }
        if (!opened) {
            // The local command is submitted while GuiChat is still closing.
            if (mc.currentScreen instanceof GuiChat && clock.getAsLong() - requestedAt < 3000) return;
            if (!session.open("Spoof")) { stop("Inventory clearing cancelled: another menu or inventory action is active."); return; }
            opened = true;
        }
        if (!session.valid()) { stop("Inventory clearing cancelled because the inventory was closed or another menu opened."); return; }
        while (!slots.isEmpty() && !container.getSlot(slots.peekFirst()).getHasStack()) slots.removeFirst();
        if (slots.isEmpty()) { stop("Dropped " + dropped + " stacks from your inventory."); return; }
        if (clicks.click(container, slots.peekFirst(), 1, 4, 125)) {
            slots.removeFirst(); dropped++;
            // Never revisit a dropped slot: picking up an item must not create an endless drop loop.
            if (slots.isEmpty()) stop("Dropped " + dropped + " stacks from your inventory.");
        }
    }

    private void stop(String message) {
        session.close(); clicks.reset(); slots.clear(); player = null; world = null; container = null; opened = false;
        feedback.accept(message);
    }
}
