package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.inventory.InventoryLayout;
import dev.vibe.inventory.InventoryLayout.Type;
import dev.vibe.module.impl.InventoryEditorModule;
import dev.vibe.module.impl.BlurModule;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;

/** A small HUD-editor-like layout surface for Inventory Manager. */
public final class InventoryEditorGui extends GuiScreen {

    private final InventoryEditorModule module;
    private final ParticlesRenderer particles = new ParticlesRenderer();
    private InventoryLayout layout;
    private Type draggingType;
    private int left;
    private int top;
    private static final int SLOT = 20;
    private static final int PANEL_WIDTH = 510;
    private static final int PANEL_HEIGHT = 344;
    private final List<Type> palette = Arrays.asList(Type.values());

    public InventoryEditorGui(InventoryEditorModule module) {
        this.module = module;
    }

    @Override
    public void initGui() {
        layout = module.getLayout();
        left = Math.max(8, (width - PANEL_WIDTH) / 2);
        top = Math.max(18, (height - PANEL_HEIGHT) / 2);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.INVENTORY_EDITOR, partialTicks);
        SkeetEditorStyle.window(left, top, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, "Inventory editor", "drag category • right click clears");
        String help = "Drag a category into a slot • right click clears • barrier keeps it free";
        fontRendererObj.drawStringWithShadow(help, left + 10, top + 27, SkeetEditorStyle.MUTED);

        int paletteLeft = left + 10;
        SkeetEditorStyle.panel(left + 7, top + 38, left + 163, top + PANEL_HEIGHT - 10, "Placeholders");
        int visiblePaletteIndex = 0;
        for (int i = 0; i < palette.size(); i++) {
            Type type = palette.get(i);
            if (!available(type)) continue;
            int col = visiblePaletteIndex % 5;
            int row = visiblePaletteIndex++ / 5;
            int x = paletteLeft + col * 30;
            int y = top + 60 + row * 31;
            drawChoice(x, y, type, mouseX, mouseY);
        }

