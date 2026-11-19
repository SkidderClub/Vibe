package dev.vibe.module.impl.client;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import java.util.Locale;
import org.lwjgl.input.Keyboard;

/** Retains the live server window, not a snapshot of a shop's contents. */
public final class ShopSaverModule extends Module {
    private static final String[] SHOP_TITLES = {"Quick Buy", "Blocks", "Melee", "Armor", "Tools", "Ranged", "Potions", "Utility"};
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
        if (!(chest.inventorySlots instanceof ContainerChest) || !isShop((ContainerChest)chest.inventorySlots)
                || chest.inventorySlots != mc.thePlayer.openContainer
                || chest.inventorySlots == mc.thePlayer.inventoryContainer) return false;
        saved = chest; player = mc.thePlayer; world = mc.theWorld;
        hiding = chest;
        try { mc.displayGuiScreen(null); }
        finally { hiding = null; }
        if (mc.currentScreen == chest) { clear(); return false; }
        return true;
    }

    /** Match the supplied shop menus, never a normal storage window with a similar name. */
    public static boolean isShop(ContainerChest container) {
        IInventory inventory = container.getLowerChestInventory();
        if (inventory == null || inventory.getSizeInventory() != 54 || !inventory.hasCustomName()
                || inventory instanceof net.minecraft.tileentity.TileEntityChest
                || inventory instanceof net.minecraft.inventory.InventoryLargeChest
                || inventory instanceof net.minecraft.inventory.InventoryEnderChest) return false;
        String title = plain(inventory.getDisplayName().getUnformattedText());
        boolean known = false;
        for (String candidate : SHOP_TITLES) if (plain(candidate).equals(title)) { known = true; break; }
        if (!known) return false;
        // The eight named category controls distinguish these shops from renamed chests.
        // Prices, bought items, highlights and window IDs can all change freely.
        for (int i = 0; i < SHOP_TITLES.length; i++) {
            ItemStack item = inventory.getStackInSlot(i);
            if (item == null || !item.hasDisplayName() || !plain(SHOP_TITLES[i]).equals(
                    plain(item.getTagCompound().getCompoundTag("display").getString("Name")))) return false;
        }
        return true;
    }

    private static String plain(String text) {
        return text == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(text).trim().toLowerCase(Locale.ROOT);
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
