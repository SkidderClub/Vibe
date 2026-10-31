package dev.vibe.inventory;

import dev.vibe.Vibe;
import dev.vibe.module.impl.world.InventoryManagerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C16PacketClientStatus;

/** A bounded player-inventory session. Hidden mode still uses vanilla open/close notifications. */
public final class PlayerInventorySession implements AutoCloseable {
    private static PlayerInventorySession active;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ContainerClickDispatcher owner;
    private EntityPlayerSP player;
    private Object world;
    private GuiScreen openedScreen;
    private boolean spoofed;
    public PlayerInventorySession(ContainerClickDispatcher owner) { this.owner = owner; }
    public boolean open(String mode) {
        if (active == this) return valid();
        if (active != null) { if (!active.valid()) active.close(); else return false; }
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
                || mc.thePlayer.inventory.getItemStack() != null
                || (mc.currentScreen != null && !(mc.currentScreen instanceof GuiInventory))) return false;
        InventoryManagerModule manager = Vibe.getInstance() == null ? null : Vibe.getInstance().getModuleManager().getModule(InventoryManagerModule.class);
        if (manager != null && manager.isEnabled() && manager.hasPendingInteraction()) return false;
        if (mode.equals("Inventory Only") && !(mc.currentScreen instanceof GuiInventory)) return false;
        player = mc.thePlayer; world = mc.theWorld;
        if (!(mc.currentScreen instanceof GuiInventory)) {
            player.sendQueue.addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
            if (mode.equals("Open Inventory")) {
                openedScreen = new GuiInventory(player); mc.displayGuiScreen(openedScreen);
            } else spoofed = true;
        }
        active = this; return true;
    }
    public boolean valid() {
        return active == this && mc == Minecraft.getMinecraft() && player == mc.thePlayer && world == mc.theWorld
                && player.openContainer == player.inventoryContainer
                && (spoofed ? mc.currentScreen == null : mc.currentScreen instanceof GuiInventory)
                && (openedScreen == null || mc.currentScreen == openedScreen);
    }
    public static boolean isBusy() {
        if (active != null && !active.valid()) active.close();
        return active != null;
    }
    static boolean owns(ContainerClickDispatcher dispatcher) {
        return isBusy() && active.owner == dispatcher;
    }
    @Override public void close() {
        if (active != this) return;
        boolean closeWindow = valid() && player.inventory.getItemStack() == null && (spoofed || openedScreen != null);
        active = null;
        if (closeWindow) player.closeScreen();
        player = null; world = null; openedScreen = null; spoofed = false;
    }
}
