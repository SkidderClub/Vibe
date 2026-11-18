package dev.vibe.module.impl.client;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import org.lwjgl.input.Keyboard;

/** Retains the live server window, not a snapshot of a shop's contents. */
public final class ShopSaverModule extends Module {
    private GuiChest saved;
    private GuiScreen hiding;
    private EntityPlayerSP player;
    private Object world;

    public ShopSaverModule() {
        super("Shop Saver", "Hide chest shops and reopen them with your inventory key", Category.CLIENT, Keyboard.KEY_NONE);
    }

    public static boolean hideHook(Object closingPlayer) {
        ShopSaverModule module = instance();
        return module != null && module.hide(closingPlayer);
    }

    public static boolean keepContainerHook(Object screen) {
        ShopSaverModule module = instance();
        return module != null && module.hiding == screen;
    }

    private boolean hide(Object closingPlayer) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || closingPlayer != mc.thePlayer || mc.theWorld == null
                || !mc.thePlayer.isEntityAlive() || !(mc.currentScreen instanceof GuiChest)) return false;
        GuiChest chest = (GuiChest) mc.currentScreen;
        if (chest.inventorySlots != mc.thePlayer.openContainer
                || chest.inventorySlots == mc.thePlayer.inventoryContainer) return false;
        saved = chest; player = mc.thePlayer; world = mc.theWorld;
        hiding = chest;
        try { mc.displayGuiScreen(null); }
        finally { hiding = null; }
        if (mc.currentScreen == chest) { clear(); return false; }
        return true;
    }

    public GuiScreen opening(GuiScreen requested) {
        if (!valid()) { clear(); return requested; }
        return requested instanceof GuiInventory ? saved : requested;
    }

    public void tick() { if (!valid()) clear(); }

    private boolean valid() {
        Minecraft mc = Minecraft.getMinecraft();
        return isEnabled() && saved != null && player == mc.thePlayer && world == mc.theWorld
                && player.isEntityAlive() && player.openContainer == saved.inventorySlots;
    }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean close = saved != null && player == mc.thePlayer && world == mc.theWorld
                && player.openContainer == saved.inventorySlots;
        clear();
        // Return the server and client to the ordinary player inventory exactly once.
        if (close) mc.thePlayer.closeScreen();
    }

    private void clear() { saved = null; player = null; world = null; }

    private static ShopSaverModule instance() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null
                : vibe.getModuleManager().getModule(ShopSaverModule.class);
    }
}
