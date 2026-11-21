package dev.vibe.event;

import dev.vibe.Vibe;
import dev.vibe.inventory.ChestSnapshot;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.Module;
import dev.vibe.module.impl.client.ShopSaverModule;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.world.ChestStealerModule;
import dev.vibe.ui.menu.AccountScreenStyle;
import dev.vibe.ui.menu.MenuGlass;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/** Glass corner controls for vanilla inventory and chest screens. */
public final class ChestControlsEvents {
    static final int AURA = 0x564910, STEALER = 0x564911, COPY = 0x564912, CLOSE_SHOP = 0x564913;
    private final Map<GuiScreen, List<GuiButton>> screens = new WeakHashMap<>();

    @SubscribeEvent public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        boolean chest = event.gui instanceof GuiChest;
        if (!chest && !(event.gui instanceof GuiInventory)) return;
        event.buttonList.removeIf(button -> control(button.id));
        if (chest) event.buttonList.add(new CornerButton(CLOSE_SHOP, "Close Shop"));
        event.buttonList.add(new CornerButton(AURA, "Disable Killaura"));
        if (chest) event.buttonList.add(new CornerButton(STEALER, "Disable Cheststealer"));
        event.buttonList.add(new CornerButton(COPY, chest ? "Copy Chest NBT" : "Copy Inventory NBT"));
        screens.put(event.gui, event.buttonList);
        sync(event.gui);
    }

    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Pre event) { sync(event.gui); }

    @SubscribeEvent public void render(GuiScreenEvent.DrawScreenEvent.Post event) {
        List<GuiButton> buttons = screens.get(event.gui);
        if (buttons == null || event.gui.width <= 16 || event.gui.height <= 0) return;
        MenuGlass.beginLavalamp(event.gui, event.gui.width, event.gui.height);
        try {
            for (GuiButton button : buttons) {
                if (control(button.id)) button.drawButton(Minecraft.getMinecraft(), event.mouseX, event.mouseY);
            }
        } finally {
            MenuGlass.endOverlay();
        }
    }

    private void sync(GuiScreen gui) {
        List<GuiButton> buttons = screens.get(gui);
        if (buttons == null) return;
        int width = Math.min(140, Math.max(0, gui.width - 16));
        int y = 8;
        for (GuiButton button : buttons) {
            if (!control(button.id)) continue;
            button.visible = button.enabled = width > 0 && available(gui, button.id);
            button.width = width;
            button.xPosition = gui.width - width - 8;
            button.yPosition = y;
            if (button.visible) y += 24;
        }
    }

    private static boolean available(GuiScreen gui, int id) {
        if (id == COPY) return true;
        if (id == CLOSE_SHOP) {
            ShopSaverModule shop = module(ShopSaverModule.class);
            return shop != null && shop.canCloseShop(gui);
        }
        Module toggle = toggleModule(id);
        return toggle != null && toggle.isEnabled();
    }

    @SubscribeEvent public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!screens.containsKey(event.gui) || event.button == null || !control(event.button.id)) return;
        event.setCanceled(true);
        activate(event.gui, event.button.id);
    }

    @SubscribeEvent public void mouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!screens.containsKey(event.gui) || Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0) return;
        int x = Mouse.getEventX() * event.gui.width / mc.displayWidth;
        int y = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;
        // GuiContainer also processes slot clicks after its button handler.
        if (press(event.gui, x, y)) event.setCanceled(true);
    }

    boolean press(GuiScreen gui, int x, int y) {
        sync(gui);
        List<GuiButton> buttons = screens.get(gui);
        if (buttons == null) return false;
        for (GuiButton button : buttons) {
            if (control(button.id) && button.mousePressed(Minecraft.getMinecraft(), x, y)) {
                activate(gui, button.id);
                return true;
            }
        }
        return false;
    }

    private static boolean control(int id) { return id >= AURA && id <= CLOSE_SHOP; }

    private void activate(GuiScreen gui, int id) {
        if (id == CLOSE_SHOP) {
            ShopSaverModule shop = module(ShopSaverModule.class);
            if (shop != null) shop.closeShop(gui);
        } else if (id == COPY) {
            copy(gui);
        } else {
            Module toggle = toggleModule(id);
            if (toggle == null || !toggle.isEnabled()) return;
            toggle.setEnabled(false);
            sync(gui);
            Vibe vibe = Vibe.getInstance();
            if (vibe != null && vibe.getConfig() != null) vibe.getConfig().save(vibe.getModuleManager());
        }
    }

    private static void copy(GuiScreen gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.currentScreen != gui) return;
        boolean chest = gui instanceof GuiChest;
        try {
            NBTTagCompound data;
            if (chest) {
                if (!(((GuiChest) gui).inventorySlots instanceof ContainerChest)) return;
                data = ChestSnapshot.capture((ContainerChest) ((GuiChest) gui).inventorySlots);
            } else {
                data = new NBTTagCompound();
                data.setTag("Items", mc.thePlayer.inventory.writeToNBT(new NBTTagList()));
                data.setInteger("SelectedItemSlot", mc.thePlayer.inventory.currentItem);
            }
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(data.toString()), null);
            dev.vibe.chat.ClientChat.send((chest ? "Chest" : "Inventory") + " NBT copied to the clipboard.");
        } catch (RuntimeException error) {
            dev.vibe.chat.ClientChat.send("Could not copy NBT. Please try again.");
        }
    }

    private static Module toggleModule(int id) {
        return id == AURA ? module(KillAuraModule.class) : module(ChestStealerModule.class);
    }

    private static <T extends Module> T module(Class<T> type) {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(type);
    }

    private static final class CornerButton extends AccountScreenStyle.Button {
        CornerButton(int id, String text) { super(id, 0, 0, 140, 20, LanguageManager.translate(text), "", false, false); }

        @Override public void drawButton(Minecraft mc, int x, int y) {
            if (MenuGlass.active()) super.drawButton(mc, x, y);
        }
    }
}
