package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.cosmetic.CosmeticPreset;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.CosmeticsEditorModule;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Skeet card browser for cosmetic presets. */
public final class CosmeticsEditorGui extends GuiScreen {
    private final CosmeticsEditorModule module;
    private final CosmeticPreviewRenderer preview = new CosmeticPreviewRenderer();
    private GuiTextField rename;
    private int left, top, right, bottom, scroll;
    private float previewYaw;
    private boolean rotating;
    private int lastMouseX;

    public CosmeticsEditorGui(CosmeticsEditorModule module) { this.module = module; }

    @Override public void initGui() {
        int w = Math.min(width - 18, 900), h = Math.min(height - 28, 490);
        left = (width - w) / 2; right = left + w; top = Math.max(14, (height - h) / 2); bottom = top + h;
        rename = new GuiTextField(0, fontRendererObj, left + 20, top + 50, 185, 16);
        rename.setEnableBackgroundDrawing(false); rename.setMaxStringLength(24);
        syncName();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.COSMETICS_EDITOR, partialTicks);
        SkeetEditorStyle.window(left, top, right, bottom, "Cosmetics editor", "presets • local and friend looks");
        CosmeticPreset selected = Vibe.getInstance().getCosmeticPresetManager().getSelected();
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Selected preset"), left + 16, top + 28, SkeetEditorStyle.MUTED);
        SkeetEditorStyle.input(left + 14, top + 45, left + 207, top + 66); rename.drawTextBox();
        if (selected != null) {
            int x = left + 220;
            SkeetEditorStyle.button(x, top + 45, x + 72, top + 66, "EDIT", true);
            SkeetEditorStyle.button(x + 80, top + 45, x + 152, top + 66, "SAVE", true);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.format("Skin: %s", selected.getSkinName()), x + 165, top + 51, SkeetEditorStyle.MUTED);
        }
        int gridTop = gridTop(), gridBottom = gridBottom(), cardW = cardWidth(), cardH = 270, gap = 12, columns = 5;
        int index = 0;
        beginScissor(left + 12, gridTop, right - left - 24, gridBottom - gridTop);
        try {
            for (CosmeticPreset preset : Vibe.getInstance().getCosmeticPresetManager().getPresets()) {
                int x = left + 12 + (index % columns) * (cardW + gap);
                int y = gridTop + (index / columns) * (cardH + gap) - scroll;
                if (y + cardH >= gridTop && y < gridBottom) drawCard(preset, x, y, cardW, cardH, preset == selected);
                index++;
            }
            int addX = left + 12 + (index % columns) * (cardW + gap), addY = gridTop + (index / columns) * (cardH + gap) - scroll;
            if (addY + cardH >= gridTop && addY < gridBottom) drawAddCard(addX, addY, cardW, cardH);
        } finally {
            endScissor();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCard(CosmeticPreset preset, int x, int y, int w, int h, boolean selected) {
        int accent = selected ? 0xFF38D996 : SkeetEditorStyle.accent(0.3F);
        SkeetEditorStyle.panel(x, y, x + w, y + h, null);
        SkeetEditorStyle.border(x, y, x + w, y + h, selected ? accent : SkeetEditorStyle.BORDER);
        if (selected) { net.minecraft.client.gui.Gui.drawRect(x + 1, y + 1, x + w - 1, y + 3, accent); }
        String name = fontRendererObj.trimStringToWidth(preset.getName(), w - 36);
        fontRendererObj.drawStringWithShadow(name, x + 10, y + 10, SkeetEditorStyle.TEXT);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("✎"), x + w - 20, y + 9, 0xFF2BAEF5);
        SkeetEditorStyle.panel(x + 10, y + 32, x + w - 10, y + 210, "Preview");
        preview.draw(preset, x + w / 2, y + 197, 47, previewYaw);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.format("Skin: %s", preset.getSkinName()), x + 10, y + 220, SkeetEditorStyle.MUTED);
        int count = preset.getAccessories().size();
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.format(count == 1 ? "%s Cosmetica accessory" : "%s Cosmetica accessories", count), x + 10, y + 236, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("⌫"), x + w - 18, y + h - 22, 0xFFFF6078);
    }
    private void drawAddCard(int x, int y, int w, int h) {
        SkeetEditorStyle.panel(x, y, x + w, y + h, null); SkeetEditorStyle.border(x, y, x + w, y + h, SkeetEditorStyle.BORDER);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("+"), x + w / 2 - 4, y + h / 2 - 10, 0xFF38D996);
        String text = dev.vibe.language.LanguageManager.translate("CREATE PRESET"); fontRendererObj.drawStringWithShadow(text, x + (w - fontRendererObj.getStringWidth(text)) / 2, y + h / 2 + 14, SkeetEditorStyle.MUTED);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        rename.mouseClicked(mouseX, mouseY, button);
        CosmeticPreset selected = Vibe.getInstance().getCosmeticPresetManager().getSelected();
        if (button == 0 && selected != null && hit(left + 220, top + 45, 72, 21, mouseX, mouseY)) { saveName(); mc.displayGuiScreen(new CosmeticPresetEditGui(module, selected, this)); return; }
        if (button == 0 && selected != null && hit(left + 300, top + 45, 72, 21, mouseX, mouseY)) { saveName(); return; }
        int gridTop = gridTop(), gridBottom = gridBottom(), cardW = cardWidth(), cardH = 270, gap = 12, columns = 5;
        if (mouseY < gridTop || mouseY >= gridBottom) { super.mouseClicked(mouseX, mouseY, button); return; }
        List<CosmeticPreset> presets = Vibe.getInstance().getCosmeticPresetManager().getPresets();
        for (int index = 0; index < presets.size(); index++) {
            CosmeticPreset preset = presets.get(index); int x = left + 12 + (index % columns) * (cardW + gap), y = gridTop + (index / columns) * (cardH + gap) - scroll;
            if (!hit(x, y, cardW, cardH, mouseX, mouseY)) continue;
            if (button == 0 && hit(x + cardW - 28, y + cardH - 30, 26, 26, mouseX, mouseY)) { Vibe.getInstance().getCosmeticPresetManager().delete(preset.getId()); syncName(); return; }
            Vibe.getInstance().getCosmeticPresetManager().select(preset.getId()); syncName();
            if (button == 0 && hit(x + cardW - 32, y + 3, 30, 28, mouseX, mouseY)) mc.displayGuiScreen(new CosmeticPresetEditGui(module, preset, this));
            else if (button == 0 && hit(x + 10, y + 32, cardW - 20, 178, mouseX, mouseY)) { rotating = true; lastMouseX = mouseX; }
            return;
        }
        int addIndex = presets.size(), addX = left + 12 + (addIndex % columns) * (cardW + gap), addY = gridTop + (addIndex / columns) * (cardH + gap) - scroll;
        if (button == 0 && hit(addX, addY, cardW, cardH, mouseX, mouseY)) { CosmeticPreset preset = Vibe.getInstance().getCosmeticPresetManager().create("New preset"); syncName(); mc.displayGuiScreen(new CosmeticPresetEditGui(module, preset, this)); return; }
        super.mouseClicked(mouseX, mouseY, button);
    }
    @Override protected void mouseClickMove(int mouseX, int mouseY, int button, long time) { if (rotating && button == 0) { previewYaw += mouseX - lastMouseX; lastMouseX = mouseX; } }
    @Override protected void mouseReleased(int mouseX, int mouseY, int state) { rotating = false; super.mouseReleased(mouseX, mouseY, state); }
    @Override public void handleMouseInput() throws IOException { super.handleMouseInput(); int wheel = Mouse.getEventDWheel(); if (wheel == 0) return; int mouseX = Mouse.getEventX() * width / mc.displayWidth, mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1; if (mouseX < left + 12 || mouseX >= right - 12 || mouseY < gridTop() || mouseY >= gridBottom()) return; scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel < 0 ? 28 : -28))); }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException { if (keyCode == Keyboard.KEY_ESCAPE) { saveName(); mc.displayGuiScreen(null); return; } if (rename.textboxKeyTyped(typedChar, keyCode)) saveName(); }
    @Override public void onGuiClosed() { if (module.isEnabled() && !(mc.currentScreen instanceof CosmeticPresetEditGui)) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    private void saveName() { CosmeticPreset preset = Vibe.getInstance().getCosmeticPresetManager().getSelected(); if (preset != null && !rename.getText().trim().isEmpty()) { preset.setName(rename.getText()); Vibe.getInstance().getCosmeticPresetManager().save(); } }
    private void syncName() { if (rename != null) { CosmeticPreset preset = Vibe.getInstance().getCosmeticPresetManager().getSelected(); rename.setText(preset == null ? "" : preset.getName()); } }
    private int gridTop() { return top + 85; }
    private int gridBottom() { return bottom - 12; }
    private int cardWidth() { return Math.max(1, (right - left - 24 - 12 * 4) / 5); }
    private int maxScroll() { int rows = (Vibe.getInstance().getCosmeticPresetManager().getPresets().size() + 1 + 4) / 5; return Math.max(0, rows * 270 + Math.max(0, rows - 1) * 12 - (gridBottom() - gridTop())); }
    private void beginScissor(int x, int y, int width, int height) { ScaledResolution resolution = new ScaledResolution(mc); int scale = resolution.getScaleFactor(); GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(x * scale, (resolution.getScaledHeight() - (y + height)) * scale, width * scale, height * scale); }
    private void endScissor() { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    private static boolean hit(int x, int y, int w, int h, int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
}