        int gridLeft = left + 170;
        SkeetEditorStyle.panel(left + 166, top + 38, left + 354, top + 158, "Inventory");
        // Main storage first, then the hotbar below it to match Minecraft.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int logical = 9 + row * 9 + col;
                drawLayoutSlot(gridLeft + col * SLOT, top + 60 + row * SLOT, logical, mouseX, mouseY);
            }
        }
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("HOTBAR"), gridLeft, top + 123, SkeetEditorStyle.MUTED);
        for (int col = 0; col < 9; col++) {
            drawLayoutSlot(gridLeft + col * SLOT, top + 135, col, mouseX, mouseY);
        }

        int armourLeft = left + 364;
        SkeetEditorStyle.panel(left + 360, top + 38, left + PANEL_WIDTH - 12, top + PANEL_HEIGHT - 10, "Armor / autoarmor");
        for (int index = 0; index < 4; index++) {
            drawArmorToggle(armourLeft + index * 30, top + 60, index, mouseX, mouseY);
            String label = index == 0 ? "H" : index == 1 ? "C" : index == 2 ? "L" : "B";
            fontRendererObj.drawStringWithShadow(label, armourLeft + index * 30 + 7, top + 82, RenderUtils.MUTED);
        }
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("DROP BIN"), armourLeft, top + 101, 0xFFFF8799);
        int binSlots = layout.getDropBinSize();
        for (int index = 0; index < binSlots; index++) {
            drawLayoutSlot(armourLeft + (index % 5) * 27, top + 113 + (index / 5) * 27, 40 + index, mouseX, mouseY);
        }
        int binRows = Math.max(1, (binSlots + 4) / 5);
        int noteTop = top + 117 + binRows * 27;
        // Keep the instruction inside the narrow right panel at every GUI
        // scale instead of letting one long sentence run into the editor.
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Drop-bin items"), armourLeft, noteTop, RenderUtils.MUTED);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("are discarded."), armourLeft, noteTop + 11, RenderUtils.MUTED);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Ignore protects its slot."), armourLeft, noteTop + 22, RenderUtils.MUTED);
        if (draggingType != null) {
            itemRender.renderItemAndEffectIntoGUI(draggingType.createPlaceholder(), mouseX - 8, mouseY - 8);
            fontRendererObj.drawStringWithShadow(draggingType.getLabel(), mouseX + 8, mouseY + 7, RenderUtils.TEXT);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawChoice(int x, int y, Type type, int mouseX, int mouseY) {
        boolean hovered = within(mouseX, mouseY, x, y, x + 26, y + 29);
        SkeetEditorStyle.row(x, y, x + 26, y + 29, false, hovered);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(type.createPlaceholder(), x + 5, y + 3);
        RenderHelper.disableStandardItemLighting();
        String text = type.getLabel().length() > 3 ? type.getLabel().substring(0, 3) : type.getLabel();
        fontRendererObj.drawStringWithShadow(text, x + (26 - fontRendererObj.getStringWidth(text)) / 2, y + 19, RenderUtils.MUTED);
    }

    private boolean available(Type type) {
        return type != Type.HELMET && type != Type.CHESTPLATE && type != Type.LEGGINGS && type != Type.BOOTS
                && !layout.isAssigned(type);
    }

    /** Armor is opted into directly per armour slot, never dragged from the palette. */
    private void drawArmorToggle(int x, int y, int armorIndex, int mouseX, int mouseY) {
        int slot = 36 + armorIndex;
        Type desired = armorIndex == 0 ? Type.HELMET : armorIndex == 1 ? Type.CHESTPLATE
                : armorIndex == 2 ? Type.LEGGINGS : Type.BOOTS;
        boolean enabled = layout.get(slot) == desired;
        boolean hovered = within(mouseX, mouseY, x, y, x + 18, y + 18);
        SkeetEditorStyle.row(x, y, x + 18, y + 18, enabled, hovered);
        if (enabled) {
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(desired.createPlaceholder(), x + 1, y + 1);
            RenderHelper.disableStandardItemLighting();
        }
    }

    private void drawLayoutSlot(int x, int y, int slot, int mouseX, int mouseY) {
        Type type = layout.get(slot);
        boolean hovered = within(mouseX, mouseY, x, y, x + 18, y + 18);
        SkeetEditorStyle.row(x, y, x + 18, y + 18, type == Type.BARRIER, hovered);
        if (type != null) {
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(type.createPlaceholder(), x + 1, y + 1);
            RenderHelper.disableStandardItemLighting();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int visiblePaletteIndex = 0;
        for (int i = 0; i < palette.size(); i++) {
            if (!available(palette.get(i))) continue;
            int x = left + 10 + (visiblePaletteIndex % 5) * 30;
            int y = top + 60 + (visiblePaletteIndex / 5) * 31;
            visiblePaletteIndex++;
            if (within(mouseX, mouseY, x, y, x + 26, y + 29) && mouseButton == 0) {
                draggingType = palette.get(i);
                return;
            }
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0) {
            if (mouseButton == 1) {
                layout.set(slot, null);
                module.setLayout(layout);
            } else if (draggingType != null) {
                layout.set(slot, draggingType);
                module.setLayout(layout);
                draggingType = null;
            } else if (layout.get(slot) != null) {
                draggingType = layout.get(slot);
            }
            return;
        }
        int armourLeft = left + 364;
        for (int index = 0; index < 4; index++) {
            if (within(mouseX, mouseY, armourLeft + index * 30, top + 60, armourLeft + index * 30 + 18, top + 78) && mouseButton == 0) {
                int slotId = 36 + index;
                Type type = index == 0 ? Type.HELMET : index == 1 ? Type.CHESTPLATE : index == 2 ? Type.LEGGINGS : Type.BOOTS;
                layout.set(slotId, layout.get(slotId) == type ? null : type);
                module.setLayout(layout);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && draggingType != null) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                layout.set(slot, draggingType);
                module.setLayout(layout);
            }
            draggingType = null;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    private int slotAt(int mouseX, int mouseY) {
        int gridLeft = left + 170;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            if (within(mouseX, mouseY, gridLeft + col * SLOT, top + 60 + row * SLOT, gridLeft + col * SLOT + 18, top + 78 + row * SLOT)) return 9 + row * 9 + col;
        }
        for (int col = 0; col < 9; col++) {
            if (within(mouseX, mouseY, gridLeft + col * SLOT, top + 135, gridLeft + col * SLOT + 18, top + 153)) return col;
        }
        int armourLeft = left + 364;
        for (int index = 0; index < layout.getDropBinSize(); index++) if (within(mouseX, mouseY, armourLeft + (index % 5) * 27, top + 113 + (index / 5) * 27,
                armourLeft + (index % 5) * 27 + 18, top + 131 + (index / 5) * 27)) return 40 + index;
        return -1;
    }

    private boolean within(int mouseX, int mouseY, int x, int y, int right, int bottom) {
        return mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom;
    }

    private String ellipsize(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    @Override
    public void onGuiClosed() {
        if (module.isEnabled()) module.setEnabled(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
